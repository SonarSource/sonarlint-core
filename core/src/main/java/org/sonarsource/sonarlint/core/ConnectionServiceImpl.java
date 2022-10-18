/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.clientapi.connection.ConnectionService;
import org.sonarsource.sonarlint.core.clientapi.connection.config.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidAddConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidRemoveConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidUpdateConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.InitializeParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class ConnectionServiceImpl implements ConnectionService {

  private final SonarLintLogger LOG = SonarLintLogger.get();

  private final Map<String, AbstractConnectionConfiguration> connectionsById = new HashMap<>();

  @Override
  public CompletableFuture<Void> initialize(InitializeParams params) {
    params.getSonarQubeConnections().forEach(c -> connectionsById.put(c.getConnectionId(), c));
    params.getSonarCloudConnections().forEach(c -> connectionsById.put(c.getConnectionId(), c));
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void didAddConnection(DidAddConnectionParams params) {
    params.getAddedConnection().map(this::addConnection, this::addConnection);
  }

  private <T> T addConnection(AbstractConnectionConfiguration connectionConfiguration) {
    var previous = connectionsById.put(connectionConfiguration.getConnectionId(), connectionConfiguration);
    if (previous != null) {
      LOG.error("Duplicate connection registered: {}", previous.getConnectionId());
    }
    return null;
  }

  @Override
  public void didRemoveConnection(DidRemoveConnectionParams params) {
    var idToRemove = params.getConnectionId();
    AbstractConnectionConfiguration removed = connectionsById.remove(idToRemove);
    if (removed == null) {
      LOG.error("Attempt to remove connection '{}' that was not registered", idToRemove);
    }
  }

  @Override
  public void didUpdateConnection(DidUpdateConnectionParams params) {
    params.getUpdatedConnection().map(this::updateConnection, this::updateConnection);

  }

  private <T> T updateConnection(AbstractConnectionConfiguration connectionConfiguration) {
    String connectionId = connectionConfiguration.getConnectionId();
    AbstractConnectionConfiguration previous = connectionsById.get(connectionId);
    if (previous == null) {
      LOG.error("Attempt to update connection '{}' that was not registered", connectionId);
    }

    connectionsById.put(connectionId, connectionConfiguration);
    return null;
  }

  public Map<String, AbstractConnectionConfiguration> getConnectionsById() {
    return Map.copyOf(connectionsById);
  }
}
