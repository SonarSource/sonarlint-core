/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2024 SonarSource SA
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
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.sonar.scm.git.blame.BlameResult;

import static java.util.Objects.isNull;

public class SonarLintBlameResult {

  private final BlameResult blameResult;
  private final Path gitRepoRelativeProjectBaseDir;

  public SonarLintBlameResult(BlameResult blameResult, Path gitRepoRelativeProjectBaseDir) {
    this.blameResult = blameResult;
    this.gitRepoRelativeProjectBaseDir = gitRepoRelativeProjectBaseDir;
  }

  /**
   * @param projectDirRelativeFilePath A path relative to the Git repository root
   * @param lineNumbers Line numbers for which to check the latest change date. Numbering starts from `1`!
   * @return The latest changed date or an empty optional if the date couldn't be determined or any of the lines is modified
   */
  public Optional<Date> getLatestChangeDateForLinesInFile(Path projectDirRelativeFilePath, Collection<Integer> lineNumbers) {
    validateLineNumbersArgument(lineNumbers);
    var fileBlameByPath = blameResult.getFileBlameByPath();

    return Optional.of(projectDirRelativeFilePath.toString())
      .map(gitRepoRelativeProjectBaseDir::resolve)
      .map(Path::toString)
      .map(FilenameUtils::separatorsToUnix)
      .map(fileBlameByPath::get)
      .map(fileBlame -> getTheLatestChange(fileBlame, lineNumbers));
  }

  private static Date getTheLatestChange(BlameResult.FileBlame blameForFile, Collection<Integer> lineNumbers) {
    Date latestDate = null;
    for (var lineNumber : lineNumbers) {
      if (lineNumber > blameForFile.lines()) {
        continue;
      }
      var dateForLine = blameForFile.getCommitDates()[lineNumber - 1];
      if (isLineModified(dateForLine)) {
        return null;
      }
      latestDate = isNull(latestDate) || latestDate.before(dateForLine) ? dateForLine : latestDate;
    }
    return latestDate;
  }

  private static void validateLineNumbersArgument(Collection<Integer> lineNumbers) {
    if (lineNumbers.stream().anyMatch(i -> i < 1)) {
      throw new IllegalArgumentException("Line numbers must be greater than 0. The numbering starts from 1 (i.e. the " +
        "first line of a file should be `1`)");
    }
  }

  private static boolean isLineModified(@Nullable Date dateForLine) {
    return dateForLine == null;
  }
}
