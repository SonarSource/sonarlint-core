/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.ai.ide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandException;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationAgentCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliCommandAction;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareCliCommandParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareCliCommandResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.SonarQubeCliState;

/**
 * Shared policy and command preparation for integrating IDE-hosted AI agents with SonarQube.
 * Clients remain responsible for detecting agents in their own IDE and running commands in a
 * native terminal. This service never transports credentials to the CLI.
 */
public class AiIntegrationService {

  private static final long COMMAND_TIMEOUT_MILLIS = 30_000L;
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
  private static final Pattern VERSION_PATTERN = Pattern.compile("\\b\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?\\b");
  private static final String UNIX_INSTALL_SCRIPT_URL =
    "https://raw.githubusercontent.com/SonarSource/sonarqube-cli/refs/heads/master/user-scripts/install.sh";
  private static final String WINDOWS_INSTALL_SCRIPT_URL =
    "https://raw.githubusercontent.com/SonarSource/sonarqube-cli/refs/heads/master/user-scripts/install.ps1";

  private final System2 system2;
  private final CommandExecutor commandExecutor;
  private final Path userHome;
  private final Map<String, String> environment;

  @Inject
  public AiIntegrationService() {
    this(System2.INSTANCE, CommandExecutor.create(), Paths.get(System.getProperty("user.home")), System.getenv());
  }

  AiIntegrationService(System2 system2, CommandExecutor commandExecutor, Path userHome, Map<String, String> environment) {
    this.system2 = system2;
    this.commandExecutor = commandExecutor;
    this.userHome = userHome;
    this.environment = environment;
  }

  public GetAiIntegrationStateResponse getIntegrationState(GetAiIntegrationStateParams params) {
    var cli = findCli();
    var cliState = toCliState(cli);
    var agentCapabilities = params.getDetectedAgents().stream()
      .distinct()
      .map(AiIntegrationService::capabilityFor)
      .toList();
    return new GetAiIntegrationStateResponse(cliState, agentCapabilities);
  }

  public PrepareCliCommandResponse prepareCliCommand(PrepareCliCommandParams params) {
    if (params.getAction() == CliCommandAction.INSTALL) {
      return prepareInstallCommand();
    }

    var cli = findCli();
    if (cli.installationStatus != CliInstallationStatus.INSTALLED || cli.path == null) {
      throw new IllegalStateException("A working SonarQube CLI installation is required");
    }
    return switch (params.getAction()) {
      case AUTHENTICATE -> prepareAuthenticationCommand(cli.path, params);
      case INTEGRATE -> prepareIntegrationCommand(cli.path, params.getAgent());
      case INSTALL -> throw new IllegalStateException("Installation command should already have been prepared");
    };
  }

  private SonarQubeCliState toCliState(CliLookup cli) {
    if (cli.installationStatus != CliInstallationStatus.INSTALLED || cli.path == null) {
      return new SonarQubeCliState(cli.installationStatus, CliAuthenticationStatus.UNKNOWN,
        cli.path == null ? null : cli.path.toString(), cli.version, null, null);
    }

    var status = readCliStatus(cli.path);
    return new SonarQubeCliState(CliInstallationStatus.INSTALLED, status.authenticationStatus,
      cli.path.toString(), status.version.orElse(cli.version), status.serverUrl, status.organization);
  }

  private CliStatus readCliStatus(Path executable) {
    var result = execute(executable, List.of("system", "status", "--json"));
    if (result.output.isEmpty()) {
      return CliStatus.unknown();
    }

    try {
      var json = JSON_MAPPER.readTree(String.join("\n", result.output));
      var auth = json.get("auth");
      var version = stringValue(json, "version");
      if (auth == null || "unauthenticated".equals(stringValue(auth, "status").orElse(null))) {
        return new CliStatus(CliAuthenticationStatus.UNAUTHENTICATED, version, null, null);
      }

      var tokenStatus = stringValue(auth, "token").orElse(null);
      var authenticationStatus = switch (tokenStatus == null ? "" : tokenStatus) {
        case "active" -> CliAuthenticationStatus.AUTHENTICATED;
        case "invalid" -> CliAuthenticationStatus.INVALID;
        case "set_unverified" -> CliAuthenticationStatus.UNVERIFIED;
        case "not_set" -> CliAuthenticationStatus.UNAUTHENTICATED;
        default -> CliAuthenticationStatus.UNKNOWN;
      };
      return new CliStatus(authenticationStatus, version,
        stringValue(auth, "server").orElse(null), stringValue(auth, "org").orElse(null));
    } catch (IOException | RuntimeException e) {
      return CliStatus.unknown();
    }
  }

  private CliLookup findCli() {
    Path firstUnusable = null;
    for (var candidate : cliCandidates()) {
      if (!isExecutable(candidate)) {
        continue;
      }
      var version = readCliVersion(candidate);
      if (version.isPresent()) {
        return new CliLookup(CliInstallationStatus.INSTALLED, candidate, version.orElse(null));
      }
      if (firstUnusable == null) {
        firstUnusable = candidate;
      }
    }
    return firstUnusable == null
      ? new CliLookup(CliInstallationStatus.NOT_INSTALLED, null, null)
      : new CliLookup(CliInstallationStatus.UNUSABLE, firstUnusable, null);
  }

