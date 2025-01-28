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
import java.util.Date;
import java.util.regex.Pattern;
import org.sonar.scm.git.blame.BlameResult;
import org.sonarsource.sonarlint.core.commons.SonarLintBlameResult;

public class BlameParser {
  private BlameParser() {
    // Utility class
  }
  
  public static SonarLintBlameResult parseBlameOutput(String blameOutput, String currentFilePath, Path projectBaseDir) throws IOException {
    var blameResult = new BlameResult();
    var currentFileBlame = new BlameResult.FileBlame(currentFilePath, countCommitterTimeOccurrences(blameOutput));
    blameResult.getFileBlameByPath().put(currentFilePath, currentFileBlame);
    var fileSections = blameOutput.split("filename ");
    var currentLineNumber = 0;

    for (var fileSection : fileSections) {
      if (fileSection.isBlank()) {
        continue;
      }

      try (var reader = new BufferedReader(new StringReader(fileSection))) {
        String line;

        while ((line = reader.readLine()) != null) {
          if (line.startsWith("author-time ")) {
            var epochSeconds = Long.parseLong(line.substring(12));
            var commitDate = new Date(epochSeconds * 1000);
            currentFileBlame.getCommitDates()[currentLineNumber] = commitDate;
          }
        }
      }
      currentLineNumber++;
    }

    return new SonarLintBlameResult(blameResult, projectBaseDir);
  }

  public static int countCommitterTimeOccurrences(String blameOutput) {
    var pattern = Pattern.compile("^committer-time ", Pattern.MULTILINE);
    var matcher = pattern.matcher(blameOutput);
    var count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }
}
