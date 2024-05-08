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
import org.sonar.scm.git.blame.BlameResult;

import static java.util.Objects.isNull;

public class SonarLintBlameResult {

  private final BlameResult blameResult;

  public SonarLintBlameResult(BlameResult blameResult) {
    this.blameResult = blameResult;
  }

  public Optional<Date> getLatestChangeDateForLinesInFile(Path filePath, Collection<Integer> lineNumbers) {
    var fileBlameByPath = blameResult.getFileBlameByPath();
    var blameForFile = fileBlameByPath.get(filePath.toString());
    if (blameForFile == null) {
      return Optional.empty();
    }

    Date latestDate = null;
    for (var lineNumber : lineNumbers) {
      if (lineNumber < 1 || lineNumber > blameForFile.lines()) {
        continue;
      }
      var dateForLine = blameForFile.getCommitDates()[lineNumber - 1];
      if (isLineModified(dateForLine)) {
        return Optional.empty();
      }
      latestDate = isNull(latestDate) || latestDate.before(dateForLine) ? dateForLine : latestDate;
    }
    return Optional.ofNullable(latestDate);
  }

  private static boolean isLineModified(@Nullable Date dateForLine) {
    return dateForLine == null;
  }

  public BlameResult getBlameResult() {
    return blameResult;
  }
}
