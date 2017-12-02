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
 */
package org.sonarlint.languageserver;

import java.net.URL;
import java.util.Map;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;

public class EngineFactory {
  private final LanguageClient client;
  private final LogOutput logOutput;
  private final URL[] analyzers;
  
  private String workspaceServerId;
  private String workspaceProjectKey;
  private SonarQubeServer server;
  private AbstractEngine engine;
  
  public EngineFactory(LanguageClient client, LogOutput logOutput, URL[] analyzers) {
    this.client = client;
    this.logOutput = logOutput;
    this.analyzers = analyzers;
  }

  public synchronized AbstractEngine get() {
    return get(false);
  }
  public synchronized AbstractEngine get(boolean forceUpdate) {
    if ( engine == null ) {
      if ( isConnectedMode() ) {
        this.engine = new ConnectedEngine(client, logOutput, server, workspaceProjectKey, forceUpdate);
      }else {
        this.engine = new StandaloneEngine(client, logOutput, analyzers);
      }
    }
    return this.engine;
  }

  public synchronized void updateStandalone(boolean forceUpdate) {
    if ( forceUpdate || !(this.engine instanceof StandaloneEngine) ) {
      client.logMessage(new MessageParams(MessageType.Log, "Updating standalone"));
      if ( engine != null ) {
        engine.stop();
        engine = null;
      }
      this.server = null;
      this.workspaceServerId = null;
      this.workspaceProjectKey = null;
    }
  }
  
  public synchronized boolean updateConnected(Map<String, SonarQubeServer> servers, String workspaceServerId, String workspaceProjectKey, boolean forceUpdate) {
    if ( forceUpdate || !(this.engine instanceof ConnectedEngine) || this.workspaceServerId != workspaceServerId  || this.workspaceProjectKey != workspaceProjectKey ) {
      client.logMessage(new MessageParams(MessageType.Log, "Updating standalone"));
      
      if ( engine != null ) {
        engine.stop();
        engine = null;
      }

      this.server = servers.get(workspaceServerId);
      if ( this.server == null ) {
        client.logMessage(new MessageParams(MessageType.Error, "Server not found: " + workspaceServerId));
        this.workspaceServerId = null;
        this.workspaceProjectKey = null;
        return false;
      }else {
        this.workspaceServerId = workspaceServerId;
        this.workspaceProjectKey = workspaceProjectKey;
        return true;
      }
    }else {
      return this.workspaceServerId != null;
    }
  }

  public synchronized void stop() {
    if ( engine != null ) {
      engine.stop();
    }
  }

  public boolean isConnectedMode() {
    return workspaceServerId != null && workspaceProjectKey != null && server != null;
  }

  public void updateBindings() {
    ((ConnectedEngine)get(true)).updateEngine();
  }
}
