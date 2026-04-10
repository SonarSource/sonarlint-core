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

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.analysis.NodeJsService;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginRequirementsCheckResult;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ArtifactsLoadingResult;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ArtifactsLoadingStrategy;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ConnectedArtifactsLoadingStrategyFactory;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.StandaloneArtifactsLoadingStrategy;
import org.sonarsource.sonarlint.core.plugin.skipped.SkippedPlugin;
import org.sonarsource.sonarlint.core.plugin.skipped.SkippedPluginsRepository;
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.serverconnection.PluginsSynchronizer;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.PluginsSynchronizedEvent;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.springframework.context.ApplicationEventPublisher;

import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.DATAFLOW_BUG_DETECTION;

public class PluginsService {
  private static final Version REPACKAGED_DOTNET_ANALYZER_MIN_SQ_VERSION = Version.create("10.8");

  private final SonarLintLogger logger = SonarLintLogger.get();
  private final PluginsRepository pluginsRepository;
  private final SkippedPluginsRepository skippedPluginsRepository;
  private final StorageService storageService;
  private final InitializeParams initializeParams;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final NodeJsService nodeJsService;
  private final boolean enableDataflowBugDetection;
  private final ApplicationEventPublisher eventPublisher;
  private final StandaloneArtifactsLoadingStrategy standaloneArtifactsLoadingStrategy;
  private final ConnectedArtifactsLoadingStrategyFactory connectedArtifactsLoadingStrategyFactory;

  public PluginsService(PluginsRepository pluginsRepository, SkippedPluginsRepository skippedPluginsRepository,
    StorageService storageService, InitializeParams params, ConnectionConfigurationRepository connectionConfigurationRepository,
    NodeJsService nodeJsService, ApplicationEventPublisher eventPublisher,
    StandaloneArtifactsLoadingStrategy standaloneArtifactsLoadingStrategy,
    ConnectedArtifactsLoadingStrategyFactory connectedArtifactsLoadingStrategyFactory) {
    this.pluginsRepository = pluginsRepository;
    this.skippedPluginsRepository = skippedPluginsRepository;
    this.storageService = storageService;
    this.enableDataflowBugDetection = params.getBackendCapabilities().contains(DATAFLOW_BUG_DETECTION);
    this.initializeParams = params;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.nodeJsService = nodeJsService;
    this.eventPublisher = eventPublisher;
    this.standaloneArtifactsLoadingStrategy = standaloneArtifactsLoadingStrategy;
    this.connectedArtifactsLoadingStrategyFactory = connectedArtifactsLoadingStrategyFactory;
  }

  public List<PluginStatus> getPluginStatuses(@Nullable String connectionId) {
    var result = getPluginLoadingStrategy(connectionId).resolveArtifacts();
    return Arrays.stream(SonarLanguage.values())
      .map(language -> buildPluginStatus(language, result))
      .toList();
  }

  private static PluginStatus buildPluginStatus(SonarLanguage language, ArtifactsLoadingResult result) {
    var pluginKey = resolvePluginKey(language, result.resolvedArtifactsByKey());
    var artifact = result.resolvedArtifactsByKey().get(pluginKey);
    if (artifact != null) {
      return PluginStatus.forLanguage(language, artifact.state(), artifact.source(), artifact.version(), null, artifact.path(), null);
    }
    return PluginStatus.unsupported(language);
  }

  private ArtifactsLoadingStrategy getPluginLoadingStrategy(@Nullable String connectionId) {
    return connectionId != null ? connectedArtifactsLoadingStrategyFactory.getOrCreate(connectionId) : standaloneArtifactsLoadingStrategy;
  }

  /**
   * Returns the effective plugin key for a language, preferring the enterprise variant if it is
   * already present in the resolved map.
   */
  private static String resolvePluginKey(SonarLanguage language, Map<String, ResolvedArtifact> resolved) {
    var baseKey = language.getPlugin().getKey();
    var enterpriseKey = SonarPlugin.findByKey(baseKey)
      .flatMap(SonarPlugin::getEnterpriseVariant)
      .map(SonarPlugin::getKey)
      .orElse(null);
    if (enterpriseKey != null && resolved.containsKey(enterpriseKey)) {
      return enterpriseKey;
    }
    return baseKey;
  }

  @NotNull
  private static List<SkippedPlugin> getSkippedPlugins(PluginsLoadResult result) {
    return result.getPluginCheckResultByKeys().values().stream()
      .filter(PluginRequirementsCheckResult::isSkipped)
      .map(plugin -> new SkippedPlugin(plugin.getPlugin().getKey(), plugin.getSkipReason().get()))
      .toList();
  }

  public LoadedPlugins getEmbeddedPlugins() {
    var loadedEmbeddedPlugins = pluginsRepository.getLoadedEmbeddedPlugins();
    if (loadedEmbeddedPlugins == null) {
      var result = loadPlugins(null);
      loadedEmbeddedPlugins = result.getLoadedPlugins();
      pluginsRepository.setLoadedEmbeddedPlugins(loadedEmbeddedPlugins);
      skippedPluginsRepository.setSkippedEmbeddedPlugins(getSkippedPlugins(result));
      eventPublisher.publishEvent(new PluginStatusesChangedEvent(null, getPluginStatuses(null)));
    }
    return loadedEmbeddedPlugins;
  }

