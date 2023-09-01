/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.commons.Version;

public class VersionUtils {

  private static final Version CURRENT_LTS = Version.create("9.9");
  private static final Version PREVIOUS_LTS = Version.create("8.9");
  private static final Instant CURRENT_LTS_RELEASE_DATE = ZonedDateTime.of(2023, 2, 1, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant();
  private static final String VERSION_REGEX = ".*?(\\d+\\.\\d+(?:\\.\\d+)*).*";
  private static final Pattern JAR_VERSION_PATTERN = Pattern.compile(VERSION_REGEX);

  private VersionUtils() {
  }

  public static boolean isVersionSupportedDuringGracePeriod(Version currentVersion) {
    return currentVersion.compareTo(CURRENT_LTS) < 0 && currentVersion.compareToIgnoreQualifier(PREVIOUS_LTS) >= 0
      && ZonedDateTime.now().minusYears(1).toInstant().compareTo(CURRENT_LTS_RELEASE_DATE) < 0;
  }

  public static Version getCurrentLts() {
    return CURRENT_LTS;
  }

  public static Version getPreviousLts() {
    return PREVIOUS_LTS;
  }

  @CheckForNull
  public static String getJarVersion(String jarName) {
    var matcher = JAR_VERSION_PATTERN.matcher(jarName);
    if (matcher.find()) {
      return matcher.group(1);
    }

    return null;
  }
}
