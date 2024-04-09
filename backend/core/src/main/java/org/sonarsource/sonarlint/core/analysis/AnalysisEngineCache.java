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
package org.sonarsource.sonarlint.core.analysis;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
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
  private final long pid;
  private final ClientFileSystemService clientFileSystemService;
  private final ConfigurationRepository configurationRepository;
  private final PluginsService pluginsService;
  private final NodeJsService nodeJsService;
  private final Map<String, String> extraProperties = new HashMap<>();
  private AnalysisEngine standaloneEngine;
  private final Map<String, AnalysisEngine> connectedEnginesByConnectionId = new ConcurrentHashMap<>();

  public AnalysisEngineCache(ConfigurationRepository configurationRepository, NodeJsService nodeJsService, InitializeParams initializeParams,
    PluginsService pluginsService, ClientFileSystemService clientFileSystemService) {
    this.configurationRepository = configurationRepository;
    this.pluginsService = pluginsService;
    this.nodeJsService = nodeJsService;
    this.workDir = initializeParams.getWorkDir();
    this.pid = initializeParams.getClientConstantInfo().getPid();
    this.clientFileSystemService = clientFileSystemService;
    var shouldSupportCsharp = initializeParams.getEnabledLanguagesInStandaloneMode().contains(Language.CS);
    var languageSpecificRequirements = initializeParams.getLanguageSpecificRequirements();
    if (shouldSupportCsharp && languageSpecificRequirements != null) {
      var omnisharpRequirements = languageSpecificRequirements.getOmnisharpRequirements();
      if (omnisharpRequirements != null) {
        extraProperties.put("sonar.cs.internal.omnisharpMonoLocation", omnisharpRequirements.getMonoDistributionPath().toString());
        extraProperties.put("sonar.cs.internal.omnisharpWinLocation", omnisharpRequirements.getDotNet472DistributionPath().toString());
        extraProperties.put("sonar.cs.internal.omnisharpNet6Location", omnisharpRequirements.getDotNet6DistributionPath().toString());
      }
    }
  }

  public AnalysisEngine getOrCreateAnalysisEngine(String configurationScopeId) {
    return configurationRepository.getEffectiveBinding(configurationScopeId)
      .map(binding -> getOrCreateConnectedEngine(binding.getConnectionId()))
      .orElseGet(this::getOrCreateStandaloneEngine);
  }

  private synchronized AnalysisEngine getOrCreateConnectedEngine(String connectionId) {
    return connectedEnginesByConnectionId.computeIfAbsent(connectionId, k -> createEngine(pluginsService.getPlugins(connectionId)));
  }

  private synchronized AnalysisEngine getOrCreateStandaloneEngine() {
    if (standaloneEngine == null) {
      standaloneEngine = createEngine(pluginsService.getEmbeddedPlugins());
    }
    return standaloneEngine;
  }

  private AnalysisEngine createEngine(LoadedPlugins plugins) {
    var activeNodeJs = nodeJsService.getActiveNodeJs();
    var nodeJsPath = activeNodeJs == null ? null : activeNodeJs.getPath();
    var modules = getModules();
    var analysisEngineConfiguration = AnalysisEngineConfiguration.builder()
      .setWorkDir(workDir)
      .setClientPid(pid)
      .setExtraProperties(extraProperties)
      .setNodeJs(nodeJsPath)
      .setModulesProvider(() -> modules)
      .build();
    return new AnalysisEngine(analysisEngineConfiguration, plugins, SonarLintLogger.getTargetForCopy());
  }

  private List<ClientModuleInfo> getModules() {
    var leafConfigScopeIds = configurationRepository.getLeafConfigScopeIds();
    return leafConfigScopeIds.stream().map(scope -> {
      var backendModuleFileSystem = new BackendModuleFileSystem(clientFileSystemService, scope);
      return new ClientModuleInfo(scope, backendModuleFileSystem);
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
    stopAllGracefully();
  }

  private synchronized void stopEngineGracefully(String event) {
    var engine = connectedEnginesByConnectionId.get(event);
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

}
