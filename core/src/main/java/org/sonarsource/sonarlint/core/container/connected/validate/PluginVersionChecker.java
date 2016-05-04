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
package org.sonarsource.sonarlint.core.container.connected.validate;

import org.apache.commons.lang.StringUtils;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.util.VersionUtils;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

public class PluginVersionChecker {
  public static final String WS_PATH = "/api/plugins/installed";
  public static final String WS_PATH_LTS = "/deploy/plugins/index.txt";
  public static final String MIN_VERSIONS_FILE = "/plugins_min_versions.txt";

  private final SonarLintWsClient wsClient;

  public PluginVersionChecker(SonarLintWsClient client) {
    this.wsClient = client;
  }

  public ValidationResult validatePlugins() {
    return validatePlugins(null);
  }

  public ValidationResult validatePlugins(@Nullable String pluginsIndex) {
    String pluginsList = pluginsIndex;
    if (pluginsList == null) {
      WsResponse response = wsClient.get(WS_PATH_LTS);
      pluginsList = response.content();
    }
    Properties minimalPluginVersions = new Properties();
    try {
      minimalPluginVersions.load(this.getClass().getResourceAsStream(MIN_VERSIONS_FILE));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load minimum plugin versions", e);
    }

    List<InstalledPlugin> invalidPlugins = doValidate(pluginsList, minimalPluginVersions);

    if (!invalidPlugins.isEmpty()) {
      return new DefaultValidationResult(false, buildFailMessage(minimalPluginVersions, invalidPlugins));
    } else {
      return new DefaultValidationResult(true, "Plugins meet required minimum versions");
    }
  }

  private static List<InstalledPlugin> doValidate(String pluginsIndex, Properties minimalPluginVersions) {
    List<InstalledPlugin> invalidPlugins = new LinkedList<>();

    try (Scanner scanner = new Scanner(pluginsIndex)) {
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        String[] fields = StringUtils.split(line, ",");
        String[] nameAndHash = StringUtils.split(fields[fields.length - 1], "|");
        String key = fields[0];
        String filename = nameAndHash[0];
        String version = VersionUtils.getJarVersion(filename);

        if (version != null && minimalPluginVersions.containsKey(key) && !validate(minimalPluginVersions.getProperty(key), version)) {
          InstalledPlugin plugin = new InstalledPlugin();
          plugin.setKey(key);
          plugin.setName(key);
          plugin.setVersion(version);
          invalidPlugins.add(plugin);
        }
      }
    }

    return invalidPlugins;
  }

  public void checkPlugins(@Nullable String pluginsIndex) {
    ValidationResult result = validatePlugins(pluginsIndex);
    if (!result.success()) {
      throw new UnsupportedServerException(result.message());
    }
  }

  public void checkPlugins() {
    checkPlugins(null);
  }

  private static String buildFailMessage(Properties props, List<InstalledPlugin> failingPlugins) {
    StringBuilder builder = new StringBuilder();
    builder.append("The following plugins do not meet the required minimum versions, please upgrade them: ");

    boolean first = true;
    for (InstalledPlugin p : failingPlugins) {
      if (!first) {
        builder.append(",");
      } else {
        first = false;
      }
      builder.append(p.getName())
        .append(" (installed: ")
        .append(p.getVersion())
        .append(", minimum: ")
        .append(props.getProperty(p.getKey()))
        .append(")");
    }

    return builder.toString();
  }

  private static boolean validate(String minVersion, String version) {
    Version min = Version.create(minVersion);
    Version v = Version.create(version);

    return v.compareTo(min) >= 0;
  }
}
