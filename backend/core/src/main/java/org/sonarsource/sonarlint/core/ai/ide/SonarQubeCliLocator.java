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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.ai.ide.SonarQubeCliStatusDecoder.CliStatus;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;

final class SonarQubeCliLocator {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String CLI_EXECUTABLE_NAME = "sonar";
  private static final long COMMAND_TIMEOUT_MILLIS = 30_000L;
  private static final Pattern VERSION_PATTERN = Pattern.compile("\\b\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?\\b");

  private final OsExecutableSearch search;
  private final Path userHome;

  SonarQubeCliLocator(OsExecutableSearch search, Path userHome) {
    this.search = search;
    this.userHome = userHome;
  }

  boolean isWindows() {
    return search.isWindows();
  }

  @Nullable
  Path installationDirectory(CliLookup cli) {
    if (cli.installationStatus() != CliInstallationStatus.INSTALLED || cli.path() == null) {
      return null;
    }
    Path directory;
    if (isWindows()) {
      var localAppData = search.environmentVariableIgnoreCase("LOCALAPPDATA");
      if (localAppData == null || localAppData.isBlank()) {
        return null;
      }
      directory = Path.of(localAppData, "sonarqube-cli");
    } else {
      directory = userHome.resolve(".local/share/sonarqube-cli");
    }
    directory = directory.toAbsolutePath().normalize();
    var expected = directory.resolve("bin").resolve(isWindows() ? (CLI_EXECUTABLE_NAME + ".exe") : CLI_EXECUTABLE_NAME).toString();
    var detected = cli.path().toAbsolutePath().normalize().toString();
    var matches = isWindows() ? expected.equalsIgnoreCase(detected) : expected.equals(detected);
    return matches ? directory : null;
  }

  OsExecutableSearch.CommandResult reset(Path executable, List<String> stdout, List<String> stderr) {
    return search.execute(executable, List.of("system", "reset", "--force"), null, 120_000L, stdout::add, stderr::add);
  }

  CliLookup find() {
    var resolvedPath = search.resolvePath();
    Path firstUnusable = null;
    for (var candidate : cliCandidates(resolvedPath)) {
      if (!search.isExecutable(candidate)) {
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

  CliStatus readStatus(Path executable) {
    var stdout = new ArrayList<String>();
    var result = search.execute(executable, List.of("system", "status", "--json"), null, COMMAND_TIMEOUT_MILLIS,
      stdout::add);
    if (result.exitCode() != 0 || stdout.isEmpty()) {
      return CliStatus.unavailable();
    }

    try {
      return SonarQubeCliStatusDecoder.decode(String.join("\n", stdout));
    } catch (IOException | RuntimeException e) {
      LOG.debug("Unable to parse the SonarQube CLI status", e);
      return CliStatus.unavailable();
    }
  }

  private Set<Path> cliCandidates(@Nullable String resolvedPath) {
    var candidates = new LinkedHashSet<Path>();
    for (var directory : search.pathDirectories(resolvedPath, false)) {
      search.executableNames(CLI_EXECUTABLE_NAME).forEach(name -> candidates.add(directory.resolve(name)));
    }
    addStandardInstallCandidates(candidates);
    return candidates;
  }

  private void addStandardInstallCandidates(Set<Path> candidates) {
    if (search.isWindows()) {
      var localAppData = search.environmentVariableIgnoreCase("LOCALAPPDATA");
      if (localAppData != null && !localAppData.isBlank()) {
        search.executableNames(CLI_EXECUTABLE_NAME).forEach(name -> candidates.add(Path.of(localAppData, "sonarqube-cli", "bin", name)));
      }
      return;
    }
    candidates.add(userHome.resolve(".local/share/sonarqube-cli/bin").resolve(CLI_EXECUTABLE_NAME));
  }

  private Optional<String> readCliVersion(Path executable) {
    var stdout = new ArrayList<String>();
    var result = search.execute(executable, List.of("--version"), null, COMMAND_TIMEOUT_MILLIS, stdout::add);
    if (result.exitCode() != 0) {
      return Optional.empty();
    }
    var version = stdout.stream()
      .map(VERSION_PATTERN::matcher)
      .filter(Matcher::find)
      .map(Matcher::group)
      .findFirst();
    if (version.isEmpty()) {
      LOG.debug("SonarQube CLI at {} did not return a recognizable version", executable);
    }
    return version;
  }

  record CliLookup(CliInstallationStatus installationStatus, @Nullable Path path, @Nullable String version) {
  }
}
