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
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidAddConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidRemoveConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidUpdateConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;

public class ConnectionServiceImpl implements ConnectionService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final EventBus clientEventBus;
  private final ConnectionConfigurationRepository repository;

  public ConnectionServiceImpl(EventBus clientEventBus, ConnectionConfigurationRepository repository) {
    this.clientEventBus = clientEventBus;
    this.repository = repository;
  }

  @Override
  public CompletableFuture<Void> initialize(InitializeParams params) {
    params.getSonarQubeConnections().forEach(c -> repository.addOrReplace(adapt(c)));
    params.getSonarCloudConnections().forEach(c -> repository.addOrReplace(adapt(c)));
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void didAddConnection(DidAddConnectionParams params) {
    addConnection(params.getAddedConnection().map(ConnectionServiceImpl::adapt, ConnectionServiceImpl::adapt));
  }

  private void addConnection(AbstractConnectionConfiguration connectionConfiguration) {
    var previous = repository.getConnectionById(connectionConfiguration.getConnectionId());
    if (previous != null) {
      LOG.error("Duplicate connection registered: {}", previous.getConnectionId());
    }
    repository.addOrReplace(connectionConfiguration);
    clientEventBus.post(new ConnectionConfigurationAddedEvent(connectionConfiguration.getConnectionId()));
  }

  private static AbstractConnectionConfiguration adapt(SonarQubeConnectionConfigurationDto sqDto) {
    return new SonarQubeConnectionConfiguration(sqDto.getConnectionId(), sqDto.getServerUrl());
  }

  private static AbstractConnectionConfiguration adapt(SonarCloudConnectionConfigurationDto sqDto) {
    return new SonarCloudConnectionConfiguration(sqDto.getConnectionId(), sqDto.getOrganization());
  }

  @Override
  public void didRemoveConnection(DidRemoveConnectionParams params) {
    var idToRemove = params.getConnectionId();
    var removed = repository.remove(idToRemove);
    if (removed == null) {
      LOG.error("Attempt to remove connection '{}' that was not registered", idToRemove);
    } else {
      clientEventBus.post(new ConnectionConfigurationRemovedEvent(idToRemove));
    }
  }

  @Override
  public void didUpdateConnection(DidUpdateConnectionParams params) {
    updateConnection(params.getUpdatedConnection().map(ConnectionServiceImpl::adapt, ConnectionServiceImpl::adapt));

  }

  private void updateConnection(AbstractConnectionConfiguration connectionConfiguration) {
    String connectionId = connectionConfiguration.getConnectionId();
    AbstractConnectionConfiguration previous = repository.getConnectionById(connectionId);
    repository.addOrReplace(connectionConfiguration);
    if (previous == null) {
      LOG.error("Attempt to update connection '{}' that was not registered", connectionId);
      clientEventBus.post(new ConnectionConfigurationAddedEvent(connectionConfiguration.getConnectionId()));
    } else {
      clientEventBus.post(new ConnectionConfigurationUpdatedEvent(connectionConfiguration.getConnectionId()));
    }

  }

}
