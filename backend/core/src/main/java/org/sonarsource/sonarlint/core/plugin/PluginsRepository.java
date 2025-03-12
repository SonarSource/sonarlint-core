/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.plugin;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;

import static org.sonarsource.sonarlint.core.commons.IOExceptionUtils.throwFirstWithOtherSuppressed;
import static org.sonarsource.sonarlint.core.commons.IOExceptionUtils.tryAndCollectIOException;

public class PluginsRepository {
  private final AtomicReference<LoadedPlugins> loadedEmbeddedPlugins = new AtomicReference<>();
  private final Map<String, LoadedPlugins> loadedPluginsByConnectionId = new HashMap<>();

  public void setLoadedEmbeddedPlugins(LoadedPlugins loadedEmbeddedPlugins) {
    this.loadedEmbeddedPlugins.set(loadedEmbeddedPlugins);
  }

  @CheckForNull
  public LoadedPlugins getLoadedEmbeddedPlugins() {
    return loadedEmbeddedPlugins.get();
  }

  @CheckForNull
  public LoadedPlugins getLoadedPlugins(String connectionId) {
    return loadedPluginsByConnectionId.get(connectionId);
  }

  public void setLoadedPlugins(String connectionId, LoadedPlugins loadedPlugins) {
    loadedPluginsByConnectionId.put(connectionId, loadedPlugins);
  }

  void unloadAllPlugins() throws IOException {
    Queue<IOException> exceptions = new LinkedList<>();
    var embeddedPlugins = loadedEmbeddedPlugins.get();
    if (embeddedPlugins != null) {
      tryAndCollectIOException(embeddedPlugins::close, exceptions);
      loadedEmbeddedPlugins.set(null);
    }
    synchronized (loadedPluginsByConnectionId) {
      loadedPluginsByConnectionId.values().forEach(l -> tryAndCollectIOException(l::close, exceptions));
      loadedPluginsByConnectionId.clear();
    }
    throwFirstWithOtherSuppressed(exceptions);
  }

  public void unload(String connectionId) {
    var loadedPlugins = loadedPluginsByConnectionId.remove(connectionId);
    if (loadedPlugins != null) {
      try {
        loadedPlugins.close();
      } catch (IOException e) {
        throw new IllegalStateException("Unable to unload plugins", e);
      }
    }
  }
}
