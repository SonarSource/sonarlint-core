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
package org.sonarsource.sonarlint.core.nodejs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandException;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public final class OsSearchPath {

  public static final Path MAC_OS_PATH_HELPER = Paths.get("/usr/libexec/path_helper");

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final Pattern PATH_HELPER_OUTPUT_PATTERN = Pattern.compile("^\\s*PATH=\"([^\"]+)\"; export PATH;?\\s*$");

  private OsSearchPath() {
  }

  @Nullable
  public static String resolve(System2 system2, Map<String, String> environment, Path pathHelperLocation,
    CommandExecutor commandExecutor, long timeoutMillis) {
    if (system2.isOsMac()) {
      var fromHelper = readMacOsPath(pathHelperLocation, commandExecutor, timeoutMillis);
      if (fromHelper != null) {
        return fromHelper;
      }
    }
    return system2.isOsWindows()
      ? environmentVariableIgnoreCase(environment, "PATH")
      : environment.get("PATH");
  }

  @Nullable
  public static String readMacOsPath(Path pathHelperLocation, CommandExecutor commandExecutor, long timeoutMillis) {
    if (!Files.exists(pathHelperLocation)) {
      return null;
    }
    var stdout = new ArrayList<String>();
    var stderr = new ArrayList<String>();
    var command = Command.create(pathHelperLocation.toString()).addArgument("-s");
    try {
      var exitCode = commandExecutor.execute(command, stdout::add, stderr::add, timeoutMillis);
      if (exitCode != 0) {
        return null;
      }
      var path = stdout.stream()
        .map(OsSearchPath::parsePathHelperOutput)
        .flatMap(Optional::stream)
        .findFirst();
      if (path.isPresent()) {
        return path.get();
      }
      LOG.debug("Unable to read PATH from macOS path_helper output");
      return null;
    } catch (CommandException e) {
      LOG.debug("Unable to execute command at {}", pathHelperLocation, e);
      return null;
    }
  }

  public static Optional<String> parsePathHelperOutput(String line) {
    var matcher = PATH_HELPER_OUTPUT_PATTERN.matcher(line);
    return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  public static String[] splitEntries(String path, String separator) {
    return path.split(Pattern.quote(separator));
  }

  @Nullable
  public static String environmentVariableIgnoreCase(Map<String, String> environment, String name) {
    var value = environment.get(name);
    if (value != null) {
      return value;
    }
    return environment.entrySet().stream()
      .filter(entry -> entry.getKey().equalsIgnoreCase(name))
      .map(Map.Entry::getValue)
      .findFirst()
      .orElse(null);
  }
}
