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

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.analysis.NodeJsService;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements;
import org.sonarsource.sonarlint.core.serverconnection.PluginsSynchronizer;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.event.EventListener;

import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.DATAFLOW_BUG_DETECTION;
import static org.sonarsource.sonarlint.core.serverconnection.PluginsSynchronizer.CUSTOM_SECRETS_MIN_SQ_VERSION;
import static org.sonarsource.sonarlint.core.serverconnection.PluginsSynchronizer.ENTERPRISE_GO_MIN_SQ_VERSION;
import static org.sonarsource.sonarlint.core.serverconnection.PluginsSynchronizer.ENTERPRISE_IAC_MIN_SQ_VERSION;

public class PluginsService {
  private static final Version REPACKAGED_DOTNET_ANALYZER_MIN_SQ_VERSION = Version.create("10.8");

  private final SonarLintLogger logger = SonarLintLogger.get();
  private final PluginsRepository pluginsRepository;
  private final SkippedPluginsRepository skippedPluginsRepository;
  private final LanguageSupportRepository languageSupportRepository;
  private final StorageService storageService;
  private final Set<Path> embeddedPluginPaths;
  private final CSharpSupport csharpSupport;
  private final Set<String> disabledPluginKeysForAnalysis;
  private final Map<String, Path> connectedModeEmbeddedPluginPathsByKey;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final NodeJsService nodeJsService;
  private final boolean enableDataflowBugDetection;

  public PluginsService(PluginsRepository pluginsRepository, SkippedPluginsRepository skippedPluginsRepository, LanguageSupportRepository languageSupportRepository,
    StorageService storageService, InitializeParams params, ConnectionConfigurationRepository connectionConfigurationRepository, NodeJsService nodeJsService) {
    this.pluginsRepository = pluginsRepository;
    this.skippedPluginsRepository = skippedPluginsRepository;
    this.languageSupportRepository = languageSupportRepository;
    this.storageService = storageService;
    this.embeddedPluginPaths = params.getEmbeddedPluginPaths();
    this.connectedModeEmbeddedPluginPathsByKey = params.getConnectedModeEmbeddedPluginPathsByKey();
    this.enableDataflowBugDetection = params.getBackendCapabilities().contains(DATAFLOW_BUG_DETECTION);
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.nodeJsService = nodeJsService;
    this.disabledPluginKeysForAnalysis = params.getDisabledPluginKeysForAnalysis();
    this.csharpSupport = new CSharpSupport(params.getLanguageSpecificRequirements());
  }

  public LoadedPlugins reloadPluginsFromStorage(String connectionId) {
    pluginsRepository.unload(connectionId);
    return getPlugins(connectionId);
  }

  static class CSharpSupport {
    final Path csharpOssPluginPath;
    final Path csharpEnterprisePluginPath;

    CSharpSupport(@Nullable LanguageSpecificRequirements languageSpecificRequirements) {
      if (languageSpecificRequirements == null) {
        csharpOssPluginPath = null;
        csharpEnterprisePluginPath = null;
      } else {
        var omnisharpRequirements = languageSpecificRequirements.getOmnisharpRequirements();
        if (omnisharpRequirements == null) {
          csharpOssPluginPath = null;
          csharpEnterprisePluginPath = null;
        } else {
          csharpOssPluginPath = omnisharpRequirements.getOssAnalyzerPath();
          csharpEnterprisePluginPath = omnisharpRequirements.getEnterpriseAnalyzerPath();
        }
      }
    }
  }

