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
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectionValidator;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.ConnectionService;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.auth.HelpGenerateUserTokenParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.check.CheckSmartNotificationsSupportedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.check.CheckSmartNotificationsSupportedResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.org.GetOrganizationParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.org.GetOrganizationResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.org.ListUserOrganizationsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.org.ListUserOrganizationsResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.org.OrganizationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionResponse;
import org.sonarsource.sonarlint.core.clientapi.common.TokenDto;
import org.sonarsource.sonarlint.core.clientapi.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.organization.OrganizationApi;

import static java.util.stream.Collectors.toMap;

@Named
@Singleton
public class ConnectionServiceImpl implements ConnectionService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final EventBus clientEventBus;
  private final ConnectionConfigurationRepository repository;
  private final HttpClientProvider httpClientProvider;
  private final TokenGeneratorHelper tokenGeneratorHelper;

  @Inject
  public ConnectionServiceImpl(EventBus clientEventBus, ConnectionConfigurationRepository repository, InitializeParams params,
    HttpClientProvider httpClientProvider, TokenGeneratorHelper tokenGeneratorHelper) {
    this(clientEventBus, repository, params.getSonarQubeConnections(), params.getSonarCloudConnections(), httpClientProvider, tokenGeneratorHelper);
  }

  ConnectionServiceImpl(EventBus clientEventBus, ConnectionConfigurationRepository repository,
    List<SonarQubeConnectionConfigurationDto> initSonarQubeConnections, List<SonarCloudConnectionConfigurationDto> initSonarCloudConnections,
    HttpClientProvider httpClientProvider, TokenGeneratorHelper tokenGeneratorHelper) {
    this.clientEventBus = clientEventBus;
    this.repository = repository;
    this.httpClientProvider = httpClientProvider;
    this.tokenGeneratorHelper = tokenGeneratorHelper;
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
    var helper = buildServerApiHelper(params.getTransientConnection());
    var connectionValidator = new ConnectionValidator(helper);
    return connectionValidator.validateConnection().thenApply(r -> new ValidateConnectionResponse(r.success(), r.message()));
  }

  @Override
  public CompletableFuture<CheckSmartNotificationsSupportedResponse> checkSmartNotificationsSupported(CheckSmartNotificationsSupportedParams params) {
    var helper = buildServerApiHelper(params.getTransientConnection());
    var developersApi = new ServerApi(helper).developers();
    return CompletableFuture.supplyAsync(() -> new CheckSmartNotificationsSupportedResponse(developersApi.isSupported()));
  }

  @Override
  public CompletableFuture<ListUserOrganizationsResponse> listUserOrganizations(ListUserOrganizationsParams params) {
    var helper = buildSonarCloudNoOrgApiHelper(params.getCredentials());
    return CompletableFuture.supplyAsync(() -> {
      var serverOrganizations = new OrganizationApi(helper).listUserOrganizations(new ProgressMonitor(null));
      return new ListUserOrganizationsResponse(
        serverOrganizations.stream().map(o -> new OrganizationDto(o.getKey(), o.getName(), o.getDescription())).collect(Collectors.toList()));
    });
  }

  @Override
  public CompletableFuture<GetOrganizationResponse> getOrganization(GetOrganizationParams params) {
    var helper = buildSonarCloudNoOrgApiHelper(params.getCredentials());
    return CompletableFuture.supplyAsync(() -> {
      var serverOrganization = new OrganizationApi(helper).getOrganization(params.getOrganizationKey(), new ProgressMonitor(null));
      return new GetOrganizationResponse(serverOrganization.map(o -> new OrganizationDto(o.getKey(), o.getName(), o.getDescription())).orElse(null));
    });
  }

  @NotNull
  ServerApiHelper buildServerApiHelper(Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection) {
    var endpointParams = transientConnection.map(
      sq -> new EndpointParams(sq.getServerUrl(), false, null),
      sc -> new EndpointParams(SonarCloudConnectionConfiguration.getSonarCloudUrl(), true, sc.getOrganization()));
    var httpClient = getClientFor(transientConnection
      .map(TransientSonarQubeConnectionDto::getCredentials, TransientSonarCloudConnectionDto::getCredentials));
    return new ServerApiHelper(endpointParams, httpClient);
  }

  @NotNull
  ServerApiHelper buildSonarCloudNoOrgApiHelper(Either<TokenDto, UsernamePasswordDto> credentials) {
    var endpointParams = new EndpointParams(SonarCloudConnectionConfiguration.getSonarCloudUrl(), true, null);
    var httpClient = getClientFor(credentials);
    return new ServerApiHelper(endpointParams, httpClient);
  }

  private HttpClient getClientFor(Either<TokenDto, UsernamePasswordDto> credentials) {
    return credentials.map(
      tokenDto -> httpClientProvider.getHttpClientWithPreemptiveAuth(tokenDto.getToken(), null),
      userPass -> httpClientProvider.getHttpClientWithPreemptiveAuth(userPass.getUsername(), userPass.getPassword()));
  }

  @Override
  public CompletableFuture<HelpGenerateUserTokenResponse> helpGenerateUserToken(HelpGenerateUserTokenParams params) {
    return tokenGeneratorHelper.helpGenerateUserToken(params);
  }
}