  public LoadedPlugins getPlugins(String connectionId) {
    var loadedPlugins = pluginsRepository.getLoadedPlugins(connectionId);
    if (loadedPlugins == null) {
      var result = loadPlugins(connectionId);
      loadedPlugins = result.getLoadedPlugins();
      pluginsRepository.setLoadedPlugins(connectionId, loadedPlugins);
      skippedPluginsRepository.setSkippedPlugins(connectionId, getSkippedPlugins(result));
      eventPublisher.publishEvent(new PluginStatusesChangedEvent(connectionId, getPluginStatuses(connectionId)));
    }
    return loadedPlugins;
  }

  private PluginsLoadResult loadPlugins(@Nullable String connectionId) {
    var strategy = getPluginLoadingStrategy(connectionId);
    var result = strategy.resolveArtifacts();
    result.whenAllArtifactsDownloaded(() -> eventPublisher.publishEvent(new PluginsSynchronizedEvent(connectionId)));

    var pluginsToLoadByKey = new HashMap<String, Path>();
    for (var entry : result.resolvedArtifactsByKey().entrySet()) {
      var key = entry.getKey();
      var artifact = entry.getValue();
      // only load artifacts that are ready on disk
      if (artifact != null && artifact.path() != null) {
        pluginsToLoadByKey.put(key, artifact.path());
      }
    }

    var config = new PluginsLoader.Configuration(new HashSet<>(pluginsToLoadByKey.values()), result.enabledLanguages(),
      enableDataflowBugDetection, nodeJsService.getActiveNodeJsVersion());
    return new PluginsLoader().load(config, initializeParams.getDisabledPluginKeysForAnalysis());
  }

  public void unloadPlugins(String connectionId) {
    logger.debug("Evict loaded plugins for connection '{}'", connectionId);
    boolean wasLoaded = pluginsRepository.getLoadedPlugins(connectionId) != null;
    pluginsRepository.unload(connectionId);
    connectedArtifactsLoadingStrategyFactory.evict(connectionId);
    if (wasLoaded) {
      eventPublisher.publishEvent(new PluginStatusesChangedEvent(connectionId, getPluginStatuses(connectionId)));
    }
  }

  public boolean shouldUseEnterpriseCSharpAnalyzer(String connectionId) {
    return shouldUseEnterpriseDotNetAnalyzer(connectionId, PluginsSynchronizer.CSHARP_ENTERPRISE_PLUGIN_ID);
  }

  private boolean shouldUseEnterpriseDotNetAnalyzer(String connectionId, String analyzerName) {
    if (isSonarQubeCloud(connectionId)) {
      return true;
    } else {
      var connectionStorage = storageService.connection(connectionId);
      var serverInfo = connectionStorage.serverInfo().read();
      if (serverInfo.isEmpty()) {
        return false;
      } else {
        var serverVersion = serverInfo.get().version();
        var supportsRepackagedDotnetAnalyzer = serverVersion.compareToIgnoreQualifier(REPACKAGED_DOTNET_ANALYZER_MIN_SQ_VERSION) >= 0;
        var hasEnterprisePlugin = connectionStorage.plugins().getStoredPlugins().stream().map(StoredPlugin::getKey).anyMatch(analyzerName::equals);
        return !supportsRepackagedDotnetAnalyzer || hasEnterprisePlugin;
      }
    }
  }

  private boolean isSonarQubeCloud(String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    return connection != null && connection.getKind() == ConnectionKind.SONARCLOUD;
  }

  public boolean shouldUseEnterpriseVbAnalyzer(String connectionId) {
    return shouldUseEnterpriseDotNetAnalyzer(connectionId, PluginsSynchronizer.VBNET_ENTERPRISE_PLUGIN_ID);
  }

  public DotnetSupport getDotnetSupport(@Nullable String connectionId) {
    var ossPath = resolveOssCsharpAnalyzerPath(connectionId);
    if (connectionId == null) {
      return new DotnetSupport(initializeParams, ossPath, false, false);
    }
    var useEnterpriseCs = shouldUseEnterpriseCSharpAnalyzer(connectionId);
    var useEnterpriseVb = shouldUseEnterpriseVbAnalyzer(connectionId);
    var actualPath = selectCsharpAnalyzerPath(connectionId, ossPath, useEnterpriseCs);
    return new DotnetSupport(initializeParams, actualPath, useEnterpriseCs, useEnterpriseVb);
  }

  @Nullable
  private Path resolveOssCsharpAnalyzerPath(@Nullable String connectionId) {
    var resolved = getPluginLoadingStrategy(connectionId).resolveArtifacts();
    var status = buildPluginStatus(SonarLanguage.CS, resolved);
    if ((status.state() == ArtifactState.ACTIVE || status.state() == ArtifactState.SYNCED) && status.path() != null) {
      return status.path();
    }
    return null;
  }

  @Nullable
  private Path selectCsharpAnalyzerPath(String connectionId, @Nullable Path ossPath, boolean useEnterprise) {
    if (useEnterprise) {
      return getStoredEnterprisePath(connectionId).orElse(ossPath);
    }
    return ossPath;
  }

  private Optional<Path> getStoredEnterprisePath(String connectionId) {
    return Optional.ofNullable(storageService.connection(connectionId).plugins().getStoredPluginsByKey().get(PluginsSynchronizer.CSHARP_ENTERPRISE_PLUGIN_ID))
      .map(StoredPlugin::getJarPath);
  }

  public void unloadEmbeddedPlugins() {
    logger.debug("Evict loaded embedded plugins");
    pluginsRepository.unloadEmbedded();
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
