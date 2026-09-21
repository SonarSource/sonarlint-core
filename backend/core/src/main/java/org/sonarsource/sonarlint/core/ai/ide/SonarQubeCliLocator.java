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
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.os.OsSearchPath;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;

final class SonarQubeCliLocator {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final long COMMAND_TIMEOUT_MILLIS = 30_000L;
  private static final Pattern VERSION_PATTERN = Pattern.compile("\\b\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?\\b");

  private final System2 system2;
  private final CommandExecutor commandExecutor;
  private final Path userHome;
  private final Map<String, String> environment;
  private final Path pathHelperLocation;

  SonarQubeCliLocator(System2 system2, CommandExecutor commandExecutor, Path userHome, Map<String, String> environment,
    Path pathHelperLocation) {
    this.system2 = system2;
    this.commandExecutor = commandExecutor;
    this.userHome = userHome;
    this.environment = environment;
    this.pathHelperLocation = pathHelperLocation;
  }

  boolean isWindows() {
    return system2.isOsWindows();
  }

  CliLookup find() {
    Path firstUnusable = null;
    for (var candidate : cliCandidates()) {
      if (!isExecutable(candidate)) {
        continue;
      }
      var version = readCliVersion(candidate);
      if (version.isPresent()) {
        return new CliLookup(CliInstallationStatus.INSTALLED, candidate, version.get());
      }
      if (firstUnusable == null) {
        firstUnusable = candidate;
      }
    }
    return firstUnusable == null
      ? new CliLookup(CliInstallationStatus.NOT_INSTALLED, null, null)
      : new CliLookup(CliInstallationStatus.UNUSABLE, firstUnusable, null);
  }

  SonarQubeCliStatusDecoder.CliStatus readStatus(Path executable) {
    var result = execute(executable, List.of("system", "status", "--json"));
    if (result.output.isEmpty()) {
      return SonarQubeCliStatusDecoder.CliStatus.unknown();
    }

    try {
      return SonarQubeCliStatusDecoder.decode(String.join("\n", result.output));
    } catch (IOException | RuntimeException e) {
      LOG.debug("Unable to parse the SonarQube CLI status", e);
      return SonarQubeCliStatusDecoder.CliStatus.unknown();
    }
  }

  private Set<Path> cliCandidates() {
    var candidates = new LinkedHashSet<Path>();
    addPathCandidates(candidates);
    addStandardInstallCandidates(candidates);
    return candidates;
  }

  private void addPathCandidates(Set<Path> candidates) {
    var path = OsSearchPath.resolve(system2, environment, pathHelperLocation, commandExecutor, COMMAND_TIMEOUT_MILLIS);
    if (path == null) {
      return;
    }
    var separator = system2.isOsWindows() ? ";" : ":";
    for (var directory : OsSearchPath.splitEntries(path, separator)) {
      addExecutablesFromDirectory(candidates, directory);
    }
  }

  private void addExecutablesFromDirectory(Set<Path> candidates, String directory) {
    if (directory.isBlank()) {
      return;
    }
    try {
      executableNames().forEach(name -> candidates.add(Paths.get(directory, name)));
    } catch (InvalidPathException e) {
      LOG.debug("Ignoring an invalid PATH entry while locating the SonarQube CLI", e);
    }
  }

  private void addStandardInstallCandidates(Set<Path> candidates) {
    if (system2.isOsWindows()) {
      var localAppData = OsSearchPath.environmentVariableIgnoreCase(environment, "LOCALAPPDATA");
      if (localAppData != null && !localAppData.isBlank()) {
        executableNames().forEach(name -> candidates.add(Paths.get(localAppData, "sonarqube-cli", "bin", name)));
      }
      return;
    }
    candidates.add(userHome.resolve(".local/share/sonarqube-cli/bin/sonar"));
  }

  private List<String> executableNames() {
    return system2.isOsWindows() ? List.of("sonar.exe", "sonar.cmd", "sonar") : List.of("sonar");
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

  record CliLookup(CliInstallationStatus installationStatus, @Nullable Path path, @Nullable String version) {
  }

  private record CommandResult(int exitCode, List<String> output) {
  }
}
