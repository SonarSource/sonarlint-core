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
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.command.RegisterModuleCommand;
import org.sonarsource.sonarlint.core.analysis.command.UnregisterModuleCommand;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.sync.PluginsSynchronizedEvent;
import org.springframework.context.event.EventListener;

public class AnalysisEngineCache {
  private final Path workDir;
  private final ClientFileSystemService clientFileSystemService;
  private final ConfigurationRepository configurationRepository;
  private final PluginsService pluginsService;
  private final NodeJsService nodeJsService;
  private final Map<String, String> extraProperties = new HashMap<>();
  private final Path csharpOssPluginPath;
  private AnalysisEngine standaloneEngine;
  private final Map<String, AnalysisEngine> connectedEnginesByConnectionId = new ConcurrentHashMap<>();

  public AnalysisEngineCache(ConfigurationRepository configurationRepository, NodeJsService nodeJsService, InitializeParams initializeParams,
    PluginsService pluginsService, ClientFileSystemService clientFileSystemService) {
    this.configurationRepository = configurationRepository;
    this.pluginsService = pluginsService;
    this.nodeJsService = nodeJsService;
    this.workDir = initializeParams.getWorkDir();
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
  public AnalysisEngine getAnalysisEngineIfStarted(String configurationScopeId) {
    return configurationRepository.getEffectiveBinding(configurationScopeId)
      .map(binding -> getConnectedEngineIfStarted(binding.getConnectionId()))
      .orElseGet(this::getStandaloneEngineIfStarted);
  }

  public AnalysisEngine getOrCreateAnalysisEngine(String configurationScopeId) {
    return configurationRepository.getEffectiveBinding(configurationScopeId)
      .map(binding -> getOrCreateConnectedEngine(binding.getConnectionId()))
      .orElseGet(this::getOrCreateStandaloneEngine);
  }

  private synchronized AnalysisEngine getOrCreateConnectedEngine(String connectionId) {
    return connectedEnginesByConnectionId.computeIfAbsent(connectionId,
      k -> createEngine(pluginsService.getPlugins(connectionId), pluginsService.getEffectivePathToCsharpAnalyzer(connectionId)));
  }

  @CheckForNull
  private synchronized AnalysisEngine getConnectedEngineIfStarted(String connectionId) {
    return connectedEnginesByConnectionId.get(connectionId);
  }

  private synchronized AnalysisEngine getOrCreateStandaloneEngine() {
    if (standaloneEngine == null) {
      standaloneEngine = createEngine(pluginsService.getEmbeddedPlugins(), csharpOssPluginPath);
    }
    return standaloneEngine;
  }

  @CheckForNull
  private synchronized AnalysisEngine getStandaloneEngineIfStarted() {
    return standaloneEngine;
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
    return new AnalysisEngine(analysisEngineConfiguration, plugins, SonarLintLogger.getTargetForCopy());
  }

  private List<ClientModuleInfo> getModules() {
    var leafConfigScopeIds = configurationRepository.getLeafConfigScopeIds();
    return leafConfigScopeIds.stream().map(scopeId -> {
      var backendModuleFileSystem = new BackendModuleFileSystem(clientFileSystemService, scopeId);
      return new ClientModuleInfo(scopeId, backendModuleFileSystem);
    }).collect(Collectors.toList());
  }

  @EventListener
  public void onConnectionRemoved(ConnectionConfigurationRemovedEvent event) {
    stopEngineGracefully(event.getRemovedConnectionId());
  }

  @EventListener
  public void onPluginsSynchronized(PluginsSynchronizedEvent event) {
    stopEngineGracefully(event.getConnectionId());
  }

  @EventListener
  public void onClientNodeJsPathChanged(ClientNodeJsPathChanged event) {
    stopAllGracefully();
  }

  @PreDestroy
  public void shutdown() {
    stopAll();
  }

  private synchronized void stopEngineGracefully(String event) {
    var engine = connectedEnginesByConnectionId.remove(event);
    if (engine != null) {
      engine.finishGracefully();
    }
  }

  private synchronized void stopAllGracefully() {
    if (this.standaloneEngine != null) {
      this.standaloneEngine.finishGracefully();
      this.standaloneEngine = null;
    }
    connectedEnginesByConnectionId.forEach((connectionId, engine) -> engine.finishGracefully());
    connectedEnginesByConnectionId.clear();
  }

  private synchronized void stopAll() {
    if (this.standaloneEngine != null) {
      this.standaloneEngine.stop();
      this.standaloneEngine = null;
    }
    connectedEnginesByConnectionId.forEach((connectionId, engine) -> engine.stop());
    connectedEnginesByConnectionId.clear();
  }

  public void registerModuleIfLeafConfigScope(String scopeId) {
    var analysisEngine = getAnalysisEngineIfStarted(scopeId);
    if (analysisEngine != null && configurationRepository.isLeafConfigScope(scopeId)) {
      var backendModuleFileSystem = new BackendModuleFileSystem(clientFileSystemService, scopeId);
      var clientModuleInfo = new ClientModuleInfo(scopeId, backendModuleFileSystem);
      analysisEngine.post(new RegisterModuleCommand(clientModuleInfo), new ProgressMonitor(null));
    }
  }

  public void unregisterModule(String scopeId) {
    var analysisEngine = getAnalysisEngineIfStarted(scopeId);
    if (analysisEngine != null && configurationRepository.isLeafConfigScope(scopeId)) {
      analysisEngine.post(new UnregisterModuleCommand(scopeId), new ProgressMonitor(null));
    }
  }
}
