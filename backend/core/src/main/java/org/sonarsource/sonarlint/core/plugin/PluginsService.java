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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.NodeJsService;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginRequirementsCheckResult;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ArtifactsLoadingResult;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ArtifactsLoadingStrategy;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ConnectedArtifactsLoadingStrategyFactory;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.StandaloneArtifactsLoadingStrategy;
import org.sonarsource.sonarlint.core.plugin.skipped.SkippedPlugin;
import org.sonarsource.sonarlint.core.plugin.skipped.SkippedPluginsRepository;
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifactSource;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.PluginsSynchronizedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;

import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.DATAFLOW_BUG_DETECTION;

public class PluginsService {
  public static final String CSHARP_ENTERPRISE_PLUGIN_ID = "csharpenterprise";
  public static final String VBNET_ENTERPRISE_PLUGIN_ID = "vbnetenterprise";

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
  private final BinariesArtifactSource binariesArtifactSource;
  private final LanguageSupportRepository languageSupportRepository;
  private final ExecutorService pluginDownloadExecutor;

  public PluginsService(PluginsRepository pluginsRepository, SkippedPluginsRepository skippedPluginsRepository,
    StorageService storageService, InitializeParams params, ConnectionConfigurationRepository connectionConfigurationRepository,
    NodeJsService nodeJsService, ApplicationEventPublisher eventPublisher,
    StandaloneArtifactsLoadingStrategy standaloneArtifactsLoadingStrategy,
    ConnectedArtifactsLoadingStrategyFactory connectedArtifactsLoadingStrategyFactory,
    BinariesArtifactSource binariesArtifactSource, LanguageSupportRepository languageSupportRepository,
    @Qualifier("pluginDownloadExecutor") ExecutorService pluginDownloadExecutor) {
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
    this.binariesArtifactSource = binariesArtifactSource;
    this.languageSupportRepository = languageSupportRepository;
    this.pluginDownloadExecutor = pluginDownloadExecutor;
  }

  public List<PluginStatus> getPluginStatuses(@Nullable String connectionId) {
    var plugins = connectionId == null ? getEmbeddedPlugins() : getPlugins(connectionId);
    return getPluginStatuses(connectionId, plugins.artifactsResult());
  }

  private List<PluginStatus> getPluginStatuses(@Nullable String connectionId, ArtifactsLoadingResult result) {
    return Arrays.stream(SonarLanguage.values())
      .map(language -> buildPluginStatus(connectionId, language, result))
      .toList();
  }

  private PluginStatus buildPluginStatus(@Nullable String connectionId, SonarLanguage language, ArtifactsLoadingResult result) {
    var enabled = getEnabledLanguages(connectionId).contains(language);
    var connectedOnly = !enabled && connectionId == null && languageSupportRepository.isEnabledOnlyInConnectedMode(language);
    if (!enabled && !connectedOnly) {
      return PluginStatus.unsupported(language);
    }
    var pluginKey = resolvePluginKey(language, result.resolvedArtifactsByKey());
    return result.getResolvedArtifactByKey(pluginKey)
      .map(artifact -> toPluginStatus(language, artifact))
      .orElseGet(() -> statusWithoutArtifact(language, connectedOnly));
  }

  private static PluginStatus toPluginStatus(SonarLanguage language, ResolvedArtifact artifact) {
    return PluginStatus.forLanguage(language, artifact.state(), artifact.source(), artifact.version(), null, artifact.path(), null);
  }

  private static PluginStatus statusWithoutArtifact(SonarLanguage language, boolean connectedOnly) {
    return connectedOnly ? PluginStatus.premium(language) : PluginStatus.unsupported(language);
  }

  private Set<SonarLanguage> getEnabledLanguages(@Nullable String connectionId) {
    return connectionId == null ? languageSupportRepository.getEnabledLanguagesInStandaloneMode()
      : languageSupportRepository.getEnabledLanguagesInConnectedMode();
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
    var enterpriseKeys = SonarPlugin.findByKey(baseKey)
      .map(SonarPlugin::getEnterpriseVariants)
      .map(variants -> variants.stream().map(SonarPlugin::getKey).collect(Collectors.toSet()))
      .orElseGet(Set::of);
    return enterpriseKeys.stream()
      .filter(resolved::containsKey)
      .findFirst()
      .orElse(baseKey);
  }

  public PluginsConfiguration getEmbeddedPlugins() {
    var cached = pluginsRepository.getEmbeddedPlugins();
    if (cached == null) {
      cached = loadPlugins(null);
      pluginsRepository.setEmbeddedPlugins(cached);
      return publishStatusesAndStart(null, cached);
    }
    return cached;
  }

  public PluginsConfiguration getPlugins(String connectionId) {
    var cached = pluginsRepository.getPlugins(connectionId);
    if (cached == null) {
      cached = loadPlugins(connectionId);
      pluginsRepository.setPlugins(connectionId, cached);
      return publishStatusesAndStart(connectionId, cached);
    }
    return cached;
  }

  private PluginsConfiguration publishStatusesAndStart(@Nullable String connectionId, PluginsConfiguration config) {
    try {
      eventPublisher.publishEvent(new PluginStatusesChangedEvent(connectionId, getPluginStatuses(connectionId, config.artifactsResult())));
    } finally {
      config.downloads().start(() -> publishPluginsSynchronized(connectionId));
    }
    return config;
  }

