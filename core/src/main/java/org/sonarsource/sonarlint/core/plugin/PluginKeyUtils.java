/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.plugin;

import org.apache.commons.lang.StringUtils;

/**
 * @since 0.4
 */
public final class PluginKeyUtils {

  private static final String SONAR_PLUGIN_SUFFIX = "-sonar-plugin";
  private static final String SONAR_PREFIX = "sonar-";
  private static final String PLUGIN_SUFFIX = "-plugin";

  private PluginKeyUtils() {
    // only static methods
  }

  public static String sanitize(String mavenArtifactId) {
    if (mavenArtifactId == null) {
      return null;
    }

    String key = mavenArtifactId;
    if (StringUtils.startsWith(mavenArtifactId, SONAR_PREFIX) && StringUtils.endsWith(mavenArtifactId, PLUGIN_SUFFIX)) {
      key = StringUtils.removeEnd(StringUtils.removeStart(mavenArtifactId, SONAR_PREFIX), PLUGIN_SUFFIX);
    } else if (StringUtils.endsWith(mavenArtifactId, SONAR_PLUGIN_SUFFIX)) {
      key = StringUtils.removeEnd(mavenArtifactId, SONAR_PLUGIN_SUFFIX);
    }
    return keepLettersAndDigits(key);
  }

  private static String keepLettersAndDigits(String key) {
    StringBuilder sb = new StringBuilder();
    for (int index = 0; index < key.length(); index++) {
      char character = key.charAt(index);
      if (Character.isLetter(character) || Character.isDigit(character)) {
        sb.append(character);
      }
    }
    return sb.toString();
  }

  public static boolean isValid(String pluginKey) {
    return StringUtils.isNotBlank(pluginKey) && StringUtils.isAlphanumeric(pluginKey);
  }

}
