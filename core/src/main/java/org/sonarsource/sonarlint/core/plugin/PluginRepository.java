/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.picocontainer.Startable;
import org.sonar.api.Plugin;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.connected.LoadedAnalyzer;
import org.sonarsource.sonarlint.core.container.model.DefaultLoadedAnalyzer;

import static java.util.Objects.requireNonNull;

/**
 * Orchestrates the installation and loading of plugins
 */
public class PluginRepository implements Startable {
  private static final Logger LOG = Loggers.get(PluginRepository.class);

  private final PluginCacheLoader cacheLoader;
  private final PluginLoader loader;

  private Map<String, Plugin> pluginInstancesByKeys;
  private Map<String, PluginInfo> infosByKeys;

  public PluginRepository(PluginCacheLoader cacheLoader, PluginLoader loader) {
    this.cacheLoader = cacheLoader;
    this.loader = loader;
  }

  @Override
  public void start() {
    infosByKeys = new HashMap<>(cacheLoader.load());
    pluginInstancesByKeys = new HashMap<>(loader.load(infosByKeys));

    logPlugins();
  }

  private void logPlugins() {
    if (infosByKeys.isEmpty()) {
      LOG.debug("No plugins loaded");
    } else {
      LOG.debug("Plugins:");
      for (PluginInfo p : infosByKeys.values()) {
        LOG.debug("  * {} {} ({})", p.getName(), p.getVersion(), p.getKey());
      }
    }
  }

  @Override
  public void stop() {
    // close plugin classloaders
    loader.unload(pluginInstancesByKeys.values());

    pluginInstancesByKeys.clear();
    infosByKeys.clear();
  }

  public Collection<LoadedAnalyzer> getLoadedAnalyzers() {
    return infosByKeys.values().stream()
      .map(PluginRepository::pluginInfoToLoadedAnalyzer)
      .collect(Collectors.toList());
  }

  private static LoadedAnalyzer pluginInfoToLoadedAnalyzer(PluginInfo p) {
    String version = p.getVersion() != null ? p.getVersion().toString() : null;
    return new DefaultLoadedAnalyzer(p.getKey(), p.getName(), version);
  }

  public Collection<PluginInfo> getPluginInfos() {
    return infosByKeys.values();
  }

  public PluginInfo getPluginInfo(String key) {
    PluginInfo info = infosByKeys.get(key);
    requireNonNull(info, () -> "Plugin [" + key + "] does not exist");
    return info;
  }

  public Plugin getPluginInstance(String key) {
    Plugin instance = pluginInstancesByKeys.get(key);
    requireNonNull(instance, () -> "Plugin [" + key + "] does not exist");
    return instance;
  }

  public boolean hasPlugin(String key) {
    return infosByKeys.containsKey(key);
  }
}
