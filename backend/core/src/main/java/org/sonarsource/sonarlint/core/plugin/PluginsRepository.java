/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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

import static org.sonarsource.sonarlint.core.commons.IOExceptionUtils.throwFirstWithOtherSuppressed;
import static org.sonarsource.sonarlint.core.commons.IOExceptionUtils.tryAndCollectIOException;

public class PluginsRepository {
  private final AtomicReference<PluginsConfiguration> embeddedPlugins = new AtomicReference<>();
  private final Map<String, PluginsConfiguration> pluginsByConnectionId = new HashMap<>();

  public void setEmbeddedPlugins(PluginsConfiguration config) {
    this.embeddedPlugins.set(config);
  }

  @CheckForNull
  public PluginsConfiguration getEmbeddedPlugins() {
    return embeddedPlugins.get();
  }

  @CheckForNull
  public PluginsConfiguration getPlugins(String connectionId) {
    return pluginsByConnectionId.get(connectionId);
  }

  public void setPlugins(String connectionId, PluginsConfiguration config) {
    pluginsByConnectionId.put(connectionId, config);
  }

  void unloadAllPlugins() throws IOException {
    Queue<IOException> exceptions = new LinkedList<>();
    var embedded = embeddedPlugins.get();
    if (embedded != null) {
      tryAndCollectIOException(embedded.plugins()::close, exceptions);
      embeddedPlugins.set(null);
    }
    synchronized (pluginsByConnectionId) {
      pluginsByConnectionId.values().forEach(config -> tryAndCollectIOException(config.plugins()::close, exceptions));
      pluginsByConnectionId.clear();
    }
    throwFirstWithOtherSuppressed(exceptions);
  }

  public void unload(String connectionId) {
    var config = pluginsByConnectionId.remove(connectionId);
    if (config != null) {
      try {
        config.plugins().close();
      } catch (IOException e) {
        throw new IllegalStateException("Unable to unload plugins", e);
      }
    }
  }

  public void unloadEmbedded() {
    var config = embeddedPlugins.getAndSet(null);
    if (config != null) {
      try {
        config.plugins().close();
      } catch (IOException e) {
        throw new IllegalStateException("Unable to unload embedded plugins", e);
      }
    }
  }

}
