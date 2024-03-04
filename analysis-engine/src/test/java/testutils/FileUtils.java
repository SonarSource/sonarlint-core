/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2023 SonarSource SA
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
package testutils;

import java.io.File;
import java.util.regex.Pattern;

public class FileUtils {

  private static final String PATH_SEPARATOR_PATTERN = Pattern.quote(File.separator);

  /**
   * Converts path to format used by SonarQube
   *
   * @param path path string in the local OS
   * @return SonarQube path
   */
  public static String toSonarQubePath(String path) {
    if (File.separatorChar != '/') {
      return path.replaceAll(PATH_SEPARATOR_PATTERN, "/");
    }
    return path;
  }

}
