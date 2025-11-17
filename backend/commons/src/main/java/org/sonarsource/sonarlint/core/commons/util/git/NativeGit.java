/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.MultiFileBlameResult;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.util.FileUtils;

import static java.lang.String.format;

public class NativeGit {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final Version MINIMUM_REQUIRED_GIT_VERSION = Version.create("2.24");
  private static final String GIT_VERSION_OUTPUT_PREFIX = "git version";
  private static final Period BLAME_HISTORY_WINDOW = Period.ofDays(365);
  private final String executable;

  public NativeGit(String executable) {
    this.executable = executable;
  }

  public boolean isSupportedVersion() {
    return version()
      .filter(version -> version.satisfiesMinRequirement(MINIMUM_REQUIRED_GIT_VERSION))
      .isPresent();
  }

  private Optional<Version> version() {
    var lines = new ArrayList<String>();
    var success = executeGitCommand(null, lines::add, executable, "--version");
    return success ? parseGitVersionOutput(lines) : Optional.empty();
  }

  static Optional<Version> parseGitVersionOutput(List<String> lines) {
    var version = lines.stream().findFirst()
      .map(String::trim)
      .filter(line -> line.startsWith(GIT_VERSION_OUTPUT_PREFIX))
      .map(line -> line.substring(GIT_VERSION_OUTPUT_PREFIX.length()))
      .map(String::trim)
      .map(actualVersion -> actualVersion.split("\\.", 3))
      .filter(versionParts -> versionParts.length > 1)
      .flatMap(NativeGit::tryCreateVersion);
    if (version.isEmpty()) {
      LOG.debug("Cannot parse git --version output: {}", String.join("\n", lines));
    }
    return version;
  }

  private static Optional<Version> tryCreateVersion(String[] versionParts) {
    try {
      // keep only MAJOR and MINOR numbers, it's sufficient for checking support
      return Optional.of(Version.create(versionParts[0] + "." + versionParts[1]));
    } catch (Exception e) {
      // error will be logged above
    }
    return Optional.empty();
  }

  public MultiFileBlameResult blame(Path projectBaseDir, Set<URI> fileUris, Instant thresholdDateFromNewCodeDefinition) {
    LOG.debug("Using native git blame");
    var startTime = System.currentTimeMillis();
    var blamePerFile = new HashMap<String, BlameResult>();
    for (var fileUri : fileUris) {
      var filePath = FileUtils.getFilePathFromUri(fileUri).toAbsolutePath().toString();
      var filePathUnix = filePath.replace("\\", "/");
      var yearAgo = Instant.now().minus(BLAME_HISTORY_WINDOW);
      var thresholdDate = thresholdDateFromNewCodeDefinition.isAfter(yearAgo) ? yearAgo : thresholdDateFromNewCodeDefinition;
      var blameHistoryThresholdCondition = "--since='" + thresholdDate + "'";
      var command = new String[] {executable, "blame", blameHistoryThresholdCondition, filePath, "--line-porcelain", "--encoding=UTF-8"};
      var blameReader = new GitBlameReader();
      var success = executeGitCommand(projectBaseDir, blameReader::readLine, command);
      if (success) {
        blamePerFile.put(filePathUnix, blameReader.getResult());
      }
    }
    LOG.debug("Blamed {} files in {}ms", fileUris.size(), System.currentTimeMillis() - startTime);
    return new MultiFileBlameResult(blamePerFile, projectBaseDir);
  }

  private static boolean executeGitCommand(@Nullable Path workingDir, Consumer<String> lineConsumer, String... command) {
    var output = new ProcessWrapperFactory()
      .create(workingDir, lineConsumer, command)
      .execute();
    if (output.exitCode() == 0) {
      return true;
    }
    LOG.debug(format("Command failed with code: %d", output.exitCode()));
    return false;
  }
}
