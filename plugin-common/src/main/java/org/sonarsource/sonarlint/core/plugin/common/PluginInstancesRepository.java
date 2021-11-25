/*
 * SonarLint Core - Plugin Common
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
package org.sonarsource.sonarlint.core.plugin.common;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.picocontainer.Startable;
import org.sonar.api.Plugin;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.plugin.common.load.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.common.load.PluginInfosLoader;
import org.sonarsource.sonarlint.core.plugin.common.load.PluginInstancesLoader;

import static java.util.Objects.requireNonNull;

/**
 * Orchestrates the installation and loading of plugins
 */
public class PluginInstancesRepository implements Startable {
  private static final Logger LOG = Loggers.get(PluginInstancesRepository.class);

  private final PluginInfosLoader pluginInfosLoader;
  private final PluginInstancesLoader pluginInstancesLoader;
  private final Configuration configuration;
  private final System2 system2;

  private Map<String, Plugin> pluginInstancesByKeys;
  private Map<String, PluginInfo> infosByKeys;

  public static class Configuration {
    private final Set<Path> pluginJarLocations;
    private final Set<Language> enabledLanguages;
    private final Optional<Version> nodeCurrentVersion;

    public Configuration(Set<Path> pluginJarLocations, Set<Language> enabledLanguages, Optional<Version> nodeCurrentVersion) {
      this.pluginJarLocations = pluginJarLocations;
      this.enabledLanguages = enabledLanguages;
      this.nodeCurrentVersion = nodeCurrentVersion;
    }
  }

  public PluginInstancesRepository(Configuration configuration, PluginInfosLoader pluginInfosLoader, PluginInstancesLoader pluginInstancesLoader, System2 system2) {
    this.configuration = configuration;
    this.pluginInfosLoader = pluginInfosLoader;
    this.pluginInstancesLoader = pluginInstancesLoader;
    this.system2 = system2;
  }

  @Override
  public void start() {
    String javaSpecVersion = Objects.requireNonNull(system2.property("java.specification.version"), "Missing Java property 'java.specification.version'");
    infosByKeys = pluginInfosLoader.loadPlugins(configuration.pluginJarLocations, configuration.enabledLanguages, Version.create(javaSpecVersion),
      configuration.nodeCurrentVersion);
    Map<String, PluginInfo> nonSkippedPlugins = infosByKeys.entrySet().stream().filter(e -> !e.getValue().isSkipped())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    pluginInstancesByKeys = pluginInstancesLoader.load(nonSkippedPlugins.values());

    logPlugins(nonSkippedPlugins);
  }

  private static void logPlugins(Map<String, PluginInfo> nonSkippedPlugins) {
    LOG.debug("Loaded {} plugins", nonSkippedPlugins.size());
    for (PluginInfo p : nonSkippedPlugins.values()) {
      LOG.debug("  * {} {} ({})", p.getManifest().getName(), p.getManifest().getVersion(), p.getManifest().getKey());
    }
  }

  @Override
  public void stop() {
    LOG.debug("Unloading plugins");
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
