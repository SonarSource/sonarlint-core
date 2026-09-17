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

import com.google.common.annotations.VisibleForTesting;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationAgentCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationConnection;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationScope;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareAuthenticateCliCommandParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareCliCommandResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareIntegrateCliCommandParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.SonarQubeCliState;

/**
 * Shared policy and command preparation for integrating IDE-hosted AI agents with SonarQube.
 * Clients remain responsible for detecting agents in their own IDE and running commands in a
 * native terminal. This service never transports credentials to the CLI.
 */
public class AiIntegrationService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final long COMMAND_TIMEOUT_MILLIS = 30_000L;
  private static final Path MAC_OS_PATH_HELPER = Paths.get("/usr/libexec/path_helper");
  private static final Pattern VERSION_PATTERN = Pattern.compile("\\b\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?\\b");

  private final System2 system2;
  private final CommandExecutor commandExecutor;
  private final Path userHome;
  private final Map<String, String> environment;
  private final ConnectionConfigurationRepository connectionRepository;
  private final ConfigurationRepository configurationRepository;

  @Inject
  public AiIntegrationService(ConnectionConfigurationRepository connectionRepository, ConfigurationRepository configurationRepository) {
    this(System2.INSTANCE, CommandExecutor.create(), Paths.get(System.getProperty("user.home")), System.getenv(),
      connectionRepository, configurationRepository);
  }

  @VisibleForTesting
  public AiIntegrationService(System2 system2, CommandExecutor commandExecutor, Path userHome, Map<String, String> environment,
    ConnectionConfigurationRepository connectionRepository, ConfigurationRepository configurationRepository) {
    this.system2 = system2;
    this.commandExecutor = commandExecutor;
    this.userHome = userHome;
    this.environment = environment;
    this.connectionRepository = connectionRepository;
    this.configurationRepository = configurationRepository;
  }

  public GetAiIntegrationStateResponse getIntegrationState(GetAiIntegrationStateParams params) {
    var cli = findCli();
    var cliState = toCliState(cli);
    var agentCapabilities = params.getDetectedAgents().stream()
      .distinct()
      .map(agent -> capabilityFor(params.getIdeHost(), agent, params.getScope()))
      .toList();
    var authenticationStatus = cliState.getAuthenticationStatus();
    var loginRequired = authenticationStatus == CliAuthenticationStatus.UNAUTHENTICATED
      || authenticationStatus == CliAuthenticationStatus.INVALID
      || authenticationStatus == CliAuthenticationStatus.UNVERIFIED;
    var connectionChoices = loginRequired
      ? availableConnections()
      : List.<AiIntegrationConnection>of();
    return new GetAiIntegrationStateResponse(cliState, agentCapabilities, connectionChoices,
      recommendedConnectionId(params, connectionChoices));
  }

  public PrepareCliCommandResponse prepareInstallCommand() {
    return CliCommandFactory.prepareInstallCommand(system2.isOsWindows());
  }

  public PrepareCliCommandResponse prepareAuthenticateCommand(PrepareAuthenticateCliCommandParams params) {
    var connection = selectedConnection(params.getConnectionId());
    var serverUrl = connection == null ? params.getServerUrl() : connection.getServerUrl();
    var organization = connection == null ? params.getOrganization() : connection.getOrganization();
    return CliCommandFactory.prepareAuthenticationCommand(requireInstalledCli(), serverUrl, organization);
  }

  public PrepareCliCommandResponse prepareIntegrateCommand(PrepareIntegrateCliCommandParams params) {
    return CliCommandFactory.prepareIntegrationCommand(requireInstalledCli(), params.getAgent());
  }

  private Path requireInstalledCli() {
    var cli = findCli();
    if (cli.installationStatus != CliInstallationStatus.INSTALLED || cli.path == null) {
      throw new IllegalStateException("A working SonarQube CLI installation is required");
    }
    return cli.path;
  }

  private SonarQubeCliState toCliState(CliLookup cli) {
    if (cli.installationStatus != CliInstallationStatus.INSTALLED || cli.path == null) {
      return new SonarQubeCliState(cli.installationStatus, CliAuthenticationStatus.UNKNOWN,
        cli.path == null ? null : cli.path.toString(), cli.version, null, null);
    }

    var status = readCliStatus(cli.path);
    return new SonarQubeCliState(CliInstallationStatus.INSTALLED, status.authenticationStatus(),
      cli.path.toString(), status.version().orElse(cli.version), status.serverUrl(), status.organization());
  }

  private SonarQubeCliStatusDecoder.CliStatus readCliStatus(Path executable) {
    var result = execute(executable, List.of("system", "status", "--json"));
    if (result.exitCode != 0 || result.output.isEmpty()) {
      return SonarQubeCliStatusDecoder.CliStatus.unavailable();
    }

    try {
      return SonarQubeCliStatusDecoder.decode(String.join("\n", result.output));
    } catch (IOException | RuntimeException e) {
      LOG.debug("Unable to parse the SonarQube CLI status", e);
      return SonarQubeCliStatusDecoder.CliStatus.unavailable();
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
    var path = searchPath();
    if (path != null) {
      for (var directory : OsPathHelpers.splitPathEntries(path, system2.isOsWindows())) {
        if (!directory.isBlank()) {
          try {
            executableNames.forEach(name -> candidates.add(Paths.get(directory, name)));
          } catch (InvalidPathException e) {
            LOG.debug("Ignoring an invalid PATH entry while locating the SonarQube CLI", e);
            // Ignore malformed PATH entries and continue looking for a working installation.
          }
        }
      }
    }

    if (system2.isOsWindows()) {
      var localAppData = environmentVariable("LOCALAPPDATA");
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
    var version = result.output.stream()
      .map(VERSION_PATTERN::matcher)
      .filter(Matcher::find)
      .map(Matcher::group)
      .findFirst();
    if (version.isEmpty()) {
      LOG.debug("SonarQube CLI at {} did not return a recognizable version", executable);
    }
    return version;
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
      LOG.debug("Unable to execute command at {}", executable, e);
      return new CommandResult(-1, List.of());
    }
  }

  @Nullable
  private String searchPath() {
    if (!system2.isOsMac()) {
      return environmentVariable("PATH");
    }
    var pathHelperResult = execute(MAC_OS_PATH_HELPER, List.of("-s"));
    if (pathHelperResult.exitCode == 0) {
      var path = pathHelperResult.output.stream()
        .map(OsPathHelpers::pathFromPathHelperOutput)
        .flatMap(Optional::stream)
        .findFirst();
      if (path.isPresent()) {
        return path.get();
      }
      LOG.debug("Unable to read PATH from macOS path_helper output");
    }
    return environmentVariable("PATH");
  }

  @Nullable
  private String environmentVariable(String name) {
    return OsPathHelpers.environmentVariable(environment, name, system2.isOsWindows());
  }

  @Nullable
  private AiIntegrationConnection selectedConnection(@Nullable String connectionId) {
    if (connectionId == null || connectionId.isBlank()) {
      return null;
    }
    var connection = connectionRepository.getConnectionById(connectionId);
    if (connection == null) {
      throw new IllegalArgumentException("Unknown SonarQube connection: " + connectionId);
    }
    return asConnection(connection);
  }

  private List<AiIntegrationConnection> availableConnections() {
    return connectionRepository.getConnectionsById().values().stream()
      .map(AiIntegrationService::asConnection)
      .sorted(Comparator.comparing(AiIntegrationConnection::getConnectionId))
      .toList();
  }

  @Nullable
  private String recommendedConnectionId(GetAiIntegrationStateParams params, List<AiIntegrationConnection> connectionChoices) {
    if (connectionChoices.isEmpty()) {
      return null;
    }
    var scopeId = params.getConfigurationScopeId();
    if (scopeId != null) {
      var connectionId = configurationRepository.getEffectiveBinding(scopeId).map(Binding::connectionId).orElse(null);
      if (connectionId != null && connectionChoices.stream().anyMatch(connection -> connection.getConnectionId().equals(connectionId))) {
        return connectionId;
      }
    }
    return connectionChoices.size() == 1 ? connectionChoices.get(0).getConnectionId() : null;
  }

  private static AiIntegrationConnection asConnection(AbstractConnectionConfiguration connection) {
    var organization = connection instanceof SonarCloudConnectionConfiguration sonarCloudConnection
      ? sonarCloudConnection.getOrganization()
      : null;
    return new AiIntegrationConnection(connection.getConnectionId(), connection.getUrl(), organization);
  }

  private static AiIntegrationAgentCapability capabilityFor(AiIntegrationHost host, AiAgent agent, AiIntegrationScope scope) {
    var cliIntegrationSupported = scope == AiIntegrationScope.GLOBAL && cliTarget(agent, host).isPresent();
    var standaloneMcpSupported = supportsStandaloneMcp(host, agent);
    var hookSupported = scope == AiIntegrationScope.GLOBAL && host == AiIntegrationHost.WINDSURF && agent == AiAgent.WINDSURF;
    return new AiIntegrationAgentCapability(agent, cliIntegrationSupported, standaloneMcpSupported, hookSupported, cliIntegrationSupported);
  }

  private static boolean supportsStandaloneMcp(AiIntegrationHost host, AiAgent agent) {
    if (AiAgentSupport.jsonSectionName(agent).isEmpty()) {
      return false;
    }
    return switch (agent) {
      case CURSOR -> host == AiIntegrationHost.CURSOR || host == AiIntegrationHost.OTHER;
      case GITHUB_COPILOT -> host == AiIntegrationHost.VSCODE || host == AiIntegrationHost.INTELLIJ
        || host == AiIntegrationHost.VISUAL_STUDIO || host == AiIntegrationHost.OTHER;
      case KIRO -> host == AiIntegrationHost.KIRO || host == AiIntegrationHost.OTHER;
      case WINDSURF -> host == AiIntegrationHost.WINDSURF || host == AiIntegrationHost.OTHER;
      case CLAUDE_CODE -> true;
      case CODEX -> false;
    };
  }

  private static Optional<String> cliTarget(AiAgent agent, AiIntegrationHost host) {
    if (agent == AiAgent.CURSOR && host != AiIntegrationHost.CURSOR && host != AiIntegrationHost.OTHER) {
      return Optional.empty();
    }
    return AiAgentSupport.cliTarget(agent);
  }

  private record CliLookup(CliInstallationStatus installationStatus, @Nullable Path path, @Nullable String version) {
  }

  private record CommandResult(int exitCode, List<String> output) {
  }

}
