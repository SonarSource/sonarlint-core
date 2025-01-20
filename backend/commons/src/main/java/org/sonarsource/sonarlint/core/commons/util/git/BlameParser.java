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
package org.sonarsource.sonarlint.core.commons.util.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sonar.scm.git.blame.BlameResult;
import org.sonarsource.sonarlint.core.commons.SonarLintBlameResult;

public class BlameParser {

  public static SonarLintBlameResult parseBlameOutput(String blameOutput, String currentFilePath, Path projectBaseDir) throws IOException, ParseException {
    BlameResult blameResult = new BlameResult();
    BlameResult.FileBlame currentFileBlame = new BlameResult.FileBlame(currentFilePath, countCommitterTimeOccurrences(blameOutput));
    blameResult.getFileBlameByPath().put(currentFilePath, currentFileBlame);
    String[] fileSections = blameOutput.split("filename ");
    int currentLineNumber = 0;

    for (String fileSection : fileSections) {
      if (fileSection.isBlank()) {
        continue;
      }

      try (BufferedReader reader = new BufferedReader(new StringReader(fileSection))) {
        String line;

        while ((line = reader.readLine()) != null) {
          if (line.startsWith("author ")) {
            String author = line.substring(7);
            currentFileBlame.getAuthorEmails()[currentLineNumber] = author;
          } else if (line.startsWith("author-time ")) {
            long epochSeconds = Long.parseLong(line.substring(12));
            Date commitDate = new Date(epochSeconds * 1000);
            currentFileBlame.getCommitDates()[currentLineNumber] = commitDate;
          } else if (line.startsWith("commit ")) {
            String commitHash = line.substring(7);
            currentFileBlame.getCommitHashes()[currentLineNumber] = commitHash;
          }
        }
      }
      currentLineNumber++;
    }

    return new SonarLintBlameResult(blameResult, projectBaseDir);
  }

  public static int countCommitterTimeOccurrences(String blameOutput) {
    Pattern pattern = Pattern.compile("^committer-time ", Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(blameOutput);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  private static int countLines(String blameOutput) {
    return blameOutput.split("\n").length;
  }

}
