/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons;

import java.io.IOException;
import java.util.Properties;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class PluginsMinVersions {
  public static final String MIN_VERSIONS_FILE = "/plugins_min_versions.txt";

  private final Properties minimalPluginVersions;

  public PluginsMinVersions() {
    this.minimalPluginVersions = new Properties();
    try {
      minimalPluginVersions.load(this.getClass().getResourceAsStream(MIN_VERSIONS_FILE));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load minimum plugin versions", e);
    }

  }

  @CheckForNull
  public String getMinimumVersion(String key) {
    return minimalPluginVersions.getProperty(key);
  }

  public boolean isVersionSupported(String key, @Nullable String version) {
    if (version != null) {
      var v = Version.create(version);
      return isVersionSupported(key, v);
    }
    return true;
  }

  public boolean isVersionSupported(String key, @Nullable Version version) {
    var minVersion = getMinimumVersion(key);
    if (version != null && minVersion != null) {
      var minimalVersion = Version.create(minVersion);
      return version.compareToIgnoreQualifier(minimalVersion) >= 0;
    }
    return true;
  }
}
