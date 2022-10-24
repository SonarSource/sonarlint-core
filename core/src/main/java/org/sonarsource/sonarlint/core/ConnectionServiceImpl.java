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

import com.google.common.eventbus.EventBus;
import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.clientapi.connection.ConnectionService;
import org.sonarsource.sonarlint.core.clientapi.connection.config.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidAddConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidRemoveConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidUpdateConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.InitializeParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.ConnectionAddedEvent;
import org.sonarsource.sonarlint.core.repository.ConnectionConfigurationRepository;

public class ConnectionServiceImpl implements ConnectionService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final EventBus clientEventBus;
  private final ConnectionConfigurationRepository referential;

  public ConnectionServiceImpl(EventBus clientEventBus, ConnectionConfigurationRepository referential) {
    this.clientEventBus = clientEventBus;
    this.referential = referential;
  }

  @Override
  public CompletableFuture<Void> initialize(InitializeParams params) {
    params.getSonarQubeConnections().forEach(referential::addOrReplace);
    params.getSonarCloudConnections().forEach(referential::addOrReplace);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void didAddConnection(DidAddConnectionParams params) {
    params.getAddedConnection().map(this::addConnection, this::addConnection);
  }

  private <T> T addConnection(AbstractConnectionConfiguration connectionConfiguration) {
    var previous = referential.getConnectionById(connectionConfiguration.getConnectionId());
    if (previous != null) {
      LOG.error("Duplicate connection registered: {}", previous.getConnectionId());
    }
    referential.addOrReplace(connectionConfiguration);
    clientEventBus.post(new ConnectionAddedEvent(connectionConfiguration.getConnectionId()));
    return null;
  }

  @Override
  public void didRemoveConnection(DidRemoveConnectionParams params) {
    var idToRemove = params.getConnectionId();
    var removed = referential.remove(idToRemove);
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
    AbstractConnectionConfiguration previous = referential.getConnectionById(connectionId);
    if (previous == null) {
      LOG.error("Attempt to update connection '{}' that was not registered", connectionId);
    }

    referential.addOrReplace(connectionConfiguration);
    return null;
  }

}
