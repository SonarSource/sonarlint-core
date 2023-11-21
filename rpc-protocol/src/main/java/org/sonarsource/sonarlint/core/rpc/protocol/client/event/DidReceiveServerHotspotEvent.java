/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.event;

import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class DidReceiveServerHotspotEvent {

  @NonNull
  private final String connectionId;
  private final String sonarProjectKey;
  private final String serverFilePath;

  public DidReceiveServerHotspotEvent(String connectionId, String sonarProjectKey, String serverFilePath) {
    this.connectionId = connectionId;
    this.sonarProjectKey = sonarProjectKey;
    this.serverFilePath = serverFilePath;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public String getSonarProjectKey() {
    return sonarProjectKey;
  }

  public String getServerFilePath() {
    return serverFilePath;
  }
}
