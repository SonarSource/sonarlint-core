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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandException;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.os.OsSearchPath;

final class OsExecutableSearch {

  private static final long PATH_RESOLVE_TIMEOUT_MILLIS = 30_000L;

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final System2 system2;
  private final CommandExecutor commandExecutor;
  private final Map<String, String> environment;
  private final Path pathHelperLocation;
  private boolean pathResolved;
  @Nullable
  private String resolvedPath;

  OsExecutableSearch(System2 system2, CommandExecutor commandExecutor, Map<String, String> environment,
    Path pathHelperLocation) {
    this.system2 = system2;
    this.commandExecutor = commandExecutor;
    this.environment = environment;
    this.pathHelperLocation = pathHelperLocation;
  }

  boolean isWindows() {
    return system2.isOsWindows();
  }

  boolean isMac() {
    return system2.isOsMac();
  }

  @Nullable
  synchronized String resolvePath() {
    if (!pathResolved) {
      resolvedPath = OsSearchPath.resolve(system2, environment, pathHelperLocation, commandExecutor,
        PATH_RESOLVE_TIMEOUT_MILLIS);
      pathResolved = true;
    }
    return resolvedPath;
  }

  Set<Path> pathDirectories(@Nullable String path, boolean absoluteOnly) {
    var directories = new LinkedHashSet<Path>();
    if (path == null) {
      return directories;
    }
    var separator = system2.isOsWindows() ? ";" : ":";
    for (var directory : OsSearchPath.splitEntries(path, separator)) {
      addDirectory(directories, directory, absoluteOnly);
    }
    return directories;
  }

  List<String> executableNames(String baseName) {
    return system2.isOsWindows() ? List.of(baseName + ".exe", baseName + ".cmd", baseName) : List.of(baseName);
  }

  boolean isExecutable(Path path) {
    return Files.isRegularFile(path) && (system2.isOsWindows() || Files.isExecutable(path));
  }

  @Nullable
  String environmentVariableIgnoreCase(String name) {
    return OsSearchPath.environmentVariableIgnoreCase(environment, name);
  }

  CommandResult execute(Path executable, List<String> arguments, @Nullable String pathOverride, long timeoutMillis,
    Consumer<String> stdout) {
    return execute(executable, arguments, pathOverride, timeoutMillis, stdout, line -> {
    });
  }

  CommandResult execute(Path executable, List<String> arguments, @Nullable String pathOverride, long timeoutMillis,
    Consumer<String> stdout, Consumer<String> stderr) {
    var command = Command.create(executable.toString());
    environment.forEach(command::setEnvironmentVariable);
    if (pathOverride != null) {
      command.setEnvironmentVariable("PATH", pathOverride);
    }
    arguments.forEach(command::addArgument);
    try {
      var exitCode = commandExecutor.execute(command, stdout::accept, stderr::accept, timeoutMillis);
      return new CommandResult(exitCode);
    } catch (CommandException e) {
      LOG.debug("Unable to execute command at {}", executable, e);
      return new CommandResult(-1);
    }
  }

  private static void addDirectory(Set<Path> directories, String directory, boolean absoluteOnly) {
    if (directory.isBlank()) {
      return;
    }
    try {
      var candidate = Paths.get(directory);
      if (!absoluteOnly || candidate.isAbsolute()) {
        directories.add(candidate);
      }
    } catch (InvalidPathException e) {
      LOG.debug("Ignoring an invalid PATH entry", e);
    }
  }

  record CommandResult(int exitCode) {
  }
}
