/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.analysis.NodeJsService;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginRequirementsCheckResult;
import org.sonarsource.sonarlint.core.plugin.resolvers.ConnectedModeArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.skipped.SkippedPlugin;
import org.sonarsource.sonarlint.core.plugin.skipped.SkippedPluginsRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.DATAFLOW_BUG_DETECTION;

public class PluginsService {
  private static final Version REPACKAGED_DOTNET_ANALYZER_MIN_SQ_VERSION = Version.create("10.8");

  private final SonarLintLogger logger = SonarLintLogger.get();
  private final PluginsRepository pluginsRepository;
  private final SkippedPluginsRepository skippedPluginsRepository;
  private final LanguageSupportRepository languageSupportRepository;
  private final StorageService storageService;
  private final PluginArtifactProvider pluginArtifactProvider;
  private final Set<String> disabledPluginKeysForAnalysis;
  private final InitializeParams initializeParams;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final NodeJsService nodeJsService;
  private final boolean enableDataflowBugDetection;
  private final ApplicationEventPublisher eventPublisher;

  public PluginsService(PluginsRepository pluginsRepository, SkippedPluginsRepository skippedPluginsRepository, LanguageSupportRepository languageSupportRepository,
    StorageService storageService, InitializeParams params, ConnectionConfigurationRepository connectionConfigurationRepository, NodeJsService nodeJsService,
    ApplicationEventPublisher eventPublisher, PluginArtifactProvider pluginArtifactProvider) {
    this.pluginsRepository = pluginsRepository;
    this.skippedPluginsRepository = skippedPluginsRepository;
    this.languageSupportRepository = languageSupportRepository;
    this.storageService = storageService;
    this.enableDataflowBugDetection = params.getBackendCapabilities().contains(DATAFLOW_BUG_DETECTION);
    this.initializeParams = params;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.nodeJsService = nodeJsService;
    this.disabledPluginKeysForAnalysis = params.getDisabledPluginKeysForAnalysis();
    this.eventPublisher = eventPublisher;
    this.pluginArtifactProvider = pluginArtifactProvider;
  }

  public boolean arePluginsReady(@Nullable String connectionId) {
    return pluginArtifactProvider.arePluginsReady(connectionId);
  }

  public List<PluginStatus> getPluginStatuses(@Nullable String connectionId) {
    return pluginArtifactProvider.resolve(connectionId).values().stream()
      .map(AnalyzerArtifacts::status)
      .toList();
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
      var enabledLanguages = languageSupportRepository.getEnabledLanguagesInStandaloneMode();
      var artifacts = pluginArtifactProvider.resolve(null);
      var allPluginPaths = artifacts.values().stream()
        .map(AnalyzerArtifacts::pluginJar)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(HashSet::new));
      allPluginPaths.addAll(pluginArtifactProvider.getAdditionalEmbeddedPluginPaths());

      var result = loadPlugins(enabledLanguages, allPluginPaths, enableDataflowBugDetection);
      loadedEmbeddedPlugins = result.getLoadedPlugins();
      pluginsRepository.setLoadedEmbeddedPlugins(loadedEmbeddedPlugins);
      skippedPluginsRepository.setSkippedEmbeddedPlugins(getSkippedPlugins(result));
      eventPublisher.publishEvent(new PluginStatusesChangedEvent(null));
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
      eventPublisher.publishEvent(new PluginStatusesChangedEvent(connectionId));
    }
    return loadedPlugins;
  }

  private PluginsLoadResult loadPlugins(String connectionId) {
    var artifacts = pluginArtifactProvider.resolve(connectionId);
    var pluginPaths = artifacts.values().stream()
      .map(AnalyzerArtifacts::pluginJar)
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(HashSet::new));
    pluginPaths.addAll(pluginArtifactProvider.getAdditionalEmbeddedPluginPaths());
    return loadPlugins(languageSupportRepository.getEnabledLanguagesInConnectedMode(), pluginPaths, enableDataflowBugDetection);
  }

  private PluginsLoadResult loadPlugins(Set<SonarLanguage> enabledLanguages, Set<Path> pluginPaths, boolean enableDataflowBugDetection) {
    var config = new PluginsLoader.Configuration(pluginPaths, enabledLanguages, enableDataflowBugDetection, nodeJsService.getActiveNodeJsVersion());
    return new PluginsLoader().load(config, disabledPluginKeysForAnalysis);
  }

  public void unloadEmbeddedPlugins() {
    logger.debug("Evict loaded embedded plugins");
    pluginsRepository.unloadEmbedded();
    pluginArtifactProvider.evict(null);
  }

  public void unloadPlugins(String connectionId) {
    logger.debug("Evict loaded plugins for connection '{}'", connectionId);
    pluginsRepository.unload(connectionId);
    pluginArtifactProvider.evict(connectionId);
  }

  public boolean shouldUseEnterpriseCSharpAnalyzer(String connectionId) {
    return shouldUseEnterpriseDotNetAnalyzer(connectionId, ConnectedModeArtifactResolver.CSHARP_ENTERPRISE_PLUGIN_KEY);
  }

  private boolean shouldUseEnterpriseDotNetAnalyzer(String connectionId, String analyzerName) {
    if (isSonarCloud(connectionId)) {
      return true;
    } else {
      var connectionStorage = storageService.connection(connectionId);
      var serverInfo = connectionStorage.serverInfo().read();
      if (serverInfo.isEmpty()) {
        return false;
      } else {
        // For SQ versions older than 10.8, enterprise C# and VB.NET analyzers were packaged in all editions.
        // For newer versions, we need to check if enterprise plugin is present on the server
        var serverVersion = serverInfo.get().version();
        var supportsRepackagedDotnetAnalyzer = serverVersion.compareToIgnoreQualifier(REPACKAGED_DOTNET_ANALYZER_MIN_SQ_VERSION) >= 0;
        var hasEnterprisePlugin = connectionStorage.plugins().getStoredPlugins().stream().map(StoredPlugin::getKey).anyMatch(analyzerName::equals);
        return !supportsRepackagedDotnetAnalyzer || hasEnterprisePlugin;
      }
    }
  }

  private boolean isSonarCloud(String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    return connection != null && connection.getKind() == ConnectionKind.SONARCLOUD;
  }

  public boolean shouldUseEnterpriseVbAnalyzer(String connectionId) {
    return shouldUseEnterpriseDotNetAnalyzer(connectionId, ConnectedModeArtifactResolver.VBNET_ENTERPRISE_PLUGIN_KEY);
  }

  public DotnetSupport getDotnetSupport(@Nullable String connectionId) {
    var csArtifacts = pluginArtifactProvider.resolve(connectionId).get(SonarLanguage.CS);
    if (connectionId == null) {
      return new DotnetSupport(initializeParams, csArtifacts.pluginJar(), false, false, csArtifacts.extra());
    }
    return new DotnetSupport(initializeParams, csArtifacts.pluginJar(),
      shouldUseEnterpriseCSharpAnalyzer(connectionId),
      shouldUseEnterpriseVbAnalyzer(connectionId),
      csArtifacts.extra());
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
