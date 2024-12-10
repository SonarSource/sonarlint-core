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

import com.google.common.annotations.VisibleForTesting;
import java.net.URI;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.InvalidTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiErrorHandlingWrapper;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.ServerInfoSynchronizer;

import static org.apache.commons.lang.StringUtils.removeEnd;

@Named
@Singleton
public class ConnectionManager {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConnectionConfigurationRepository connectionRepository;
  private final ConnectionAwareHttpClientProvider awareHttpClientProvider;
  private final HttpClientProvider httpClientProvider;
  private final URI sonarCloudUri;
  private final SonarLintRpcClient client;

  public ConnectionManager(ConnectionConfigurationRepository connectionRepository, ConnectionAwareHttpClientProvider awareHttpClientProvider,
    HttpClientProvider httpClientProvider, SonarCloudActiveEnvironment sonarCloudActiveEnvironment, SonarLintRpcClient client) {
    this.connectionRepository = connectionRepository;
    this.awareHttpClientProvider = awareHttpClientProvider;
    this.httpClientProvider = httpClientProvider;
    this.sonarCloudUri = sonarCloudActiveEnvironment.getUri();
    this.client = client;
  }

  public boolean hasConnection(String connectionId) {
    return getServerApi(connectionId).isPresent();
  }

  public Optional<ServerApiErrorHandlingWrapper> tryGetServerApiWrapper(String connectionId) {
    return getServerApi(connectionId).map( serverApi -> wrapWithConnection(serverApi, connectionId));
  }

  public ServerApiErrorHandlingWrapper getServerApiWrapper(String baseUrl, @Nullable String organization, String token) {
    return wrap(getServerApi(baseUrl, organization, token));
  }

  @VisibleForTesting
  Optional<ServerApi> getServerApi(String connectionId) {
    var params = connectionRepository.getEndpointParams(connectionId);
    if (params.isEmpty()) {
      LOG.debug("Connection '{}' is gone", connectionId);
      return Optional.empty();
    }
    return Optional.of(new ServerApi(params.get(), awareHttpClientProvider.getHttpClient(connectionId)));
  }

  @VisibleForTesting
  ServerApi getServerApi(String baseUrl, @Nullable String organization, String token) {
    var params = new EndpointParams(baseUrl, removeEnd(sonarCloudUri.toString(), "/").equals(removeEnd(baseUrl, "/")), organization);
    return new ServerApi(params, httpClientProvider.getHttpClientWithPreemptiveAuth(token));
  }

  /**
   * Used to do SonarCloud requests before knowing the organization
   */
  public ServerApiErrorHandlingWrapper getForSonarCloudNoOrg(Either<TokenDto, UsernamePasswordDto> credentials) {
    var endpointParams = new EndpointParams(sonarCloudUri.toString(), true, null);
    var httpClient = getClientFor(credentials);
    return wrap(new ServerApi(new ServerApiHelper(endpointParams, httpClient)));
  }

  public ServerApiErrorHandlingWrapper getForTransientConnection(Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection) {
    var endpointParams = transientConnection.map(
      sq -> new EndpointParams(sq.getServerUrl(), false, null),
      sc -> new EndpointParams(sonarCloudUri.toString(), true, sc.getOrganization()));
    var httpClient = getClientFor(transientConnection
      .map(TransientSonarQubeConnectionDto::getCredentials, TransientSonarCloudConnectionDto::getCredentials));
    return wrap(new ServerApi(new ServerApiHelper(endpointParams, httpClient)));
  }

  private HttpClient getClientFor(Either<TokenDto, UsernamePasswordDto> credentials) {
    return credentials.map(
      tokenDto -> httpClientProvider.getHttpClientWithPreemptiveAuth(tokenDto.getToken()),
      userPass -> httpClientProvider.getHttpClientWithPreemptiveAuth(userPass.getUsername(), userPass.getPassword()));
  }

  private void notifyClientAboutWrongToken(@Nullable String connectionId) {
    client.invalidToken(new InvalidTokenParams(connectionId));
  }

  private ServerApiErrorHandlingWrapper wrap(ServerApi serverApi) {
    return new ServerApiErrorHandlingWrapper(serverApi, () -> notifyClientAboutWrongToken(null));
  }

  private ServerApiErrorHandlingWrapper wrapWithConnection(ServerApi serverApi, String connectionId) {
    return new ServerApiErrorHandlingWrapper(serverApi, () -> notifyClientAboutWrongToken(connectionId));
  }

  public boolean isSonarCloud(String connectionId) {
    return getServerApiWrapperOrThrow(connectionId).isSonarCloud();
  }

  public Version getSonarServerVersion(String connectionId, ConnectionStorage storage, SonarLintCancelMonitor cancelMonitor) {
    var serverApiWrapper = getServerApiWrapperOrThrow(connectionId);
    var serverInfoSynchronizer = new ServerInfoSynchronizer(storage);
    return serverInfoSynchronizer.readOrSynchronizeServerInfo(serverApiWrapper, cancelMonitor).getVersion();
  }

  public void throwIfNoConnection(String connectionId) {
    if (getServerApi(connectionId).isEmpty()) {
      throw unknownConnection(connectionId);
    }
  }

  public ServerApiErrorHandlingWrapper getServerApiWrapperOrThrow(String connectionId) {
    return wrapWithConnection(getServerApiOrThrow(connectionId), connectionId);
  }

  private static ResponseErrorException unknownConnection(String connectionId) {
    var error = new ResponseError(SonarLintRpcErrorCode.CONNECTION_NOT_FOUND, "Connection with ID '" + connectionId + "' does not exist", connectionId);
    return new ResponseErrorException(error);
  }

  private ServerApi getServerApiOrThrow(String connectionId) {
    var params = connectionRepository.getEndpointParams(connectionId);
    if (params.isEmpty()) {
      throw unknownConnection(connectionId);
    }
    return new ServerApi(params.get(), awareHttpClientProvider.getHttpClient(connectionId));
  }
}
