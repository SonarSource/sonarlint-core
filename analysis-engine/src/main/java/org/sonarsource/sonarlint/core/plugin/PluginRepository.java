/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2021 SonarSource SA
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

import static java.util.Objects.requireNonNull;

/**
 * Orchestrates the installation and loading of plugins
 */
public class PluginRepository implements Startable {
  private static final Logger LOG = Loggers.get(PluginRepository.class);

  private final PluginInfosLoader pluginInfosLoader;
  private final PluginInstancesLoader pluginInstancesLoader;

  private Map<String, Plugin> pluginInstancesByKeys;
  private Map<String, PluginInfo> infosByKeys;

  public PluginRepository(PluginInfosLoader pluginInfosLoader, PluginInstancesLoader pluginInstancesLoader) {
    this.pluginInfosLoader = pluginInfosLoader;
    this.pluginInstancesLoader = pluginInstancesLoader;
  }

  @Override
  public void start() {
    infosByKeys = new HashMap<>(pluginInfosLoader.load());
    Map<String, PluginInfo> nonSkippedPlugins = infosByKeys.entrySet().stream().filter(e -> !e.getValue().isSkipped())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    pluginInstancesByKeys = new HashMap<>(pluginInstancesLoader.load(nonSkippedPlugins));

    logPlugins(nonSkippedPlugins);
  }

  private static void logPlugins(Map<String, PluginInfo> nonSkippedPlugins) {
    if (nonSkippedPlugins.isEmpty()) {
      LOG.debug("No plugins loaded");
    } else {
      LOG.debug("Plugins:");
      for (PluginInfo p : nonSkippedPlugins.values()) {
        LOG.debug("  * {} {} ({})", p.getName(), p.getVersion(), p.getKey());
      }
    }
  }

  @Override
  public void stop() {
    // close plugin classloaders
    pluginInstancesLoader.unload(pluginInstancesByKeys.values());

    pluginInstancesByKeys.clear();
    infosByKeys.clear();
  }

  public Collection<PluginInfo> getActivePluginInfos() {
    return infosByKeys.values().stream().filter(p -> !p.isSkipped()).collect(Collectors.toList());
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
