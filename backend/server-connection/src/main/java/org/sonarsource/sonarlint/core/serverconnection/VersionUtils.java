/*
 * SonarLint Core - Server Connection
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.serverconnection;

import org.sonarsource.sonarlint.core.commons.Version;

public class VersionUtils {

  private static final Version CURRENT_LTS_SHORT = Version.create("25.1");
  private static final Version CURRENT_LTS = Version.create("20" + CURRENT_LTS_SHORT);
  public static final Version MINIMAL_SUPPORTED_VERSION_SHORT = Version.create("9.9");
  // temporarily force a higher version to keep the check below correct
  private static final Version MINIMAL_SUPPORTED_VERSION = Version.create("2025.1");

  private VersionUtils() {
    // utility class
  }

  /**
   * Versions in the grace-period window should trigger the soon-unsupported warning.
   *
   * The check currently uses two version ranges: the legacy or Community Build short numbering from the minimal
   * supported short version (included) to the current Community Build LTS (excluded), and the SonarQube Server
   * full-year numbering from the temporary full-year lower bound (included) to the current Server LTS (excluded).
   */
  public static boolean isVersionSupportedDuringGracePeriod(Version currentVersion) {
    return (currentVersion.compareToIgnoreQualifier(MINIMAL_SUPPORTED_VERSION_SHORT) >= 0 && currentVersion.compareTo(CURRENT_LTS_SHORT) < 0)
      || (currentVersion.compareTo(MINIMAL_SUPPORTED_VERSION) >= 0 && currentVersion.compareTo(CURRENT_LTS) < 0);
  }

  public static Version getCurrentLts() {
    return CURRENT_LTS;
  }

  public static Version getCurrentCommunityBuildLts() {
    return CURRENT_LTS_SHORT;
  }
}
