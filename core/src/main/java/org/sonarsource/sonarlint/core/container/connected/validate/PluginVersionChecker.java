/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.validate;

import java.io.IOException;
import java.util.Properties;

import javax.annotation.CheckForNull;

public class PluginVersionChecker {
  public static final String MIN_VERSIONS_FILE = "/plugins_min_versions.txt";
  private static final String STREAM_SUPPORT_FILE = "/plugins_stream_support.txt";

  private final Properties minimalPluginVersions;
  private final Properties streamSupportVersions;

  public PluginVersionChecker() {
    this.minimalPluginVersions = new Properties();
    this.streamSupportVersions = new Properties();
    try {
      minimalPluginVersions.load(this.getClass().getResourceAsStream(MIN_VERSIONS_FILE));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load minimum plugin versions", e);
    }

    try {
      streamSupportVersions.load(this.getClass().getResourceAsStream(STREAM_SUPPORT_FILE));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load list of plugins supporting file content stream", e);
    }
  }

  @CheckForNull
  public String getMinimumVersion(String key) {
    return minimalPluginVersions.getProperty(key);
  }

  @CheckForNull
  public String getMinimumStreamSupportVersion(String key) {
    return streamSupportVersions.getProperty(key);
  }
}
