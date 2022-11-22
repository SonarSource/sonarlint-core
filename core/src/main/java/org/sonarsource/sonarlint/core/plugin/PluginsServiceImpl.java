/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.clientapi.plugin.PluginsService;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

public class PluginsServiceImpl implements PluginsService {
  private final PluginsRepository pluginsRepository;
  private Path storageRoot;
  private Set<Path> embeddedPluginPaths;
  private Map<String, Path> connectedModeEmbeddedPluginPathsByKey;
  private Map<String, Path> connectedModeExtraPluginPathsByKey;
  private Set<Language> enabledLanguages;
  private Version nodeJsVersion;

  public PluginsServiceImpl(PluginsRepository pluginsRepository) {
    this.pluginsRepository = pluginsRepository;
  }

  public void initialize(Path storageRoot, Set<Path> embeddedPluginPaths, Map<String, Path> connectedModeEmbeddedPluginPathsByKey,
    Map<String, Path> connectedModeExtraPluginPathsByKey, Set<Language> enabledLanguages, @Nullable Version nodeJsVersion) {
    this.storageRoot = storageRoot;
    this.embeddedPluginPaths = embeddedPluginPaths;
    this.connectedModeEmbeddedPluginPathsByKey = connectedModeEmbeddedPluginPathsByKey;
    this.connectedModeExtraPluginPathsByKey = connectedModeExtraPluginPathsByKey;
    this.enabledLanguages = enabledLanguages;
    this.nodeJsVersion = nodeJsVersion;
  }

  public LoadedPlugins getEmbeddedPlugins() {
    var loadedEmbeddedPlugins = pluginsRepository.getLoadedEmbeddedPlugins();
    if (loadedEmbeddedPlugins == null) {
      var result = loadPlugins(embeddedPluginPaths);
      loadedEmbeddedPlugins = result.getLoadedPlugins();
      pluginsRepository.setLoadedEmbeddedPlugins(loadedEmbeddedPlugins);
      // TODO notify about skipped plugins ?
    }
    return loadedEmbeddedPlugins;
  }

  public LoadedPlugins getPlugins(String connectionId) {
    var loadedPlugins = pluginsRepository.getLoadedPlugins(connectionId);
    if (loadedPlugins == null) {
      var result = loadPlugins(connectionId);
      loadedPlugins = result.getLoadedPlugins();
      pluginsRepository.setLoadedPlugins(connectionId, loadedPlugins);
      // TODO notify about skipped plugins ?
    }
    return loadedPlugins;
  }

  private PluginsLoadResult loadPlugins(String connectionId) {
    // for now assume the sync already happened and the plugins are stored
    var pluginsStorage = new PluginsStorage(storageRoot.resolve(encodeForFs(connectionId)).resolve("plugins"));

    Map<String, Path> pluginsToLoadByKey = new HashMap<>();
    // order is important as e.g. embedded takes precedence over stored
    pluginsToLoadByKey.putAll(connectedModeExtraPluginPathsByKey);
    pluginsToLoadByKey.putAll(pluginsStorage.getStoredPluginPathsByKey());
    pluginsToLoadByKey.putAll(connectedModeEmbeddedPluginPathsByKey);
    Set<Path> pluginPaths = new HashSet<>(pluginsToLoadByKey.values());

    return loadPlugins(pluginPaths);
  }

  private PluginsLoadResult loadPlugins(Set<Path> pluginPaths) {
    var config = new PluginsLoader.Configuration(pluginPaths, enabledLanguages, Optional.ofNullable(nodeJsVersion));
    return new PluginsLoader().load(config);
  }

  public void shutdown() {
    pluginsRepository.getAllLoadedPlugins().forEach(LoadedPlugins::unload);
  }
}
