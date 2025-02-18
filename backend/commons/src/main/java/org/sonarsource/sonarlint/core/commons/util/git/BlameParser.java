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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.regex.Pattern;
import org.sonar.scm.git.blame.BlameResult;
import org.sonarsource.sonarlint.core.commons.SonarLintBlameResult;

public class BlameParser {

  private static final String FILENAME = "filename ";
  private static final String AUTHOR_MAIL = "author-mail ";
  private static final String COMMITTER_TIME = "committer-time ";
  // if this text change between different git versions it will break the implementation
  private static final String NOT_COMMITTED = "<not.committed.yet>";
  private BlameParser() {
    // Utility class
  }

  public static SonarLintBlameResult parseBlameOutput(String blameOutput, String currentFilePath, Path projectBaseDir) throws IOException {
    var blameResult = new BlameResult();
    var currentFileBlame = new BlameResult.FileBlame(currentFilePath, countCommitterTimeOccurrences(blameOutput));
    blameResult.getFileBlameByPath().put(currentFilePath, currentFileBlame);
    var fileSections = blameOutput.split(FILENAME);
    var currentLineNumber = 0;

    for (var fileSection : fileSections) {
      if (fileSection.isBlank()) {
        continue;
      }

      try (var reader = new BufferedReader(new StringReader(fileSection))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.startsWith(AUTHOR_MAIL)) {
            var authorEmail = line.substring(AUTHOR_MAIL.length());
            currentFileBlame.getAuthorEmails()[currentLineNumber] = authorEmail;
          }
          if (line.startsWith(COMMITTER_TIME) && !currentFileBlame.getAuthorEmails()[currentLineNumber].equals(NOT_COMMITTED)) {
            var committerTime = line.substring(COMMITTER_TIME.length());
            var commitDate = Date.from(Instant.ofEpochSecond(Long.parseLong(committerTime)).truncatedTo(ChronoUnit.SECONDS));
            currentFileBlame.getCommitDates()[currentLineNumber] = commitDate;
          }
        }
      }
      currentLineNumber++;
    }

    return new SonarLintBlameResult(blameResult, projectBaseDir);
  }

  public static int countCommitterTimeOccurrences(String blameOutput) {
    var pattern = Pattern.compile("^" + COMMITTER_TIME, Pattern.MULTILINE);
    var matcher = pattern.matcher(blameOutput);
    var count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }
}
