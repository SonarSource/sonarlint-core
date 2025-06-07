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
import org.sonarsource.sonarlint.core.analysis.api.AnalysisSchedulerConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.command.RegisterModuleCommand;
import org.sonarsource.sonarlint.core.analysis.command.UnregisterModuleCommand;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.monitoring.Trace;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.sync.PluginsSynchronizedEvent;
import org.springframework.context.event.EventListener;

import static org.sonarsource.sonarlint.core.commons.monitoring.Trace.startChild;

public class AnalysisSchedulerCache {
  private final Path workDir;
  private final ClientFileSystemService clientFileSystemService;
  private final ConfigurationRepository configurationRepository;
  private final PluginsService pluginsService;
  private final NodeJsService nodeJsService;
  private final Map<String, String> extraProperties = new HashMap<>();
  private final Path csharpOssPluginPath;
  private final AtomicReference<AnalysisScheduler> standaloneScheduler = new AtomicReference<>();
  private final Map<String, AnalysisScheduler> connectedSchedulerByConnectionId = new ConcurrentHashMap<>();

  public AnalysisSchedulerCache(InitializeParams initializeParams, UserPaths userPaths, ConfigurationRepository configurationRepository, NodeJsService nodeJsService,
    PluginsService pluginsService, ClientFileSystemService clientFileSystemService) {
    this.configurationRepository = configurationRepository;
    this.pluginsService = pluginsService;
    this.nodeJsService = nodeJsService;
    this.workDir = userPaths.getWorkDir();
    this.clientFileSystemService = clientFileSystemService;
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
    return getOrCreateAnalysisScheduler(configurationScopeId, null);
  }

  public AnalysisScheduler getOrCreateAnalysisScheduler(String configurationScopeId, @Nullable Trace trace) {
    return configurationRepository.getEffectiveBinding(configurationScopeId)
      .map(binding -> getOrCreateConnectedScheduler(binding.connectionId(), trace))
      .orElseGet(() -> getOrCreateStandaloneScheduler(trace));
  }

  private synchronized AnalysisScheduler getOrCreateConnectedScheduler(String connectionId, @Nullable Trace trace) {
    return connectedSchedulerByConnectionId.computeIfAbsent(connectionId,
      k -> createScheduler(pluginsService.getPlugins(connectionId), pluginsService.getEffectivePathToCsharpAnalyzer(connectionId), trace));
  }

  @CheckForNull
  private synchronized AnalysisScheduler getConnectedSchedulerIfStarted(String connectionId) {
    return connectedSchedulerByConnectionId.get(connectionId);
  }

  private synchronized AnalysisScheduler getOrCreateStandaloneScheduler(@Nullable Trace trace) {
    var scheduler = standaloneScheduler.get();
    if (scheduler == null) {
      scheduler = createScheduler(pluginsService.getEmbeddedPlugins(), csharpOssPluginPath, trace);
      standaloneScheduler.set(scheduler);
    }
    return scheduler;
  }

  @CheckForNull
  private synchronized AnalysisScheduler getStandaloneSchedulerIfStarted() {
    return standaloneScheduler.get();
  }

  private AnalysisScheduler createScheduler(LoadedPlugins plugins, @Nullable Path actualCsharpAnalyzerPath, @Nullable Trace trace) {
    return new AnalysisScheduler(createSchedulerConfiguration(actualCsharpAnalyzerPath, trace), plugins, SonarLintLogger.get().getTargetForCopy());
  }

  private AnalysisSchedulerConfiguration createSchedulerConfiguration(@Nullable Path actualCsharpAnalyzerPath) {
    return createSchedulerConfiguration(actualCsharpAnalyzerPath, null);
  }

