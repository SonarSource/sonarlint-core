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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginRequirementsCheckResult;
import org.sonarsource.sonarlint.core.plugin.skipped.SkippedPlugin;
import org.sonarsource.sonarlint.core.plugin.skipped.SkippedPluginsRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.event.EventListener;

import static org.sonarsource.sonarlint.core.serverconnection.PluginsSynchronizer.CUSTOM_SECRETS_MIN_SQ_VERSION;

@Named
@Singleton
public class PluginsService {
  private final SonarLintLogger logger = SonarLintLogger.get();
  private final PluginsRepository pluginsRepository;
  private final SkippedPluginsRepository skippedPluginsRepository;
  private final LanguageSupportRepository languageSupportRepository;
  private final StorageService storageService;
  private final Set<Path> embeddedPluginPaths;
  private final Map<String, Path> connectedModeEmbeddedPluginPathsByKey;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final SonarLintRpcClient client;
  private final boolean enableDataflowBugDetection;

  public PluginsService(PluginsRepository pluginsRepository, SkippedPluginsRepository skippedPluginsRepository, LanguageSupportRepository languageSupportRepository,
    StorageService storageService, InitializeParams params, ConnectionConfigurationRepository connectionConfigurationRepository, SonarLintRpcClient client) {
    this.pluginsRepository = pluginsRepository;
    this.skippedPluginsRepository = skippedPluginsRepository;
    this.languageSupportRepository = languageSupportRepository;
    this.storageService = storageService;
    this.embeddedPluginPaths = params.getEmbeddedPluginPaths();
    this.connectedModeEmbeddedPluginPathsByKey = params.getConnectedModeEmbeddedPluginPathsByKey();
    this.enableDataflowBugDetection = params.getFeatureFlags().isEnableDataflowBugDetection();
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.client = client;
  }

  public LoadedPlugins getEmbeddedPlugins() {
    var loadedEmbeddedPlugins = pluginsRepository.getLoadedEmbeddedPlugins();
    if (loadedEmbeddedPlugins == null) {
      var result = loadPlugins(languageSupportRepository.getEnabledLanguagesInStandaloneMode(), embeddedPluginPaths, enableDataflowBugDetection);
      loadedEmbeddedPlugins = result.getLoadedPlugins();
      pluginsRepository.setLoadedEmbeddedPlugins(loadedEmbeddedPlugins);
      skippedPluginsRepository.setSkippedEmbeddedPlugins(getSkippedPlugins(result));
    }
    return loadedEmbeddedPlugins;
  }

  @NotNull
  private static List<SkippedPlugin> getSkippedPlugins(PluginsLoadResult result) {
    return result.getPluginCheckResultByKeys().values().stream()
      .filter(PluginRequirementsCheckResult::isSkipped)
      .map(plugin -> new SkippedPlugin(plugin.getPlugin().getKey(), plugin.getSkipReason().get()))
      .collect(Collectors.toList());
  }

  public LoadedPlugins getPlugins(String connectionId) {
    var loadedPlugins = pluginsRepository.getLoadedPlugins(connectionId);
    if (loadedPlugins == null) {
      var result = loadPlugins(connectionId);
      loadedPlugins = result.getLoadedPlugins();
      pluginsRepository.setLoadedPlugins(connectionId, loadedPlugins);
      skippedPluginsRepository.setSkippedPlugins(connectionId, getSkippedPlugins(result));
    }
    return loadedPlugins;
  }

  private PluginsLoadResult loadPlugins(String connectionId) {
    var pluginPaths = getPluginPathsForConnection(connectionId);

    return loadPlugins(languageSupportRepository.getEnabledLanguagesInConnectedMode(), pluginPaths, enableDataflowBugDetection);
  }

  private Set<Path> getPluginPathsForConnection(String connectionId) {
    // for now assume the sync already happened and the plugins are stored
    var pluginsStorage = storageService.connection(connectionId).plugins();

    Map<String, Path> pluginsToLoadByKey = new HashMap<>();
    // order is important as e.g. embedded takes precedence over stored
    pluginsToLoadByKey.putAll(pluginsStorage.getStoredPluginPathsByKey());
    pluginsToLoadByKey.putAll(getEmbeddedPluginPathsByKey(connectionId));
    return Set.copyOf(pluginsToLoadByKey.values());
  }

  private Map<String, Path> getEmbeddedPluginPathsByKey(String connectionId) {
    if (supportsCustomSecrets(connectionId)) {
      var embeddedPluginsExceptSecrets = new HashMap<>(connectedModeEmbeddedPluginPathsByKey);
      embeddedPluginsExceptSecrets.remove(SonarLanguage.SECRETS.getPluginKey());
      return embeddedPluginsExceptSecrets;
    }
    return connectedModeEmbeddedPluginPathsByKey;
  }

  public boolean supportsCustomSecrets(String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    if (connection == null) {
      // Connection is gone
      return false;
    }
    // when storage is not present, assume that secrets are not supported by server
    return connection.getKind() != ConnectionKind.SONARCLOUD && storageService.connection(connectionId).serverInfo().read()
      .map(serverInfo -> serverInfo.getVersion().compareToIgnoreQualifier(CUSTOM_SECRETS_MIN_SQ_VERSION) >= 0)
      .orElse(false);
  }

  private static PluginsLoadResult loadPlugins(Set<SonarLanguage> enabledLanguages, Set<Path> pluginPaths, boolean enableDataflowBugDetection) {
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

  public List<Path> getEmbeddedPluginPaths() {
    return List.copyOf(embeddedPluginPaths);
  }

  public List<Path> getConnectedPluginPaths(String connectionId) {
    return List.copyOf(getPluginPathsForConnection(connectionId));
  }
}
