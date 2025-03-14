/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.commons.util.git;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static org.sonarsource.sonarlint.core.commons.util.git.WinGitUtils.locateGitOnWindows;

public class NativeGitWrapper {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String MINIMUM_REQUIRED_GIT_VERSION = "2.24.0";
  private static final Pattern whitespaceRegex = Pattern.compile("\\s+");
  private static final Pattern semanticVersionDelimiter = Pattern.compile("\\.");
  // So we only have to make the expensive call once (or at most twice) to get the native Git executable
  private boolean checkedForNativeGitExecutable = false;
  private String nativeGitExecutable = null;

  String getGitExecutable() throws IOException {
    return SystemUtils.IS_OS_WINDOWS ? locateGitOnWindows() : "git";
  }

  private String executeCommandAndRecordOutput(Path workingDir, String[] command) throws IOException {
    var commandResult = new LinkedList<String>();
    new ProcessWrapperFactory()
      .create(workingDir, commandResult::add, command)
      .execute();
    return String.join(System.lineSeparator(), commandResult);
  }

  private boolean isCompatibleGitVersion(String gitVersionCommandOutput) {
    // Due to the danger of argument injection on git blame the use of `--end-of-options` flag is necessary
    // The flag is available only on git versions >= 2.24.0
    var gitVersion = whitespaceRegex
      .splitAsStream(gitVersionCommandOutput)
      .skip(2)
      .findFirst()
      .orElse("");

    var formattedGitVersion = formatGitSemanticVersion(gitVersion);
    return Version.create(formattedGitVersion).compareToIgnoreQualifier(Version.create(MINIMUM_REQUIRED_GIT_VERSION)) >= 0;
  }

  private static String formatGitSemanticVersion(String version) {
    return semanticVersionDelimiter
      .splitAsStream(version)
      .takeWhile(NumberUtils::isCreatable)
      .collect(Collectors.joining("."));
  }

  public Optional<String> executeGitCommand(Path workingDir, String... command) {
    try {
      return Optional.of(executeCommandAndRecordOutput(workingDir, command));
    } catch (Exception e) {
      LOG.warn("Failed to execute git command: " + String.join(" ", command), e);
      return Optional.empty();
    }
  }

  /**
   * Get the native Git executable by checking for the version of both `git` and `git.exe`. We cache this information
   * to run these expensive processes more than once (or twice in case of Windows).
   */
  @CheckForNull
  public String getNativeGitExecutable() {
    if (!checkedForNativeGitExecutable) {
      try {
        var executable = getGitExecutable();
        var process = new ProcessBuilder(executable, "--version").start();
        var exitCode = process.waitFor();
        if (exitCode == 0) {
          nativeGitExecutable = executable;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        LOG.debug("Checking for native Git executable failed", e);
      }
      checkedForNativeGitExecutable = true;
    }
    return nativeGitExecutable;
  }

  public boolean checkIfNativeGitEnabled(Path projectBaseDir) {
    var nativeExecutable = getNativeGitExecutable();
    if (nativeExecutable == null) {
      return false;
    }
    var output = executeGitCommand(projectBaseDir, nativeExecutable, "--version");
    return output.map(out -> out.contains("git version") && isCompatibleGitVersion(out)).orElse(false);
  }

}
