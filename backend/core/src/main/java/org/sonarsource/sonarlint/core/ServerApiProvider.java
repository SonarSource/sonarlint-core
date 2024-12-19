/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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

import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.connection.ConnectionManager;
import org.sonarsource.sonarlint.core.connection.ServerConnection;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
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
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.LastWebApiErrorNotificationService;

import static org.apache.commons.lang.StringUtils.removeEnd;

@Named
@Singleton
public class ServerApiProvider implements ConnectionManager {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConnectionConfigurationRepository connectionRepository;
  private final ConnectionAwareHttpClientProvider awareHttpClientProvider;
  private final HttpClientProvider httpClientProvider;
  private final StorageService storageService;
  private final SonarLintRpcClient client;
  private final URI sonarCloudUri;

  public ServerApiProvider(ConnectionConfigurationRepository connectionRepository, ConnectionAwareHttpClientProvider awareHttpClientProvider, HttpClientProvider httpClientProvider,
    SonarCloudActiveEnvironment sonarCloudActiveEnvironment, StorageService storageService, SonarLintRpcClient client) {
    this.connectionRepository = connectionRepository;
    this.awareHttpClientProvider = awareHttpClientProvider;
    this.httpClientProvider = httpClientProvider;
    this.storageService = storageService;
    this.client = client;
    this.sonarCloudUri = sonarCloudActiveEnvironment.getUri();
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
    var params = new EndpointParams(baseUrl, removeEnd(sonarCloudUri.toString(), "/").equals(removeEnd(baseUrl, "/")), organization);
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
  public ServerApi getForSonarCloudNoOrg(Either<TokenDto, UsernamePasswordDto> credentials) {
    var endpointParams = new EndpointParams(sonarCloudUri.toString(), true, null);
    var httpClient = getClientFor(endpointParams, credentials);
    return new ServerApi(new ServerApiHelper(endpointParams, httpClient));
  }

  public ServerApi getForTransientConnection(Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection) {
    var endpointParams = transientConnection.map(
      sq -> new EndpointParams(sq.getServerUrl(), false, null),
      sc -> new EndpointParams(sonarCloudUri.toString(), true, sc.getOrganization()));
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

  @Override
  public ServerConnection getConnectionOrThrow(String connectionId) {
    var serverApi = getServerApiOrThrow(connectionId);
    return new ServerConnection(connectionId, serverApi, new LastWebApiErrorNotificationService(storageService), client);
  }

  @Override
  public Optional<ServerConnection> tryGetConnection(String connectionId) {
    return getServerApi(connectionId)
      .map(serverApi -> new ServerConnection(connectionId, serverApi, new LastWebApiErrorNotificationService(storageService), client));
  }

  public Optional<ServerConnection> tryGetConnectionWithoutCredentials(String connectionId) {
    return getServerApiWithoutCredentials(connectionId)
      .map(serverApi -> new ServerConnection(connectionId, serverApi, new LastWebApiErrorNotificationService(storageService), client));
  }

  @Override
  public ServerApi getTransientConnection(String token,@Nullable String organization,  String baseUrl) {
    return getServerApi(baseUrl, organization, token);
  }

  @Override
  public Optional<ServerConnection> getValidConnection(String connectionId) {
    return tryGetConnection(connectionId).filter(ServerConnection::isValid);
  }

  @Override
  public void withValidConnection(String connectionId, Consumer<ServerConnection> serverConnectionCall) {
    // wrap the consumer call which is web API call and handle the connection state to avoid spamming the server with notifications
    getValidConnection(connectionId).ifPresent(serverConnectionCall);
  }

  @Override
  public <T> Optional<T> withValidConnectionAndReturn(String connectionId, Function<ServerConnection, Optional<T>> serverConnectionCall) {
    return getValidConnection(connectionId).flatMap(serverConnectionCall);
  }
}
