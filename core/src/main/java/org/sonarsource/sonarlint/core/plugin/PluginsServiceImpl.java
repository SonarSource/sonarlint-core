/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
import java.util.Set;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginsService;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.event.EventListener;

@Named
@Singleton
public class PluginsServiceImpl implements PluginsService {

  private final SonarLintLogger logger = SonarLintLogger.get();
  private final PluginsRepository pluginsRepository;
  private final LanguageSupportRepository languageSupportRepository;
  private final StorageService storageService;
  private final Set<Path> embeddedPluginPaths;
  private final Map<String, Path> connectedModeEmbeddedPluginPathsByKey;
  private final boolean enableDataflowBugDetection;

  public PluginsServiceImpl(PluginsRepository pluginsRepository, LanguageSupportRepository languageSupportRepository, StorageService storageService, InitializeParams params) {
    this.pluginsRepository = pluginsRepository;
    this.languageSupportRepository = languageSupportRepository;
    this.storageService = storageService;
    this.embeddedPluginPaths = params.getEmbeddedPluginPaths();
    this.connectedModeEmbeddedPluginPathsByKey = params.getConnectedModeEmbeddedPluginPathsByKey();
    this.enableDataflowBugDetection = params.getFeatureFlags().isEnableDataflowBugDetection();
  }

  public LoadedPlugins getEmbeddedPlugins() {
    var loadedEmbeddedPlugins = pluginsRepository.getLoadedEmbeddedPlugins();
    if (loadedEmbeddedPlugins == null) {
      var result = loadPlugins(languageSupportRepository.getEnabledLanguagesInStandaloneMode(), embeddedPluginPaths, enableDataflowBugDetection);
      loadedEmbeddedPlugins = result.getLoadedPlugins();
      pluginsRepository.setLoadedEmbeddedPlugins(loadedEmbeddedPlugins);
    }
    return loadedEmbeddedPlugins;
  }

  public LoadedPlugins getPlugins(String connectionId) {
    var loadedPlugins = pluginsRepository.getLoadedPlugins(connectionId);
    if (loadedPlugins == null) {
      var result = loadPlugins(connectionId);
      loadedPlugins = result.getLoadedPlugins();
      pluginsRepository.setLoadedPlugins(connectionId, loadedPlugins);
    }
    return loadedPlugins;
  }

  private PluginsLoadResult loadPlugins(String connectionId) {
    // for now assume the sync already happened and the plugins are stored
    var pluginsStorage = storageService.connection(connectionId).plugins();

    Map<String, Path> pluginsToLoadByKey = new HashMap<>();
    // order is important as e.g. embedded takes precedence over stored
    pluginsToLoadByKey.putAll(pluginsStorage.getStoredPluginPathsByKey());
    pluginsToLoadByKey.putAll(connectedModeEmbeddedPluginPathsByKey);
    Set<Path> pluginPaths = new HashSet<>(pluginsToLoadByKey.values());

    return loadPlugins(languageSupportRepository.getEnabledLanguagesInConnectedMode(), pluginPaths, enableDataflowBugDetection);
  }

  private static PluginsLoadResult loadPlugins(Set<Language> enabledLanguages, Set<Path> pluginPaths, boolean enableDataflowBugDetection) {
    // not interested in the Node.js path at the moment
    var config = new PluginsLoader.Configuration(pluginPaths, enabledLanguages, enableDataflowBugDetection);
    return new PluginsLoader().load(config);
  }

  @EventListener
  public void connectionRemoved(ConnectionConfigurationRemovedEvent e) {
    evictAll(e.getRemovedConnectionId());
  }

  private void evictAll(String connectionId) {
    logger.debug("Evict loaded plugins for connection '{}'", connectionId);
    pluginsRepository.unload(connectionId);
  }

}
