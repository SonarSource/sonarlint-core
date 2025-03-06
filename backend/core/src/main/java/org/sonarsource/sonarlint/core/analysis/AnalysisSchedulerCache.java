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
package org.sonarsource.sonarlint.core.analysis;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.monitoring.MonitoringService;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.fs.FileExclusionService;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rules.RulesService;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.PluginsSynchronizedEvent;
import org.springframework.context.event.EventListener;

public class AnalysisSchedulerCache {
  private final Path workDir;
  private final RulesRepository rulesRepository;
  private final RulesService rulesService;
  private final LanguageSupportRepository languageSupportRepository;
  private final ClientFileSystemService fileSystemService;
  private final MonitoringService monitoringService;
  private final FileExclusionService fileExclusionService;
  private final ClientFileSystemService clientFileSystemService;
  private final SonarLintRpcClient client;
  private final Path esLintBridgeServerPath;
  private final InitializeParams initializeParams;
  private final ConfigurationRepository configurationRepository;
  private final UserAnalysisPropertiesRepository userAnalysisPropertiesRepository;
  private final StorageService storageService;
  private final PluginsService pluginsService;
  private final NodeJsService nodeJsService;
  private final Map<String, String> extraProperties = new HashMap<>();
  private final Path csharpOssPluginPath;
  private final AtomicReference<AnalysisScheduler> standaloneScheduler = new AtomicReference<>();
  private final Map<String, AnalysisScheduler> connectedSchedulersByConnectionId = new ConcurrentHashMap<>();

  public AnalysisSchedulerCache(InitializeParams initializeParams, UserPaths userPaths, ConfigurationRepository configurationRepository, NodeJsService nodeJsService,
    UserAnalysisPropertiesRepository userAnalysisPropertiesRepository, StorageService storageService, PluginsService pluginsService, RulesRepository rulesRepository,
    RulesService rulesService, LanguageSupportRepository languageSupportRepository, ClientFileSystemService fileSystemService, MonitoringService monitoringService,
    FileExclusionService fileExclusionService, ClientFileSystemService clientFileSystemService, SonarLintRpcClient client, Path esLintBridgeServerPath) {
    this.initializeParams = initializeParams;
    this.configurationRepository = configurationRepository;
    this.userAnalysisPropertiesRepository = userAnalysisPropertiesRepository;
    this.storageService = storageService;
    this.pluginsService = pluginsService;
    this.nodeJsService = nodeJsService;
    this.workDir = userPaths.getWorkDir();
    this.rulesRepository = rulesRepository;
    this.rulesService = rulesService;
    this.languageSupportRepository = languageSupportRepository;
    this.fileSystemService = fileSystemService;
    this.monitoringService = monitoringService;
    this.fileExclusionService = fileExclusionService;
    this.clientFileSystemService = clientFileSystemService;
    this.client = client;
    this.esLintBridgeServerPath = esLintBridgeServerPath;
    var shouldSupportCsharp = initializeParams.getEnabledLanguagesInStandaloneMode().contains(Language.CS);
    var languageSpecificRequirements = initializeParams.getLanguageSpecificRequirements();
    if (shouldSupportCsharp && languageSpecificRequirements != null) {
      var omnisharpRequirements = languageSpecificRequirements.getOmnisharpRequirements();
      if (omnisharpRequirements != null) {
        csharpOssPluginPath = omnisharpRequirements.getOssAnalyzerPath();
        extraProperties.put("sonar.cs.internal.omnisharpMonoLocation", omnisharpRequirements.getMonoDistributionPath().toString());
        extraProperties.put("sonar.cs.internal.omnisharpWinLocation", omnisharpRequirements.getDotNet472DistributionPath().toString());
        extraProperties.put("sonar.cs.internal.omnisharpNet6Location", omnisharpRequirements.getDotNet6DistributionPath().toString());
      } else {
        csharpOssPluginPath = null;
      }
    } else {
      csharpOssPluginPath = null;
    }
  }

  @CheckForNull
  public AnalysisScheduler getAnalysisSchedulerIfStarted(String configurationScopeId) {
    return configurationRepository.getEffectiveBinding(configurationScopeId)
      .map(binding -> getConnectedSchedulerIfStarted(binding.connectionId()))
      .orElseGet(this::getStandaloneSchedulerIfStarted);
  }

  public AnalysisScheduler getOrCreateAnalysisScheduler(String configurationScopeId) {
    return configurationRepository.getEffectiveBinding(configurationScopeId)
      .map(binding -> getOrCreateConnectedScheduler(binding.connectionId()))
      .orElseGet(this::getOrCreateStandaloneScheduler);
  }

  private synchronized AnalysisScheduler getOrCreateConnectedScheduler(String connectionId) {
    return connectedSchedulersByConnectionId.computeIfAbsent(connectionId,
      k -> createScheduler(pluginsService.getPlugins(connectionId), pluginsService.getEffectivePathToCsharpAnalyzer(connectionId)));
  }

  @CheckForNull
  private synchronized AnalysisScheduler getConnectedSchedulerIfStarted(String connectionId) {
    return connectedSchedulersByConnectionId.get(connectionId);
  }

