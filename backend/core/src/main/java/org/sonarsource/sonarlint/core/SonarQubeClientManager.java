/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.connection.SonarQubeClient;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionCredentialsChangedEvent;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.http.WebSocketClient;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.InvalidTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverconnection.ServerVersionAndStatusChecker;
import org.springframework.context.event.EventListener;

public class SonarQubeClientManager {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConnectionConfigurationRepository connectionRepository;
  private final HttpClientProvider httpClientProvider;
  private final SonarLintRpcClient client;
  private final SonarCloudActiveEnvironment sonarCloudActiveEnvironment;
  private final Map<String, Optional<SonarQubeClient>> clientsByConnectionId = new ConcurrentHashMap<>();

  public SonarQubeClientManager(ConnectionConfigurationRepository connectionRepository, HttpClientProvider httpClientProvider,
    SonarCloudActiveEnvironment sonarCloudActiveEnvironment, SonarLintRpcClient client) {
    this.connectionRepository = connectionRepository;
    this.httpClientProvider = httpClientProvider;
    this.client = client;
    this.sonarCloudActiveEnvironment = sonarCloudActiveEnvironment;
  }

  /**
   * Throws ResponseErrorException if connection with provided ID is not found in ConnectionConfigurationRepository
   */
  public SonarQubeClient getValidClientOrThrow(String connectionId) {
    return clientsByConnectionId.computeIfAbsent(connectionId, this::createSonarQubeClient)
      .orElseThrow(() -> new ResponseErrorException(new ResponseError(SonarLintRpcErrorCode.CONNECTION_NOT_FOUND, "Connection '" + connectionId + "' is not valid", connectionId)));
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
    return clientsByConnectionId.computeIfAbsent(connectionId, this::createSonarQubeClient)
      .filter(connection -> isConnectionActive(connectionId, connection));
  }

  private Optional<SonarQubeClient> createSonarQubeClient(String connectionId) {
    var connection = connectionRepository.getConnectionById(connectionId);
    if (connection == null) {
      LOG.debug("Connection '{}' is gone", connectionId);
      return Optional.empty();
    }
    var credentials = getValidCredentialsFromClient(connectionId);
    if (credentials.isEmpty()) {
      client.invalidToken(new InvalidTokenParams(connectionId));
      return Optional.empty();
    }
    var endpointParams = connection.getEndpointParams();
    var isBearerSupported = checkIfBearerIsSupported(endpointParams);
    var httpClient = credentials.get().map(
      tokenDto -> httpClientProvider.getHttpClientWithPreemptiveAuth(tokenDto.getToken(), isBearerSupported),
      userPass -> httpClientProvider.getHttpClientWithPreemptiveAuth(userPass.getUsername(), userPass.getPassword()));
    return Optional.of(new SonarQubeClient(connectionId, new ServerApi(endpointParams, httpClient), credentials.get(), client));
  }

  private static boolean isConnectionActive(String connectionId, SonarQubeClient connection) {
    var isValid = connection.isActive();
    if (!isValid) {
      LOG.debug("Connection '{}' is invalid", connectionId);
    }
    return isValid;
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

  public Optional<WebSocketClient> getValidWebSocketClient(String connectionId) {
    return getValidClient(connectionId)
      .map(validClient -> {
        var credentials = validClient.getCredentials();
        if (credentials.isRight()) {
          // We are normally only supporting tokens for SonarCloud connections
          throw new IllegalStateException("Expected token for connection " + connectionId);
        }
        return httpClientProvider.getWebSocketClient(credentials.getLeft().getToken());
      });
  }

  private boolean checkIfBearerIsSupported(EndpointParams params) {
    if (params.isSonarCloud()) {
      return true;
    }
    var cancelMonitor = new SonarLintCancelMonitor();
    var serverApi = new ServerApi(params, httpClientProvider.getHttpClientWithoutAuth());
    var status = serverApi.system().getStatus(cancelMonitor);
    var serverChecker = new ServerVersionAndStatusChecker(serverApi);
    return serverChecker.isSupportingBearer(status);
  }

  private Optional<Either<TokenDto, UsernamePasswordDto>> getValidCredentialsFromClient(String connectionId) {
    var response = client.getCredentials(new GetCredentialsParams(connectionId)).join();
    var credentials = response.getCredentials();
    return validateCredentials(connectionId, credentials);
  }

  private static Optional<Either<TokenDto, UsernamePasswordDto>> validateCredentials(String connectionId, @Nullable Either<TokenDto, UsernamePasswordDto> credentials) {
    if (credentials == null) {
      LOG.error("No credentials for connection " + connectionId);
      return Optional.empty();
    }
    if (credentials.isLeft()) {
      if (isNullOrEmpty(credentials.getLeft().getToken())) {
        LOG.error("No token for connection " + connectionId);
        return Optional.empty();
      }
      return Optional.of(credentials);
    }
    var right = credentials.getRight();
    if (right == null) {
      LOG.error("No username/password for connection " + connectionId);
      return Optional.empty();
    }
    if (isNullOrEmpty(right.getUsername())) {
      LOG.error("No username for connection " + connectionId);
      return Optional.empty();
    }
    if (isNullOrEmpty(right.getPassword())) {
      LOG.error("No password for connection " + connectionId);
      return Optional.empty();
    }
    return Optional.of(credentials);
  }

  private static boolean isNullOrEmpty(@Nullable String s) {
    return s == null || s.trim().isEmpty();
  }

  @EventListener
  public void onConnectionRemoved(ConnectionConfigurationRemovedEvent event) {
    clientsByConnectionId.remove(event.removedConnectionId());
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
