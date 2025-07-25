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
package org.sonarsource.sonarlint.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.connection.SonarQubeClient;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionCredentialsChangedEvent;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverconnection.ServerVersionAndStatusChecker;
import org.springframework.context.event.EventListener;

public class SonarQubeClientManager {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConnectionConfigurationRepository connectionRepository;
  private final ConnectionAwareHttpClientProvider awareHttpClientProvider;
  private final HttpClientProvider httpClientProvider;
  private final SonarLintRpcClient client;
  private final SonarCloudActiveEnvironment sonarCloudActiveEnvironment;
  private final Map<String, SonarQubeClient> clientsByConnectionId = new ConcurrentHashMap<>();

  public SonarQubeClientManager(ConnectionConfigurationRepository connectionRepository, ConnectionAwareHttpClientProvider awareHttpClientProvider,
    HttpClientProvider httpClientProvider, SonarCloudActiveEnvironment sonarCloudActiveEnvironment, SonarLintRpcClient client) {
    this.connectionRepository = connectionRepository;
    this.awareHttpClientProvider = awareHttpClientProvider;
    this.httpClientProvider = httpClientProvider;
    this.client = client;
    this.sonarCloudActiveEnvironment = sonarCloudActiveEnvironment;
  }

  /**
   * Throws ResponseErrorException if connection with provided ID is not found in ConnectionConfigurationRepository
   */
  public SonarQubeClient getClientOrThrow(String connectionId) {
    return clientsByConnectionId.computeIfAbsent(connectionId, connId ->
      Optional.ofNullable(getSonarQubeClient(connId))
        .orElseThrow(() -> new ResponseErrorException(new ResponseError(SonarLintRpcErrorCode.CONNECTION_NOT_FOUND, "Connection '" + connectionId + "' is gone", connectionId))));
  }

  public void withActiveClient(String connectionId, Consumer<ServerApi> serverApiConsumer) {
    getValidClient(connectionId).ifPresent(connection -> connection.withClientApi(serverApiConsumer));
  }

  public <T> Optional<T> withActiveClientAndReturn(String connectionId, Function<ServerApi, T> serverApiConsumer) {
    return getValidClient(connectionId).map(connection -> connection.withClientApiAndReturn(serverApiConsumer));
  }

  public <T> Optional<T> withActiveClientFlatMapOptionalAndReturn(String connectionId, Function<ServerApi, Optional<T>> serverApiConsumer) {
    return getValidClient(connectionId).map(connection -> connection.withClientApiAndReturn(serverApiConsumer)).flatMap(Function.identity());
  }

  private Optional<SonarQubeClient> getValidClient(String connectionId) {
    return Optional.ofNullable(clientsByConnectionId.computeIfAbsent(connectionId, this::getSonarQubeClient))
      .filter(connection -> isConnectionActive(connectionId, connection));
  }

  @Nullable
  private SonarQubeClient getSonarQubeClient(String connectionId) {
    var connection = connectionRepository.getConnectionById(connectionId);
    if (connection == null) {
      LOG.debug("Connection '{}' is gone", connectionId);
      return null;
    }
    var endpointParams = connection.getEndpointParams();
    var isBearerSupported = checkIfBearerIsSupported(endpointParams);
    var serverApi = getServerApi(connectionId, endpointParams, isBearerSupported);
    return new SonarQubeClient(connectionId, serverApi, client);
  }

  private static boolean isConnectionActive(String connectionId, SonarQubeClient connection) {
    var isValid = connection.isActive();
    if (!isValid) {
      LOG.debug("Connection '{}' is invalid", connectionId);
    }
    return isValid;
  }

  @Nullable
  private ServerApi getServerApi(String connectionId, EndpointParams endpointParams, boolean isBearerSupported) {
    try {
      return new ServerApi(endpointParams, awareHttpClientProvider.getHttpClient(connectionId, isBearerSupported));
    } catch (IllegalStateException e) {
      return null;
    }
  }

  public ServerApi getForTransientConnection(Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection) {
    var endpointParams = transientConnection.map(
      sq -> new EndpointParams(sq.getServerUrl(), null, false, null),
      sc -> {
        var region = SonarCloudRegion.valueOf(sc.getRegion().toString());
        return new EndpointParams(sonarCloudActiveEnvironment.getUri(region).toString(), sonarCloudActiveEnvironment.getApiUri(region).toString(), true, sc.getOrganization());
      });
    var httpClient = transientConnection
      .map(TransientSonarQubeConnectionDto::getCredentials, TransientSonarCloudConnectionDto::getCredentials)
      .map(
        tokenDto -> {
          var isBearerSupported = checkIfBearerIsSupported(endpointParams);
          return httpClientProvider.getHttpClientWithPreemptiveAuth(tokenDto.getToken(), isBearerSupported);
        },
        userPass -> httpClientProvider.getHttpClientWithPreemptiveAuth(userPass.getUsername(), userPass.getPassword()));
    return new ServerApi(new ServerApiHelper(endpointParams, httpClient));
  }

  private boolean checkIfBearerIsSupported(EndpointParams params) {
    if (params.isSonarCloud()) {
      return true;
    }
    var httpClient = awareHttpClientProvider.getHttpClient();
    var cancelMonitor = new SonarLintCancelMonitor();
    var serverApi = new ServerApi(params, httpClient);
    var status = serverApi.system().getStatus(cancelMonitor);
    var serverChecker = new ServerVersionAndStatusChecker(serverApi);
    return serverChecker.isSupportingBearer(status);
  }

  @EventListener
  public void onConnectionRemoved(ConnectionConfigurationRemovedEvent event) {
    clientsByConnectionId.remove(event.getRemovedConnectionId());
  }

  @EventListener
  public void onConnectionUpdated(ConnectionConfigurationUpdatedEvent event) {
    clientsByConnectionId.remove(event.updatedConnectionId());
  }

  @EventListener
  public void onCredentialsChanged(ConnectionCredentialsChangedEvent event) {
    clientsByConnectionId.remove(event.getConnectionId());
  }
}
