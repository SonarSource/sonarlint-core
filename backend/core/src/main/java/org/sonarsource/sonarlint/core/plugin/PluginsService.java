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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.analysis.NodeJsService;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.PluginStatusUpdateEvent;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginRequirementsCheckResult;
import org.sonarsource.sonarlint.core.plugin.resolvers.ArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.CompanionPluginResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.ConnectedModeArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.EmbeddedArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.OnDemandArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.PremiumArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.UnsupportedArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.skipped.SkippedPlugin;
import org.sonarsource.sonarlint.core.plugin.skipped.SkippedPluginsRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.serverconnection.PluginsSynchronizer;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.PluginsSynchronizedEvent;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.DATAFLOW_BUG_DETECTION;

public class PluginsService {
  private static final Version REPACKAGED_DOTNET_ANALYZER_MIN_SQ_VERSION = Version.create("10.8");

  private final SonarLintLogger logger = SonarLintLogger.get();
  private final PluginsRepository pluginsRepository;
  private final SkippedPluginsRepository skippedPluginsRepository;
  private final StorageService storageService;
  private final Set<String> disabledPluginKeysForAnalysis;
  private final InitializeParams initializeParams;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final NodeJsService nodeJsService;
  private final boolean enableDataflowBugDetection;
  private final ApplicationEventPublisher eventPublisher;
  private final List<ArtifactResolver> artifactResolvers;
  private final List<CompanionPluginResolver> companionPluginResolvers;

  public PluginsService(PluginsRepository pluginsRepository, SkippedPluginsRepository skippedPluginsRepository,
    StorageService storageService, InitializeParams params, ConnectionConfigurationRepository connectionConfigurationRepository, NodeJsService nodeJsService,
    ApplicationEventPublisher eventPublisher, List<CompanionPluginResolver> companionPluginResolvers,
    UnsupportedArtifactResolver unsupportedArtifactResolver, ConnectedModeArtifactResolver connectedModeArtifactResolver,
    EmbeddedArtifactResolver embeddedArtifactResolver, OnDemandArtifactResolver onDemandArtifactResolver,
    PremiumArtifactResolver premiumArtifactResolver) {
    this.pluginsRepository = pluginsRepository;
    this.skippedPluginsRepository = skippedPluginsRepository;
    this.storageService = storageService;
    this.enableDataflowBugDetection = params.getBackendCapabilities().contains(DATAFLOW_BUG_DETECTION);
    this.initializeParams = params;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.nodeJsService = nodeJsService;
    this.disabledPluginKeysForAnalysis = params.getDisabledPluginKeysForAnalysis();
    this.eventPublisher = eventPublisher;
    this.artifactResolvers = List.of(
      unsupportedArtifactResolver,
      connectedModeArtifactResolver,
      embeddedArtifactResolver,
      onDemandArtifactResolver,
      premiumArtifactResolver
    );
    this.companionPluginResolvers = companionPluginResolvers;
  }

  public List<PluginStatus> getPluginStatuses(@Nullable String connectionId) {
    return Arrays.stream(SonarLanguage.values())
      .map(language -> getPluginStatus(connectionId, language))
      .toList();
  }

  private PluginStatus getPluginStatus(@Nullable String connectionId, SonarLanguage language) {
    for (var resolver : artifactResolvers) {
      if (resolver == null) continue;
      var resolved = resolver.resolve(language, connectionId);
      if (resolved.isPresent()) {
        var artifact = resolved.get();
        return PluginStatus.forLanguage(language, artifact.state(), artifact.source(), artifact.version(), null, artifact.path(), null);
      }
    }
    return PluginStatus.unsupported(language);
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
    var pluginsToLoadByKey = new HashMap<String, Path>();
    var enabledLanguages = EnumSet.noneOf(SonarLanguage.class);

    resolveLanguagePlugins(connectionId, pluginsToLoadByKey, enabledLanguages);
    resolveCompanionPlugins(connectionId, pluginsToLoadByKey);

    var config = new PluginsLoader.Configuration(new HashSet<>(pluginsToLoadByKey.values()), enabledLanguages, enableDataflowBugDetection
      , nodeJsService.getActiveNodeJsVersion());
    return new PluginsLoader().load(config, disabledPluginKeysForAnalysis);
  }

  private void resolveLanguagePlugins(@Nullable String connectionId, Map<String, Path> pluginsToLoadByKey, Set<SonarLanguage> enabledLanguages) {
    for (var language : SonarLanguage.values()) {
      var status = getPluginStatus(connectionId, language);
      if (status.state() == ArtifactState.ACTIVE || status.state() == ArtifactState.SYNCED) {
        enabledLanguages.add(language);
        if (status.path() != null) {
          pluginsToLoadByKey.put(status.pluginKey(), status.path());
        }
      }
    }
  }

  private void resolveCompanionPlugins(@Nullable String connectionId, Map<String, Path> pluginsToLoadByKey) {
    for (var companionResolver : companionPluginResolvers) {
      if (companionResolver == null) continue;
      for (var entry : companionResolver.resolveCompanionPlugins(connectionId).entrySet()) {
        var status = entry.getValue();
        if ((status.state() == ArtifactState.ACTIVE || status.state() == ArtifactState.SYNCED) && status.path() != null) {
          pluginsToLoadByKey.put(status.pluginKey(), status.path());
        }
      }
    }
  }

  public boolean areAnyPluginsDownloading(@Nullable String connectionId) {
    for (var language : SonarLanguage.values()) {
      var status = getPluginStatus(connectionId, language);
      if (status.state() == ArtifactState.DOWNLOADING) {
        return true;
      }
    }
    for (var companionResolver : companionPluginResolvers) {
      if (companionResolver == null) continue;
      for (var status : companionResolver.resolveCompanionPlugins(connectionId).values()) {
        if (status.state() == ArtifactState.DOWNLOADING) {
          return true;
        }
      }
    }
    return false;
  }

  @EventListener
  public void onPluginStatusUpdateEvent(PluginStatusUpdateEvent event) {
    if (!areAnyPluginsDownloading(event.connectionId())) {
      eventPublisher.publishEvent(new PluginsSynchronizedEvent(event.connectionId()));
    }
  }

  public void unloadPlugins(String connectionId) {
    logger.debug("Evict loaded plugins for connection '{}'", connectionId);
    boolean wasLoaded = pluginsRepository.getLoadedPlugins(connectionId) != null;
    pluginsRepository.unload(connectionId);
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
    var status = getPluginStatus(connectionId, SonarLanguage.CS);
    if ((status.state() == ArtifactState.ACTIVE || status.state() == ArtifactState.SYNCED) && status.path() != null) {
      return status.path();
    }
    return null;
  }

  @Nullable
  private Path selectCsharpAnalyzerPath(String connectionId, @Nullable Path ossPath, boolean useEnterprise) {
    if (useEnterprise) {
      return getStoredEnterprisePath(connectionId)
        .orElse(ossPath);
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
