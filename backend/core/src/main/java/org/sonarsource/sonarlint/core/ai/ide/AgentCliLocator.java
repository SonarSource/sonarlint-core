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

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandException;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.nodejs.OsSearchPath;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;

final class AgentCliLocator {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final long PATH_RESOLVE_TIMEOUT_MILLIS = 30_000L;
  private static final long AGENT_PROBE_TIMEOUT_MILLIS = 5_000L;
  private static final int AGENT_PROBE_MAX_LINES = 20;
  private static final int AGENT_PROBE_MAX_LINE_LENGTH = 1_024;
  private static final List<String> VERSION_PROBE = List.of("--version");
  private static final List<String> HELP_PROBE = List.of("--help");
  private static final List<AgentCliDescriptor> AGENT_CLIS = List.of(
    new AgentCliDescriptor(AiAgent.CLAUDE_CODE, List.of("claude"), VERSION_PROBE, List.of("claude code")),
    new AgentCliDescriptor(AiAgent.CODEX, List.of("codex"), VERSION_PROBE, List.of("codex-cli")),
    new AgentCliDescriptor(AiAgent.GITHUB_COPILOT_CLI, List.of("copilot"), VERSION_PROBE, List.of("github copilot cli")),
    new AgentCliDescriptor(AiAgent.CURSOR, List.of("cursor-agent", "agent"), HELP_PROBE, List.of("cursor agent", "cursor-agent")),
    new AgentCliDescriptor(AiAgent.ANTIGRAVITY, List.of("agy"), HELP_PROBE, List.of("usage of agy:")));

  private final System2 system2;
  private final CommandExecutor commandExecutor;
  private final Path userHome;
  private final Map<String, String> environment;
  private final Path pathHelperLocation;

  AgentCliLocator(System2 system2, CommandExecutor commandExecutor, Path userHome, Map<String, String> environment,
    Path pathHelperLocation) {
    this.system2 = system2;
    this.commandExecutor = commandExecutor;
    this.userHome = userHome;
    this.environment = environment;
    this.pathHelperLocation = pathHelperLocation;
  }

  List<AiAgent> discover() {
    var discovered = new ArrayList<AiAgent>();
    var effectivePath = OsSearchPath.resolve(system2, environment, pathHelperLocation, commandExecutor, PATH_RESOLVE_TIMEOUT_MILLIS);
    var directories = executableSearchDirectories(effectivePath);
    for (var descriptor : AGENT_CLIS) {
      if (isAgentCliInstalled(descriptor, directories, effectivePath)) {
        discovered.add(descriptor.agent());
      }
    }
    return discovered;
  }

  private boolean isAgentCliInstalled(AgentCliDescriptor descriptor, Set<Path> directories, @Nullable String effectivePath) {
    for (var baseName : descriptor.executableNames()) {
      var candidate = firstExistingExecutable(baseName, directories);
      if (candidate != null && probeMatches(candidate, descriptor, effectivePath)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private Path firstExistingExecutable(String baseName, Set<Path> directories) {
    for (var directory : directories) {
      for (var executableName : executableNames(baseName)) {
        var candidate = directory.resolve(executableName);
        if (isExecutable(candidate)) {
          return candidate;
        }
      }
    }
    return null;
  }

  private boolean probeMatches(Path executable, AgentCliDescriptor descriptor, @Nullable String effectivePath) {
    var result = executeAgentProbe(executable, descriptor.probeArguments(), effectivePath);
    if (result.exitCode != 0) {
      return false;
    }
    var output = (String.join("\n", result.stdout) + "\n" + String.join("\n", result.stderr))
      .toLowerCase(Locale.ROOT);
    return descriptor.outputMarkers().stream().anyMatch(output::contains);
  }

  private Set<Path> executableSearchDirectories(@Nullable String path) {
    var directories = new LinkedHashSet<Path>();
    if (path != null) {
      var separator = system2.isOsWindows() ? ";" : ":";
      for (var directory : OsSearchPath.splitEntries(path, separator)) {
        addDirectory(directories, directory);
      }
    }
    directories.add(userHome.resolve(".local/bin"));
    return directories;
  }

  private static void addDirectory(Set<Path> directories, String directory) {
    if (directory.isBlank()) {
      return;
    }
    try {
      var candidate = Paths.get(directory);
      if (candidate.isAbsolute()) {
        directories.add(candidate);
      }
    } catch (InvalidPathException e) {
      LOG.debug("Ignoring an invalid PATH entry while locating an AI agent CLI", e);
    }
  }

  private List<String> executableNames(String baseName) {
    return system2.isOsWindows() ? List.of(baseName + ".exe", baseName + ".cmd", baseName) : List.of(baseName);
  }

  private boolean isExecutable(Path path) {
    return Files.isRegularFile(path) && (system2.isOsWindows() || Files.isExecutable(path));
  }

  private CommandResult executeAgentProbe(Path executable, List<String> arguments, @Nullable String effectivePath) {
    var stdout = new ArrayList<String>();
    var stderr = new ArrayList<String>();
    var command = Command.create(executable.toString());
    environment.forEach(command::setEnvironmentVariable);
    if (system2.isOsMac() && effectivePath != null) {
      command.setEnvironmentVariable("PATH", effectivePath);
    }
    arguments.forEach(command::addArgument);
    try {
      var exitCode = commandExecutor.execute(command, line -> addProbeLine(stdout, line), line -> addProbeLine(stderr, line),
        AGENT_PROBE_TIMEOUT_MILLIS);
      return new CommandResult(exitCode, stdout, stderr);
    } catch (CommandException e) {
      LOG.debug("Unable to probe AI agent CLI at {}", executable, e);
      return new CommandResult(-1, List.of(), List.of());
    }
  }

  private static void addProbeLine(List<String> output, String line) {
    if (output.size() < AGENT_PROBE_MAX_LINES) {
      output.add(line.substring(0, Math.min(line.length(), AGENT_PROBE_MAX_LINE_LENGTH)));
    }
  }

  private record CommandResult(int exitCode, List<String> stdout, List<String> stderr) {
  }

  private record AgentCliDescriptor(AiAgent agent, List<String> executableNames, List<String> probeArguments,
    List<String> outputMarkers) {
  }
}
