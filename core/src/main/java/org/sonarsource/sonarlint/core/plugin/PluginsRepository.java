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

import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
@Named
@Singleton
public class PluginsRepository {
  private LoadedPlugins loadedEmbeddedPlugins;
  private final Map<String, LoadedPlugins> loadedPluginsByConnectionId = new HashMap<>();

  public void setLoadedEmbeddedPlugins(LoadedPlugins loadedEmbeddedPlugins) {
    this.loadedEmbeddedPlugins = loadedEmbeddedPlugins;
  }

  @CheckForNull
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

  @PreDestroy
  public void unloadAllPlugins() {
    if (loadedEmbeddedPlugins != null) {
      loadedEmbeddedPlugins.unload();
      loadedEmbeddedPlugins = null;
    }
    loadedPluginsByConnectionId.values().forEach(LoadedPlugins::unload);
    loadedPluginsByConnectionId.clear();
  }
}