  public LoadedPlugins getEmbeddedPlugins() {
    var loadedEmbeddedPlugins = pluginsRepository.getLoadedEmbeddedPlugins();
    if (loadedEmbeddedPlugins == null) {
      var allEmbeddedPlugins = new HashSet<>(embeddedPluginPaths);
      if (csharpSupport.csharpOssPluginPath != null) {
        allEmbeddedPlugins.add(csharpSupport.csharpOssPluginPath);
      }
      var result = loadPlugins(languageSupportRepository.getEnabledLanguagesInStandaloneMode(), allEmbeddedPlugins, enableDataflowBugDetection);
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
      .toList();
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
    if (languageSupportRepository.getEnabledLanguagesInConnectedMode().contains(SonarLanguage.CS)) {
      if (shouldUseEnterpriseCSharpAnalyzer(connectionId) && csharpSupport.csharpEnterprisePluginPath != null) {
        pluginsToLoadByKey.put(PluginsSynchronizer.CSHARP_ENTERPRISE_PLUGIN_ID, csharpSupport.csharpEnterprisePluginPath);
      } else if (csharpSupport.csharpOssPluginPath != null) {
        pluginsToLoadByKey.put(SonarLanguage.CS.getPluginKey(), csharpSupport.csharpOssPluginPath);
      }
    }
    return Set.copyOf(pluginsToLoadByKey.values());
  }

  private Map<String, Path> getEmbeddedPluginPathsByKey(String connectionId) {
    var embeddedPlugins = new HashMap<>(connectedModeEmbeddedPluginPathsByKey);
    if (supportsCustomSecrets(connectionId)) {
      embeddedPlugins.remove(SonarLanguage.SECRETS.getPluginKey());
    }
    if (supportsIaCEnterprise(connectionId)) {
      // if iacenterprise is there on the server, download both, iac and iacenterprise
      embeddedPlugins.remove(SonarLanguage.AZURERESOURCEMANAGER.getPluginKey());
    }
    if (supportsGoEnterprise(connectionId)) {
      embeddedPlugins.remove(SonarLanguage.GO.getPluginKey());
    }
    return embeddedPlugins;
  }

  public boolean supportsIaCEnterprise(String connectionId) {
    return isSonarQubeCloudOrVersionHigherThan(ENTERPRISE_IAC_MIN_SQ_VERSION, connectionId);
  }

  public boolean supportsCustomSecrets(String connectionId) {
    return isSonarQubeCloudOrVersionHigherThan(CUSTOM_SECRETS_MIN_SQ_VERSION, connectionId);
  }

  public boolean supportsGoEnterprise(String connectionId) {
    return isSonarQubeCloudOrVersionHigherThan(ENTERPRISE_GO_MIN_SQ_VERSION, connectionId);
  }

  private boolean isSonarQubeCloudOrVersionHigherThan(Version version, String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    if (connection == null) {
      // Connection is gone
      return false;
    }
    // when storage is not present, assume that server version is lower than requested
    return connection.getKind() == ConnectionKind.SONARCLOUD || storageService.connection(connectionId).serverInfo().read()
      .map(serverInfo -> serverInfo.version().compareToIgnoreQualifier(version) >= 0)
      .orElse(false);
  }

  private PluginsLoadResult loadPlugins(Set<SonarLanguage> enabledLanguages, Set<Path> pluginPaths, boolean enableDataflowBugDetection) {
    var config = new PluginsLoader.Configuration(pluginPaths, enabledLanguages, enableDataflowBugDetection, nodeJsService.getActiveNodeJsVersion());
    return new PluginsLoader().load(config, disabledPluginKeysForAnalysis);
  }

  @EventListener
  public void connectionRemoved(ConnectionConfigurationRemovedEvent e) {
    evictAll(e.getRemovedConnectionId());
  }

  private void evictAll(String connectionId) {
    logger.debug("Evict loaded plugins for connection '{}'", connectionId);
    pluginsRepository.unload(connectionId);
  }

  public boolean shouldUseEnterpriseCSharpAnalyzer(String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    var isSonarCloud = connection != null && connection.getKind() == ConnectionKind.SONARCLOUD;
    if (isSonarCloud) {
      return true;
    } else {
      var connectionStorage = storageService.connection(connectionId);
      var serverInfo = connectionStorage.serverInfo().read();
      if (serverInfo.isEmpty()) {
        return false;
      } else {
        // For SQ versions older than 10.8, enterprise C# analyzer was packaged in all editions.
        // For newer versions, we need to check if enterprise plugin is present on the server
        var serverVersion = serverInfo.get().version();
        var supportsRepackagedDotnetAnalyzer = serverVersion.compareToIgnoreQualifier(REPACKAGED_DOTNET_ANALYZER_MIN_SQ_VERSION) >= 0;
        var hasEnterprisePlugin = connectionStorage.plugins().getStoredPlugins().stream().map(StoredPlugin::getKey).anyMatch("csharpenterprise"::equals);
        return !supportsRepackagedDotnetAnalyzer || hasEnterprisePlugin;
      }
    }
  }

  @CheckForNull
  public Path getEffectivePathToCsharpAnalyzer(String connectionId) {
    return shouldUseEnterpriseCSharpAnalyzer(connectionId) ? csharpSupport.csharpEnterprisePluginPath : csharpSupport.csharpOssPluginPath;
  }

  @PreDestroy
  public void shutdown() throws IOException {
    try {
      pluginsRepository.unloadAllPlugins();
    } catch (Exception e) {
      SonarLintLogger.get().error("Error shutting down plugins service", e);
    }
  }
}