  private PluginsConfiguration loadPlugins(@Nullable String connectionId) {
    var strategy = getPluginLoadingStrategy(connectionId);
    var plan = strategy.resolveArtifacts();
    var artifactsResult = plan.artifacts();
    var downloads = new DownloadBatch(plan.pendingDownloads(), pluginDownloadExecutor);

    var config = new PluginsLoader.Configuration(new HashSet<>(artifactsResult.getPluginPaths()), getEnabledLanguages(connectionId),
      enableDataflowBugDetection, nodeJsService.getActiveNodeJsVersion());
    var pluginsLoadResult = new PluginsLoader().load(config, initializeParams.getDisabledPluginKeysForAnalysis());

    var skippedPlugins = pluginsLoadResult.getPluginCheckResultByKeys().values().stream()
      .filter(PluginRequirementsCheckResult::isSkipped)
      .map(plugin -> new SkippedPlugin(plugin.getPlugin().getKey(), plugin.getSkipReason().get()))
      .toList();
    if (connectionId == null) {
      skippedPluginsRepository.setSkippedEmbeddedPlugins(skippedPlugins);
    } else {
      skippedPluginsRepository.setSkippedPlugins(connectionId, skippedPlugins);
    }

    return new PluginsConfiguration(artifactsResult, pluginsLoadResult.getLoadedPlugins(), buildExtraProperties(connectionId, artifactsResult), downloads);
  }

  private void publishPluginsSynchronized(@Nullable String connectionId) {
    eventPublisher.publishEvent(new PluginsSynchronizedEvent(connectionId));
  }

  private Map<String, String> buildExtraProperties(@Nullable String connectionId, ArtifactsLoadingResult result) {
    var properties = new HashMap<String, String>();
    var dotnetSupport = getDotnetSupport(connectionId, result);
    if (dotnetSupport.getActualCsharpAnalyzerPath() != null) {
      properties.put("sonar.cs.internal.analyzerPath", dotnetSupport.getActualCsharpAnalyzerPath().toString());
    }
    if (dotnetSupport.isSupportsCsharp()) {
      properties.put("sonar.cs.internal.shouldUseCsharpEnterprise", String.valueOf(dotnetSupport.isShouldUseCsharpEnterprise()));
    }
    if (dotnetSupport.isSupportsVbNet()) {
      properties.put("sonar.cs.internal.shouldUseVbEnterprise", String.valueOf(dotnetSupport.isShouldUseVbNetEnterprise()));
    }
    properties.putAll(binariesArtifactSource.getOmnisharpExtraProperties());
    return properties;
  }

  public void unloadPlugins(String connectionId) {
    logger.debug("Evict loaded plugins for connection '{}'", connectionId);
    pluginsRepository.unload(connectionId);
    connectedArtifactsLoadingStrategyFactory.evict(connectionId);
  }

  public boolean shouldUseEnterpriseCSharpAnalyzer(String connectionId) {
    return shouldUseEnterpriseDotNetAnalyzer(connectionId, CSHARP_ENTERPRISE_PLUGIN_ID);
  }

  private boolean shouldUseEnterpriseDotNetAnalyzer(String connectionId, String analyzerName) {
    if (isSonarQubeCloud(connectionId)) {
      return true;
    } else {
      var connectionStorage = storageService.connection(connectionId);
      var serverInfo = connectionStorage.serverInfo().read();
      if (serverInfo.isEmpty()) {
        return false;
      }
      return connectionStorage.plugins().getStoredPlugins().stream().map(StoredPlugin::getKey).anyMatch(analyzerName::equals);
    }
  }

  private boolean isSonarQubeCloud(String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    return connection != null && connection.getKind() == ConnectionKind.SONARCLOUD;
  }

  public boolean shouldUseEnterpriseVbAnalyzer(String connectionId) {
    return shouldUseEnterpriseDotNetAnalyzer(connectionId, VBNET_ENTERPRISE_PLUGIN_ID);
  }

  private DotnetSupport getDotnetSupport(@Nullable String connectionId, ArtifactsLoadingResult result) {
    var ossPath = resolveOssCsharpAnalyzerPath(result);
    if (connectionId == null) {
      return new DotnetSupport(initializeParams, ossPath, false, false);
    }
    var useEnterpriseCs = shouldUseEnterpriseCSharpAnalyzer(connectionId);
    var useEnterpriseVb = shouldUseEnterpriseVbAnalyzer(connectionId);
    var actualPath = selectCsharpAnalyzerPath(connectionId, ossPath, useEnterpriseCs);
    return new DotnetSupport(initializeParams, actualPath, useEnterpriseCs, useEnterpriseVb);
  }

  @Nullable
  private static Path resolveOssCsharpAnalyzerPath(ArtifactsLoadingResult result) {
    return result.getResolvedArtifactByKey(SonarPlugin.CS_OSS.getKey())
      .map(ResolvedArtifact::path)
      .orElse(null);
  }

  @Nullable
  private Path selectCsharpAnalyzerPath(String connectionId, @Nullable Path ossPath, boolean useEnterprise) {
    if (useEnterprise) {
      return getStoredEnterprisePath(connectionId).orElse(ossPath);
    }
    return ossPath;
  }

  private Optional<Path> getStoredEnterprisePath(String connectionId) {
    return Optional.ofNullable(storageService.connection(connectionId).plugins().getStoredPluginsByKey().get(CSHARP_ENTERPRISE_PLUGIN_ID))
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
