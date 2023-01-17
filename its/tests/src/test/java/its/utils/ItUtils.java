/*
 * SonarLint Core - ITs - Tests
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
package its.utils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ItUtils {

  public static final String LATEST_RELEASE = "LATEST_RELEASE";
  public static final String SONAR_VERSION = getSonarVersion();

  private ItUtils() {
    // utility class, forbidden constructor
  }

  private static String getSonarVersion() {
    var versionProperty = System.getProperty("sonar.runtimeVersion");
    return versionProperty != null ? versionProperty : LATEST_RELEASE;
  }

  public static List<Path> collectAllFiles(Path path) throws IOException {
    var fileFinder = new InputFileFinder(null);
    return fileFinder.collect(path);
  }

}