  private Set<Path> cliCandidates() {
    var candidates = new LinkedHashSet<Path>();
    var executableNames = system2.isOsWindows() ? List.of("sonar.exe", "sonar.cmd", "sonar") : List.of("sonar");
    var path = environment.get("PATH");
    if (path != null) {
      var separator = system2.isOsWindows() ? ";" : ":";
      for (var directory : path.split(Pattern.quote(separator))) {
        if (!directory.isBlank()) {
          try {
            executableNames.forEach(name -> candidates.add(Paths.get(directory, name)));
          } catch (InvalidPathException e) {
            // Ignore malformed PATH entries and continue looking for a working installation.
          }
        }
      }
    }

    if (system2.isOsWindows()) {
      var localAppData = environment.get("LOCALAPPDATA");
      if (localAppData != null && !localAppData.isBlank()) {
        executableNames.forEach(name -> candidates.add(Paths.get(localAppData, "sonarqube-cli", "bin", name)));
      }
    } else {
      candidates.add(userHome.resolve(".local/share/sonarqube-cli/bin/sonar"));
    }
    return candidates;
  }

  private boolean isExecutable(Path path) {
    return Files.isRegularFile(path) && (system2.isOsWindows() || Files.isExecutable(path));
  }

  private Optional<String> readCliVersion(Path executable) {
    var result = execute(executable, List.of("--version"));
    if (result.exitCode != 0) {
      return Optional.empty();
    }
    return result.output.stream()
      .map(VERSION_PATTERN::matcher)
      .filter(Matcher::find)
      .map(Matcher::group)
      .findFirst();
  }

  private CommandResult execute(Path executable, List<String> arguments) {
    var stdout = new ArrayList<String>();
    var stderr = new ArrayList<String>();
    var command = Command.create(executable.toString());
    environment.forEach(command::setEnvironmentVariable);
    arguments.forEach(command::addArgument);
    try {
      var exitCode = commandExecutor.execute(command, stdout::add, stderr::add, COMMAND_TIMEOUT_MILLIS);
      return new CommandResult(exitCode, stdout);
    } catch (CommandException e) {
      return new CommandResult(-1, List.of());
    }
  }

  private PrepareCliCommandResponse prepareInstallCommand() {
    if (system2.isOsWindows()) {
      return new PrepareCliCommandResponse("powershell.exe",
        List.of("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", "irm " + WINDOWS_INSTALL_SCRIPT_URL + " | iex"), true);
    }
    return new PrepareCliCommandResponse("/bin/bash", List.of("-o", "pipefail", "-c",
      "curl --fail --silent --show-error --location " + UNIX_INSTALL_SCRIPT_URL + " | bash"), true);
  }

  private static PrepareCliCommandResponse prepareAuthenticationCommand(Path executable, PrepareCliCommandParams params) {
    var arguments = new ArrayList<>(List.of("auth", "login"));
    addOption(arguments, "--server", params.getServerUrl());
    addOption(arguments, "--org", params.getOrganization());
    return new PrepareCliCommandResponse(executable.toString(), arguments, true);
  }

  private static PrepareCliCommandResponse prepareIntegrationCommand(Path executable, @Nullable AiAgent agent) {
    if (agent == null) {
      throw new IllegalArgumentException("An AI agent is required");
    }
    var cliTarget = cliTarget(agent)
      .orElseThrow(() -> new IllegalArgumentException(agent + " is not supported by the SonarQube CLI"));
    return new PrepareCliCommandResponse(executable.toString(), List.of("integrate", cliTarget, "--global"), true);
  }

  private static void addOption(List<String> arguments, String option, @Nullable String value) {
    if (value != null && !value.isBlank()) {
      arguments.add(option);
      arguments.add(value.trim());
    }
  }

  private static AiIntegrationAgentCapability capabilityFor(AiAgent agent) {
    return new AiIntegrationAgentCapability(agent, cliTarget(agent).isPresent(), true);
  }

  private static Optional<String> cliTarget(AiAgent agent) {
    return switch (agent) {
      case CURSOR -> Optional.of("cursor");
      case CLAUDE_CODE -> Optional.of("claude");
      case CODEX -> Optional.of("codex");
      case WINDSURF, KIRO, GITHUB_COPILOT -> Optional.empty();
    };
  }

  private static Optional<String> stringValue(JsonNode object, String property) {
    var value = object.get(property);
    return value == null || value.isNull() || !value.isValueNode() ? Optional.empty() : Optional.of(value.asText());
  }

  private record CliLookup(CliInstallationStatus installationStatus, @Nullable Path path, @Nullable String version) {
  }

  private record CliStatus(CliAuthenticationStatus authenticationStatus, Optional<String> version,
                           @Nullable String serverUrl, @Nullable String organization) {
    private static CliStatus unknown() {
      return new CliStatus(CliAuthenticationStatus.UNKNOWN, Optional.empty(), null, null);
    }
  }

  private record CommandResult(int exitCode, List<String> output) {
  }

}
