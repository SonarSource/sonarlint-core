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

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Period;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.sonar.scm.git.blame.BlameResult;
import org.sonarsource.sonarlint.core.commons.SonarLintBlameResult;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.util.FileUtils;

import static java.lang.String.format;
import static org.sonarsource.sonarlint.core.commons.util.git.BlameParser.parseBlameOutput;
import static org.sonarsource.sonarlint.core.commons.util.git.WinGitUtils.locateGitOnWindows;

public class NativeGitWrapper {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String MINIMUM_REQUIRED_GIT_VERSION = "2.24.0";
  private static final Pattern whitespaceRegex = Pattern.compile("\\s+");
  private static final Pattern semanticVersionDelimiter = Pattern.compile("\\.");
  private static final Period blameHistoryWindow = Period.ofDays(365);
  // So we only have to make the expensive call once (or at most twice) to get the native Git executable
  private boolean checkedForNativeGitExecutable = false;
  private String nativeGitExecutable = null;

  Optional<String> getGitExecutable() {
    return SystemUtils.IS_OS_WINDOWS ? locateGitOnWindows() : Optional.of("git");
  }

  public SonarLintBlameResult blameFromNativeCommand(Path projectBaseDir, Set<URI> fileUris, Instant thresholdDateFromNewCodeDefinition) {
    var blameResult = new BlameResult();
    getNativeGitExecutable().ifPresent(gitExecutable -> {
      for (var fileUri : fileUris) {
        var filePath = FileUtils.getFilePathFromUri(fileUri).toAbsolutePath().toString();
        var filePathUnix = filePath.replace("\\", "/");
        var yearAgo = Instant.now().minus(blameHistoryWindow);
        var thresholdDate = thresholdDateFromNewCodeDefinition.isAfter(yearAgo) ? yearAgo : thresholdDateFromNewCodeDefinition;
        var blameHistoryThresholdCondition = "--since='" + thresholdDate + "'";
        var command = new String[]{gitExecutable, "blame", blameHistoryThresholdCondition, filePath, "--line-porcelain", "--encoding=UTF-8"};
        executeGitCommand(projectBaseDir, command)
          .ifPresent(blameOutput -> parseBlameOutput(blameOutput, filePathUnix, blameResult));
      }
    });
    return new SonarLintBlameResult(blameResult, projectBaseDir);
  }

  private static boolean isCompatibleGitVersion(String gitVersionCommandOutput) {
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
    var output = new ProcessWrapperFactory()
      .create(workingDir, command)
      .execute();
    if (output.exitCode() == 0) {
      return Optional.of(output.output());
    }
    LOG.debug(format("Command failed with code: %d and output %s", output.exitCode(), output.output()));
    return Optional.empty();
  }

  /**
   * Get the native Git executable by checking for the version of both `git` and `git.exe`. We cache this information
   * to not run these expensive processes more than once (or twice in case of Windows).
   */
  public Optional<String> getNativeGitExecutable() {
    if (checkedForNativeGitExecutable) {
      return Optional.ofNullable(nativeGitExecutable);
    }
    return getGitExecutable().map(git -> {
      var result = new ProcessWrapperFactory().create(null, git, "--version").execute();
      var exitCode = result.exitCode();
      if (exitCode == 0) {
        nativeGitExecutable = git;
      }
      checkedForNativeGitExecutable = true;
      return nativeGitExecutable;
    });
  }

  public boolean checkIfNativeGitEnabled(Path projectBaseDir) {
    return getNativeGitExecutable().map(gitExecutable -> {
      var output = executeGitCommand(projectBaseDir, gitExecutable, "--version");
      return output.map(out -> out.contains("git version") && isCompatibleGitVersion(out)).orElse(false);
    }).orElse(false);
  }
}
