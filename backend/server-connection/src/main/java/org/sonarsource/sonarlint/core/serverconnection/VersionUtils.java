/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import org.sonarsource.sonarlint.core.commons.Version;

public class VersionUtils {

  private static final Version CURRENT_LTS = Version.create("2025.1");
  private static final Version MINIMAL_SUPPORTED_VERSION = Version.create("9.9");

  private VersionUtils() {
  }

  /**
   *  When the CURRENT_LTS matches the MINIMAL_SUPPORTED_VERSION, this method will always return false. If this is not
   *  the case, it returns true. Therefore for every change in the LTS/LTA and minimal supported version the test at
   *  {@link VersionUtilsTest#grace_period_should_be_true_if_connected_during_grace_period} has to be adjusted!
   */
  public static boolean isVersionSupportedDuringGracePeriod(Version currentVersion) {
    return currentVersion.compareTo(CURRENT_LTS) < 0 &&
      currentVersion.compareToIgnoreQualifier(MINIMAL_SUPPORTED_VERSION) >= 0;
  }

  public static Version getCurrentLts() {
    return CURRENT_LTS;
  }

  public static Version getMinimalSupportedVersion() {
    return MINIMAL_SUPPORTED_VERSION;
  }
}
