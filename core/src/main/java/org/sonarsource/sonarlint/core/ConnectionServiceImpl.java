/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import com.google.common.eventbus.EventBus;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectionValidator;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.ConnectionService;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionResponse;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

import static java.util.stream.Collectors.toMap;

public class ConnectionServiceImpl implements ConnectionService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final EventBus clientEventBus;
  private final ConnectionConfigurationRepository repository;
  private final HttpClientProvider httpClientProvider;

  public ConnectionServiceImpl(EventBus clientEventBus, ConnectionConfigurationRepository repository,
    List<SonarQubeConnectionConfigurationDto> initSonarQubeConnections, List<SonarCloudConnectionConfigurationDto> initSonarCloudConnections,
    HttpClientProvider httpClientProvider) {
    this.clientEventBus = clientEventBus;
    this.repository = repository;
    this.httpClientProvider = httpClientProvider;
    initSonarQubeConnections.forEach(c -> repository.addOrReplace(adapt(c)));
    initSonarCloudConnections.forEach(c -> repository.addOrReplace(adapt(c)));
  }

  private static AbstractConnectionConfiguration adapt(SonarQubeConnectionConfigurationDto sqDto) {
    return new SonarQubeConnectionConfiguration(sqDto.getConnectionId(), sqDto.getServerUrl(), sqDto.getDisableNotifications());
  }

  private static AbstractConnectionConfiguration adapt(SonarCloudConnectionConfigurationDto scDto) {
    return new SonarCloudConnectionConfiguration(scDto.getConnectionId(), scDto.getOrganization(), scDto.getDisableNotifications());
  }

  private static void putAndLogIfDuplicateId(Map<String, AbstractConnectionConfiguration> map, AbstractConnectionConfiguration config) {
    if (map.put(config.getConnectionId(), config) != null) {
      LOG.error("Duplicate connection registered: {}", config.getConnectionId());
    }
  }

  @Override
  public void didUpdateConnections(DidUpdateConnectionsParams params) {
    var newConnectionsById = new HashMap<String, AbstractConnectionConfiguration>();
    params.getSonarQubeConnections().forEach(config -> putAndLogIfDuplicateId(newConnectionsById, adapt(config)));
    params.getSonarCloudConnections().forEach(config -> putAndLogIfDuplicateId(newConnectionsById, adapt(config)));

    var previousConnectionsById = repository.getConnectionsById();

    var updatedConnections = newConnectionsById.entrySet().stream()
      .filter(e -> previousConnectionsById.containsKey(e.getKey()))
      .filter(e -> !previousConnectionsById.get(e.getKey()).equals(e.getValue()))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    var addedConnections = newConnectionsById.entrySet().stream()
      .filter(e -> !previousConnectionsById.containsKey(e.getKey()))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    var removedConnectionIds = new HashSet<>(previousConnectionsById.keySet());
    removedConnectionIds.removeAll(newConnectionsById.keySet());

    updatedConnections.values().forEach(this::updateConnection);
    addedConnections.values().forEach(this::addConnection);
    removedConnectionIds.forEach(this::removeConnection);
  }

  private void addConnection(AbstractConnectionConfiguration connectionConfiguration) {
    repository.addOrReplace(connectionConfiguration);
    clientEventBus.post(new ConnectionConfigurationAddedEvent(connectionConfiguration.getConnectionId()));
  }

  private void removeConnection(String removedConnectionId) {
    var removed = repository.remove(removedConnectionId);
    if (removed == null) {
      LOG.debug("Attempt to remove connection '{}' that was not registered. Possibly a race condition?", removedConnectionId);
    } else {
      clientEventBus.post(new ConnectionConfigurationRemovedEvent(removedConnectionId));
    }
  }

  private void updateConnection(AbstractConnectionConfiguration connectionConfiguration) {
    var connectionId = connectionConfiguration.getConnectionId();
    var previous = repository.addOrReplace(connectionConfiguration);
    if (previous == null) {
      LOG.debug("Attempt to update connection '{}' that was not registered. Possibly a race condition?", connectionId);
      clientEventBus.post(new ConnectionConfigurationAddedEvent(connectionConfiguration.getConnectionId()));
    } else {
      clientEventBus.post(new ConnectionConfigurationUpdatedEvent(connectionConfiguration.getConnectionId()));
    }
  }

  @Override
  public CompletableFuture<ValidateConnectionResponse> validateConnection(ValidateConnectionParams params) {
    var helper = buildServerApiHelper(params);
    var connectionValidator = new ConnectionValidator(helper);
    return connectionValidator.validateConnection().thenApply(r -> new ValidateConnectionResponse(r.success(), r.message()));
  }

  @NotNull
  ServerApiHelper buildServerApiHelper(ValidateConnectionParams params) {
    var endpointParams = params.getTransientConnection().map(
      sq -> new EndpointParams(sq.getServerUrl(), false, null),
      sc -> new EndpointParams(SonarCloudConnectionConfiguration.getSonarCloudUrl(), true, sc.getOrganization()));
    var httpClient = params.getTransientConnection()
      .map(TransientSonarQubeConnectionDto::getCredentials, TransientSonarCloudConnectionDto::getCredentials)
      .map(
        tokenDto -> httpClientProvider.getHttpClientWithPreemptiveAuth(tokenDto.getToken(), null),
        userPass -> httpClientProvider.getHttpClientWithPreemptiveAuth(userPass.getUsername(), userPass.getPassword()));
    return new ServerApiHelper(endpointParams, httpClient);
  }

  public List<AbstractConnectionConfiguration> findByUrl(String serverUrl) {
    return repository.findByUrl(serverUrl);
  }

  public boolean hasConnectionWithOrigin(String serverOrigin) {
    return repository.hasConnectionWithOrigin(serverOrigin);
  }
}