  private synchronized AnalysisScheduler getOrCreateStandaloneScheduler() {
    var scheduler = standaloneScheduler.get();
    if (scheduler == null) {
      var engine = createEngine(pluginsService.getEmbeddedPlugins(), csharpOssPluginPath);
      scheduler = new AnalysisScheduler(engine, configurationRepository, nodeJsService, userAnalysisPropertiesRepository, storageService, pluginsService, rulesRepository,
        rulesService, languageSupportRepository, fileSystemService, monitoringService, fileExclusionService, clientFileSystemService, client, esLintBridgeServerPath);
      standaloneScheduler.set(scheduler);
    }
    return scheduler;
  }

  @CheckForNull
  private synchronized AnalysisScheduler getStandaloneSchedulerIfStarted() {
    return standaloneScheduler.get();
  }

  private AnalysisScheduler createScheduler(LoadedPlugins plugins, @Nullable Path actualCsharpAnalyzerPath) {
    return new AnalysisScheduler(createEngine(plugins, actualCsharpAnalyzerPath), configurationRepository, nodeJsService, userAnalysisPropertiesRepository, storageService,
      pluginsService, rulesRepository,
      rulesService, languageSupportRepository, fileSystemService, monitoringService, fileExclusionService, clientFileSystemService, client, esLintBridgeServerPath);
  }

  private AnalysisEngine createEngine(LoadedPlugins plugins, @Nullable Path actualCsharpAnalyzerPath) {
    var activeNodeJs = nodeJsService.getActiveNodeJs();
    var nodeJsPath = activeNodeJs == null ? null : activeNodeJs.getPath();
    var fullExtraProperties = new HashMap<>(extraProperties);
    if (actualCsharpAnalyzerPath != null) {
      fullExtraProperties.put("sonar.cs.internal.analyzerPath", actualCsharpAnalyzerPath.toString());
    }
    var analysisEngineConfiguration = AnalysisEngineConfiguration.builder()
      .setWorkDir(workDir)
      .setClientPid(ProcessHandle.current().pid())
      .setExtraProperties(fullExtraProperties)
      .setNodeJs(nodeJsPath)
      .setModulesProvider(this::getModules)
      .build();
    return new AnalysisEngine(analysisEngineConfiguration, plugins, SonarLintLogger.get().getTargetForCopy());
  }

  private List<ClientModuleInfo> getModules() {
    var leafConfigScopeIds = configurationRepository.getLeafConfigScopeIds();
    return leafConfigScopeIds.stream().map(scopeId -> {
      var backendModuleFileSystem = new BackendModuleFileSystem(clientFileSystemService, scopeId);
      return new ClientModuleInfo(scopeId, backendModuleFileSystem);
    }).toList();
  }

  @EventListener
  public void onConnectionRemoved(ConnectionConfigurationRemovedEvent event) {
    stopSchedulerGracefully(event.getRemovedConnectionId());
  }

  @EventListener
  public void onPluginsSynchronized(PluginsSynchronizedEvent event) {
    stopSchedulerGracefully(event.getConnectionId());
  }

  @EventListener
  public void onClientNodeJsPathChanged(ClientNodeJsPathChanged event) {
    stopAllGracefully();
  }

  @PreDestroy
  public void shutdown() {
    stopAll();
  }

  private synchronized void stopSchedulerGracefully(String event) {
    var scheduler = connectedSchedulersByConnectionId.remove(event);
    if (scheduler != null) {
      scheduler.finishGracefully();
    }
  }

  private synchronized void stopAllGracefully() {
    var standaloneAnalysisScheduler = this.standaloneScheduler.get();
    if (standaloneAnalysisScheduler != null) {
      standaloneAnalysisScheduler.finishGracefully();
      this.standaloneScheduler.set(null);
    }
    connectedSchedulersByConnectionId.forEach((connectionId, scheduler) -> scheduler.finishGracefully());
    connectedSchedulersByConnectionId.clear();
  }

  private synchronized void stopAll() {
    var standaloneAnalysisScheduler = this.standaloneScheduler.get();
    if (standaloneAnalysisScheduler != null) {
      standaloneAnalysisScheduler.stop();
      this.standaloneScheduler.set(null);
    }
    connectedSchedulersByConnectionId.forEach((connectionId, scheduler) -> scheduler.stop());
    connectedSchedulersByConnectionId.clear();
  }

  public void registerModuleIfLeafConfigScope(String scopeId) {
    var analysisScheduler = getAnalysisSchedulerIfStarted(scopeId);
    if (analysisScheduler != null && configurationRepository.isLeafConfigScope(scopeId)) {
      var backendModuleFileSystem = new BackendModuleFileSystem(clientFileSystemService, scopeId);
      var clientModuleInfo = new ClientModuleInfo(scopeId, backendModuleFileSystem);
      analysisScheduler.registerModule(clientModuleInfo);
    }
  }

  public void unregisterModule(String scopeId) {
    var analysisScheduler = getAnalysisSchedulerIfStarted(scopeId);
    if (analysisScheduler != null && configurationRepository.isLeafConfigScope(scopeId)) {
      analysisScheduler.unregisterModule(scopeId);
    }
  }
}
