/*
 * SonarLint Core - Plugin Commons
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
package org.sonarsource.sonarlint.core.plugin.commons;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.Plugin;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInstancesLoader;

public class LoadedPlugins {
  private final Map<String, Plugin> pluginInstancesByKeys;
  private final PluginInstancesLoader pluginInstancesLoader;
  private final Set<String> additionalAllowedPlugins;
  private final Set<String> disabledPluginKeys;

  public LoadedPlugins(Map<String, Plugin> pluginInstancesByKeys, PluginInstancesLoader pluginInstancesLoader,
    Set<String> additionalAllowedPlugins, Set<String> disabledPluginKeys) {
    this.pluginInstancesByKeys = pluginInstancesByKeys;
    this.pluginInstancesLoader = pluginInstancesLoader;
    this.additionalAllowedPlugins = additionalAllowedPlugins;
    this.disabledPluginKeys = disabledPluginKeys;
  }

  public Map<String, Plugin> getAllPluginInstancesByKeys() {
    return pluginInstancesByKeys;
  }

  public Map<String, Plugin> getAnalysisPluginInstancesByKeys() {
    return pluginInstancesByKeys.entrySet().stream()
      .filter(entry -> !disabledPluginKeys.contains(entry.getKey()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public Set<String> getAdditionalAllowedPlugins() {
    return additionalAllowedPlugins;
  }

  public void close() throws IOException {
    // close plugins classloaders
    pluginInstancesByKeys.clear();
    pluginInstancesLoader.close();
  }
}