  private AnalysisSchedulerConfiguration createSchedulerConfiguration(@Nullable Path actualCsharpAnalyzerPath, @Nullable Trace trace) {
    var activeNodeJs = startChild(trace, "getActiveNodeJs", "createSchedulerConfiguration", nodeJsService::getActiveNodeJs);
    var nodeJsPath = activeNodeJs == null ? null : activeNodeJs.getPath();
    var fullExtraProperties = new HashMap<>(extraProperties);
    if (actualCsharpAnalyzerPath != null) {
      fullExtraProperties.put("sonar.cs.internal.analyzerPath", actualCsharpAnalyzerPath.toString());
    }
    return AnalysisSchedulerConfiguration.builder()
      .setWorkDir(workDir)
      .setClientPid(ProcessHandle.current().pid())
      .setExtraProperties(fullExtraProperties)
      .setNodeJs(nodeJsPath)
      .setModulesProvider(this::getModules)
      .build();
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
    stop(event.getRemovedConnectionId());
  }

  @EventListener
  public void onPluginsSynchronized(PluginsSynchronizedEvent event) {
    var newPlugins = pluginsService.reloadPluginsFromStorage(event.connectionId());
    resetScheduler(event.connectionId(), newPlugins);
  }

  @EventListener
  public void onClientNodeJsPathChanged(ClientNodeJsPathChanged event) {
    resetStartedSchedulers();
  }

  @PreDestroy
  public void shutdown() {
    try {
      stopAll();
    } catch (Exception e) {
      SonarLintLogger.get().error("Error shutting down analysis scheduler cache", e);
    }
  }

  private synchronized void resetScheduler(String connectionId, LoadedPlugins newPlugins) {
    connectedSchedulerByConnectionId.computeIfPresent(connectionId, (k, scheduler) -> {
      scheduler.reset(createSchedulerConfiguration(pluginsService.getEffectivePathToCsharpAnalyzer(connectionId)), newPlugins);
      return scheduler;
    });
  }

  private synchronized void resetStartedSchedulers() {
    var standaloneAnalysisScheduler = this.standaloneScheduler.get();
    if (standaloneAnalysisScheduler != null) {
      standaloneAnalysisScheduler.reset(createSchedulerConfiguration(csharpOssPluginPath), pluginsService.getEmbeddedPlugins());
    }
    connectedSchedulerByConnectionId.forEach(
      (connectionId, scheduler) -> scheduler.reset(createSchedulerConfiguration(pluginsService.getEffectivePathToCsharpAnalyzer(connectionId)),
        pluginsService.getPlugins(connectionId)));
  }

  private synchronized void stopAll() {
    var standaloneAnalysisScheduler = this.standaloneScheduler.get();
    if (standaloneAnalysisScheduler != null) {
      standaloneAnalysisScheduler.stop();
      this.standaloneScheduler.set(null);
    }
    connectedSchedulerByConnectionId.forEach((connectionId, scheduler) -> scheduler.stop());
    connectedSchedulerByConnectionId.clear();
  }

  private synchronized void stop(String connectionId) {
    var scheduler = connectedSchedulerByConnectionId.remove(connectionId);
    if (scheduler != null) {
      scheduler.stop();
    }
  }

  public void registerModuleIfLeafConfigScope(String scopeId) {
    var analysisScheduler = getAnalysisSchedulerIfStarted(scopeId);
    if (analysisScheduler != null && configurationRepository.isLeafConfigScope(scopeId)) {
      var backendModuleFileSystem = new BackendModuleFileSystem(clientFileSystemService, scopeId);
      var clientModuleInfo = new ClientModuleInfo(scopeId, backendModuleFileSystem);
      analysisScheduler.post(new RegisterModuleCommand(clientModuleInfo));
    }
  }

  public void unregisterModule(String scopeId, @Nullable String connectionId) {
    var analysisScheduler = connectionId == null ? getStandaloneSchedulerIfStarted() : getConnectedSchedulerIfStarted(connectionId);
    if (analysisScheduler != null) {
      analysisScheduler.post(new UnregisterModuleCommand(scopeId));
    }
  }
}
