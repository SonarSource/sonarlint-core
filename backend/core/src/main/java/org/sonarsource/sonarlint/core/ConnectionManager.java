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
import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.connection.ServerConnection;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverconnection.ServerVersionAndStatusChecker;

public class ConnectionManager {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConnectionConfigurationRepository connectionRepository;
  private final ConnectionAwareHttpClientProvider awareHttpClientProvider;
  private final HttpClientProvider httpClientProvider;
  private final SonarLintRpcClient client;
  private final SonarCloudActiveEnvironment sonarCloudActiveEnvironment;
  private final Map<String, ServerConnection> connectionCache = new ConcurrentHashMap<>();

  public ConnectionManager(ConnectionConfigurationRepository connectionRepository, ConnectionAwareHttpClientProvider awareHttpClientProvider, HttpClientProvider httpClientProvider,
    SonarCloudActiveEnvironment sonarCloudActiveEnvironment, SonarLintRpcClient client) {
    this.connectionRepository = connectionRepository;
    this.awareHttpClientProvider = awareHttpClientProvider;
    this.httpClientProvider = httpClientProvider;
    this.client = client;
    this.sonarCloudActiveEnvironment = sonarCloudActiveEnvironment;
  }

  public Optional<ServerApi> getServerApiWithoutCredentials(String connectionId) {
    var params = connectionRepository.getEndpointParams(connectionId);
    if (params.isEmpty()) {
      LOG.debug("Connection '{}' is gone", connectionId);
      return Optional.empty();
    }
    return Optional.of(new ServerApi(params.get(), awareHttpClientProvider.getHttpClient()));
  }

  public Optional<ServerApi> getServerApi(String connectionId) {
    var params = connectionRepository.getEndpointParams(connectionId);
    if (params.isEmpty()) {
      LOG.debug("Connection '{}' is gone", connectionId);
      return Optional.empty();
    }
    var isBearerSupported = checkIfBearerIsSupported(params.get());
    return Optional.of(new ServerApi(params.get(), awareHttpClientProvider.getHttpClient(connectionId, isBearerSupported)));
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

  public ServerApi getServerApi(String baseUrl, @Nullable String organization, String token) {
    var isSonarCloud = sonarCloudActiveEnvironment.isSonarQubeCloud(baseUrl);

    var params = new EndpointParams(baseUrl, baseUrl, isSonarCloud, organization);
    var isBearerSupported = checkIfBearerIsSupported(params);
    return new ServerApi(params, httpClientProvider.getHttpClientWithPreemptiveAuth(token, isBearerSupported));
  }

  private ServerApi getServerApiOrThrow(String connectionId) {
    var params = connectionRepository.getEndpointParams(connectionId);
    if (params.isEmpty()) {
      var error = new ResponseError(SonarLintRpcErrorCode.CONNECTION_NOT_FOUND, "Connection '" + connectionId + "' is gone", connectionId);
      throw new ResponseErrorException(error);
    }
    var isBearerSupported = checkIfBearerIsSupported(params.get());
    return new ServerApi(params.get(), awareHttpClientProvider.getHttpClient(connectionId, isBearerSupported));
  }

  /**
   * Used to do SonarCloud requests before knowing the organization
   */
  public ServerApi getForSonarCloudNoOrg(Either<TokenDto, UsernamePasswordDto> credentials, SonarCloudRegion region) {
    var endpointParams = new EndpointParams(sonarCloudActiveEnvironment.getUri(region).toString(), sonarCloudActiveEnvironment.getApiUri(region).toString(), true, null);
    var httpClient = getClientFor(endpointParams, credentials);
    return new ServerApi(new ServerApiHelper(endpointParams, httpClient));
  }

  public ServerApi getForTransientConnection(Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection) {
    var endpointParams = transientConnection.map(
      sq -> new EndpointParams(sq.getServerUrl(), null, false, null),
      sc -> {
        var region = SonarCloudRegion.valueOf(sc.getRegion().toString());
        return new EndpointParams(sonarCloudActiveEnvironment.getUri(region).toString(), sonarCloudActiveEnvironment.getApiUri(region).toString(), true, sc.getOrganization());
      });
    var httpClient = getClientFor(endpointParams, transientConnection
      .map(TransientSonarQubeConnectionDto::getCredentials, TransientSonarCloudConnectionDto::getCredentials));
    return new ServerApi(new ServerApiHelper(endpointParams, httpClient));
  }

  private HttpClient getClientFor(EndpointParams params, Either<TokenDto, UsernamePasswordDto> credentials) {
    return credentials.map(
      tokenDto -> {
        var isBearerSupported = checkIfBearerIsSupported(params);
        return httpClientProvider.getHttpClientWithPreemptiveAuth(tokenDto.getToken(), isBearerSupported);
      },
      userPass -> httpClientProvider.getHttpClientWithPreemptiveAuth(userPass.getUsername(), userPass.getPassword()));
  }

  /**
   * Throws ResponseErrorException if connection with provided ID is not found in ConnectionConfigurationRepository
   */
  public ServerConnection getConnectionOrThrow(String connectionId) {
    return connectionCache.computeIfAbsent(connectionId, connId -> {
      var serverApi = getServerApiOrThrow(connId);
      return new ServerConnection(connId, serverApi, client);
    });
  }

  /**
   * Returns empty Optional if connection with provided ID is not found in ConnectionConfigurationRepository
   */
  public Optional<ServerConnection> tryGetConnection(String connectionId) {
    return Optional.ofNullable(connectionCache.computeIfAbsent(connectionId, connId ->
      getServerApi(connId).map(serverApi -> new ServerConnection(connId, serverApi, client)).orElse(null)));
  }

  /**
   * Should be used for WebAPI requests without an authentication
   */
  public Optional<ServerConnection> tryGetConnectionWithoutCredentials(String connectionId) {
    return getServerApiWithoutCredentials(connectionId)
      .map(serverApi -> new ServerConnection(connectionId, serverApi, client, true));
  }

  public void withValidConnection(String connectionId, Consumer<ServerApi> serverApiConsumer) {
    getValidConnection(connectionId).ifPresent(connection -> connection.withClientApi(serverApiConsumer));
  }

  public <T> Optional<T> withValidConnectionAndReturn(String connectionId, Function<ServerApi, T> serverApiConsumer) {
    return getValidConnection(connectionId).map(connection -> connection.withClientApiAndReturn(serverApiConsumer));
  }

  public <T> Optional<T> withValidConnectionFlatMapOptionalAndReturn(String connectionId, Function<ServerApi, Optional<T>> serverApiConsumer) {
    return getValidConnection(connectionId).map(connection -> connection.withClientApiAndReturn(serverApiConsumer)).flatMap(Function.identity());
  }

  private Optional<ServerConnection> getValidConnection(String connectionId) {
    return tryGetConnection(connectionId).filter(ServerConnection::isValid)
      .or(() -> {
        LOG.debug("Connection '{}' is invalid", connectionId);
        return Optional.empty();
      });
  }
}
