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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.regex.Pattern;
import org.sonar.scm.git.blame.BlameResult;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class BlameParser {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String FILENAME_PORCELAIN_REGEXP = "\nfilename ";
  private static final String AUTHOR_MAIL = "author-mail ";
  private static final String COMMITTER_TIME = "committer-time ";
  // if this text change between different git versions it will break the implementation
  private static final String NOT_COMMITTED = "<not.committed.yet>";

  private BlameParser() {
    // Utility class
  }

  public static void parseBlameOutput(String blameOutput, String currentFilePath, BlameResult blameResult) {
    var numberOfLines = numberOfLinesInBlameOutput(blameOutput);
    if (numberOfLines == 0) {
      return;
    }
    var currentFileBlame = new BlameResult.FileBlame(currentFilePath, numberOfLines);
    blameResult.getFileBlameByPath().put(currentFilePath, currentFileBlame);
    var lineBlameSnippets = blameOutput.split(FILENAME_PORCELAIN_REGEXP);
    var currentLineNumber = 0;

    // because of the way we split the blame output, the last snippet should be skipped
    for (var i = 0; i < lineBlameSnippets.length - 1; i++) {
      var fileSection = lineBlameSnippets[i];

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
            currentFileBlame.getCommitDates()[currentLineNumber] = commitDate.toInstant();
          }
        }
      } catch (IOException e) {
        LOG.warn("Failed to blame repository files", e);
        return;
      }
      currentLineNumber++;
    }
  }

  public static int numberOfLinesInBlameOutput(String blameOutput) {
    var pattern = Pattern.compile(FILENAME_PORCELAIN_REGEXP, Pattern.MULTILINE);
    var matcher = pattern.matcher(blameOutput);
    var count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }
}
