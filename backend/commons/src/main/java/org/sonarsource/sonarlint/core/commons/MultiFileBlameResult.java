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
package org.sonarsource.sonarlint.core.commons;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.sonarsource.sonarlint.core.commons.util.git.BlameResult;

import static java.util.Objects.isNull;

public class MultiFileBlameResult {

  private final Map<String, BlameResult> blameResultPerFile;
  private final Path gitRepoRelativeProjectBaseDir;

  public MultiFileBlameResult(Map<String, BlameResult> blameResultPerFile, Path gitRepoRelativeProjectBaseDir) {
    this.blameResultPerFile = blameResultPerFile;
    this.gitRepoRelativeProjectBaseDir = gitRepoRelativeProjectBaseDir;
  }

  public static MultiFileBlameResult empty(Path gitRepoRelativeProjectBaseDir) {
    return new MultiFileBlameResult(Map.of(), gitRepoRelativeProjectBaseDir);
  }

  /**
   * @param projectDirRelativeFilePath A path relative to the Git repository root
   * @param lineNumbers Line numbers for which to check the latest change date. Numbering starts from `1`!
   * @return The latest changed date or an empty optional if the date couldn't be determined or any of the lines is modified
   */
  public Optional<Instant> getLatestChangeDateForLinesInFile(Path projectDirRelativeFilePath, Collection<Integer> lineNumbers) {
    validateLineNumbersArgument(lineNumbers);

    return Optional.of(projectDirRelativeFilePath.toString())
      .map(gitRepoRelativeProjectBaseDir::resolve)
      .map(Path::toString)
      .map(FilenameUtils::separatorsToUnix)
      .map(blameResultPerFile::get)
      .map(fileBlame -> getTheLatestChange(fileBlame, lineNumbers));
  }

  private static Instant getTheLatestChange(BlameResult blameForFile, Collection<Integer> lineNumbers) {
    Instant latestDate = null;
    for (var lineNumber : lineNumbers) {
      if (lineNumber > blameForFile.lineCommitDates().size()) {
        continue;
      }
      var dateForLine = blameForFile.lineCommitDates().get(lineNumber - 1);
      if (isLineModified(dateForLine)) {
        return null;
      }
      latestDate = isNull(latestDate) || latestDate.isBefore(dateForLine) ? dateForLine : latestDate;
    }
    return latestDate;
  }

  private static void validateLineNumbersArgument(Collection<Integer> lineNumbers) {
    if (lineNumbers.stream().anyMatch(i -> i < 1)) {
      throw new IllegalArgumentException("Line numbers must be greater than 0. The numbering starts from 1 (i.e. the " +
        "first line of a file should be `1`)");
    }
  }

  private static boolean isLineModified(@Nullable Instant dateForLine) {
    return dateForLine == null;
  }
}
