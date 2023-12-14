/*
 * SonarLint Core - Plugin Commons
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
package org.sonarsource.sonarlint.core.plugin.commons;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginRequirementsCheckResult;

public class PluginsLoadResult implements Closeable {
  private final LoadedPlugins loadedPlugins;
  private final Map<String, PluginRequirementsCheckResult> pluginCheckResultByKeys;

  PluginsLoadResult(LoadedPlugins loadedPlugins, Map<String, PluginRequirementsCheckResult> pluginCheckResultByKeys) {
    this.loadedPlugins = loadedPlugins;
    this.pluginCheckResultByKeys = pluginCheckResultByKeys;
  }

  public LoadedPlugins getLoadedPlugins() {
    return loadedPlugins;
  }

  public Map<String, PluginRequirementsCheckResult> getPluginCheckResultByKeys() {
    return pluginCheckResultByKeys;
  }

  @Override
  public void close() throws IOException {
    loadedPlugins.close();
  }
}
