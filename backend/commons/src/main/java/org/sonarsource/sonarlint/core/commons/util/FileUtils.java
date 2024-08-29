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
package org.sonarsource.sonarlint.core.commons.util;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class FileUtils {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private FileUtils() {
    // utility class
  }

  public static String getFileRelativePath(Path gitRepoBaseDir, URI fileUri, boolean addCommonPath) {
    var filePath = Path.of(fileUri);
    LOG.debug("Relativizing path: {} for git repo {}", filePath, gitRepoBaseDir);

    var filePathDirectories = getPathDirectories(filePath);
    var baseDirDirectories = getPathDirectories(gitRepoBaseDir);
    return findFuzzyRelativeFilePath(baseDirDirectories, filePathDirectories, addCommonPath);
  }

  public static List<String> getPathDirectories(Path file) {
    var directories = new ArrayList<String>();
    do {
      directories.add(file.getFileName().toString());
      file = file.getParent();
    } while (file.getParent() != null);
    if (file.getFileName() != null) {
      directories.add(file.getFileName().toString());
    }
    Collections.reverse(directories);
    return directories;
  }

  public static String findFuzzyRelativeFilePath(List<String> baseDir, List<String> filePath, boolean addCommonPath) {
    var relativeFilePath = new ArrayList<String>();
    var baseDirPointer = 0;
    var filePathPointer = 0;

    while (baseDirPointer < baseDir.size() && filePathPointer < filePath.size()) {
      var baseDirElement = baseDir.get(baseDirPointer);
      var filePathElement = filePath.get(filePathPointer);
      if (!baseDirElement.equals(filePathElement)) {
        filePathPointer++;
      } else {
        baseDirPointer++;
        filePathPointer++;
        if (addCommonPath) {
          relativeFilePath.add(filePathElement);
        }
      }
    }
    while (filePathPointer < filePath.size()) {
      relativeFilePath.add(filePath.get(filePathPointer));
      filePathPointer++;
    }
    return String.join("/", relativeFilePath);
  }

}
