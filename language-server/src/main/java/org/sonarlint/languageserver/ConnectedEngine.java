/*
 * SonarLint Language Server
 * Copyright (C) 2009-2017 SonarSource SA
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
 */package org.sonarlint.languageserver;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.lsp4j.services.LanguageClient;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ModuleStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class ConnectedEngine extends AbstractEngine {
  private final ConnectedSonarLintEngine engine;
  private final SonarQubeServer server;
  private final String workspaceProjectKey;
  
  public ConnectedEngine(LanguageClient client, LogOutput logOutput, SonarQubeServer server, String workspaceProjectKey, boolean forceUpdate) {
    super(client, logOutput);
    this.server = server;
    this.workspaceProjectKey = workspaceProjectKey;
    
    ConnectedGlobalConfiguration.Builder builder = ConnectedGlobalConfiguration.builder()
        .setLogOutput(logOutput)
        .setServerId(server.id());
    engine = new ConnectedSonarLintEngineImpl(builder.build());
    
    GlobalStorageStatus globalStorageStatus = engine.getGlobalStorageStatus();

    if (forceUpdate) {
      debug("Updating binding..");
      updateEngine();
    } else if (globalStorageStatus == null) {
      debug("No binding storage found. Updating..");
      updateEngine();
    } else if (globalStorageStatus.isStale()) {
      debug("Binding storage is stale. Updating..");
      updateEngine();
    } else {
      checkModuleStatus();
    }
  }
  
  @Override
  public void stop() {
    engine.stop(false);
  }

  public void updateModule() {
    engine.updateModule(getServerConfiguration(server), workspaceProjectKey, null);
  }
  
  public void updateEngine() {
    engine.update(getServerConfiguration(server), null);
    engine.allModulesByKey().keySet().stream()
      .filter(key -> key.equals(workspaceProjectKey))
      .findAny()
      .orElseThrow(() -> {
        String msg = "Project key '" + workspaceProjectKey + "' not found in the SonarQube server";
        warn(msg);
        return new IllegalStateException(msg);
      });
    updateModule();
    debug("Binding updated");
  }

  private void checkModuleStatus() {
    engine.allModulesByKey().keySet().stream()
      .filter(key -> key.equals(workspaceProjectKey))
      .findAny()
      .orElseThrow(() -> 
      {
        String msg = "Project key '" + workspaceProjectKey + "' not found in the binding storage. Maybe an update of the storage is needed with the 'update' command?";
        warn(msg);
        return new IllegalStateException(msg);
      });
  
    ModuleStorageStatus moduleStorageStatus = engine.getModuleStorageStatus(workspaceProjectKey);
    if (moduleStorageStatus == null) {
      debug("Updating data for module..");
      engine.updateModule(getServerConfiguration(server), workspaceProjectKey, null);
      debug("Module updated");
    } else if (moduleStorageStatus.isStale()) {
      debug("Module's data is stale. Updating..");
      engine.updateModule(getServerConfiguration(server), workspaceProjectKey, null);
      debug("Module updated");
    }
  }

  private static ServerConfiguration getServerConfiguration(SonarQubeServer server) {
    ServerConfiguration.Builder serverConfigBuilder = ServerConfiguration.builder()
      .url(server.url())
      .userAgent("SonarLint VSCode " + VersionUtils.getLibraryVersion());

    String token = server.token();
    if (token != null) {
      serverConfigBuilder.token(token);
    } else {
      serverConfigBuilder.credentials(server.login(), server.password());
    }
    return serverConfigBuilder.build();
  }

  @Override
  public RuleDetails getRuleDetails(String ruleKey) {
    return engine.getRuleDetails(ruleKey);
  }

  @Override
  public AnalysisResults analyze(URI uri, Path baseDir, Iterable<ClientInputFile> inputFiles, Map<String, String> analyzerProperties, IssueListener issueListener) {
      ConnectedAnalysisConfiguration configuration  = new ConnectedAnalysisConfiguration(workspaceProjectKey, baseDir, baseDir.resolve(".sonarlint"),
        inputFiles,
        analyzerProperties);
    debug("Connected Analysis triggered on " + uri + " with configuration: \n" + configuration.toString());
    return engine.analyze( configuration, issueListener, logOutput, null);
  }

}
