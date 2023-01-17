/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;

public class PluginsRepository {
  private LoadedPlugins loadedEmbeddedPlugins;
  private final Map<String, LoadedPlugins> loadedPluginsByConnectionId = new HashMap<>();

  public void setLoadedEmbeddedPlugins(LoadedPlugins loadedEmbeddedPlugins) {
    this.loadedEmbeddedPlugins = loadedEmbeddedPlugins;
  }

  public LoadedPlugins getLoadedEmbeddedPlugins() {
    return loadedEmbeddedPlugins;
  }

  @CheckForNull
  public LoadedPlugins getLoadedPlugins(String connectionId) {
    return loadedPluginsByConnectionId.get(connectionId);
  }

  public void setLoadedPlugins(String connectionId, LoadedPlugins loadedPlugins) {
    loadedPluginsByConnectionId.put(connectionId, loadedPlugins);
  }

  public List<LoadedPlugins> getAllLoadedPlugins() {
    var loadedPlugins = new ArrayList<LoadedPlugins>();
    if (loadedEmbeddedPlugins != null) {
      loadedPlugins.add(loadedEmbeddedPlugins);
    }
    loadedPlugins.addAll(loadedPluginsByConnectionId.values());
    return loadedPlugins;
  }
}
