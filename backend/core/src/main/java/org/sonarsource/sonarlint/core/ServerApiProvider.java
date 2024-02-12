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

import java.util.Optional;
import javax.annotation.Nullable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

@Named
@Singleton
public class ServerApiProvider {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConnectionConfigurationRepository connectionRepository;
  private final ConnectionAwareHttpClientProvider awareHttpClientProvider;
  private final HttpClientProvider httpClientProvider;

  public ServerApiProvider(ConnectionConfigurationRepository connectionRepository,
    ConnectionAwareHttpClientProvider awareHttpClientProvider,
    HttpClientProvider httpClientProvider) {
    this.connectionRepository = connectionRepository;
    this.awareHttpClientProvider = awareHttpClientProvider;
    this.httpClientProvider = httpClientProvider;
  }

  public Optional<ServerApi> getServerApi(String connectionId) {
    var params = connectionRepository.getEndpointParams(connectionId);
    if (params.isEmpty()) {
      LOG.debug("Connection '{}' is gone", connectionId);
      return Optional.empty();
    }
    return Optional.of(new ServerApi(params.get(), awareHttpClientProvider.getHttpClient(connectionId)));
  }

  public ServerApi getServerApi(String baseUrl, @Nullable String organization, String token) {
    var params = new EndpointParams(baseUrl, SonarCloudConnectionConfiguration.getSonarCloudUrl().equals(baseUrl), organization);
    return new ServerApi(params, httpClientProvider.getHttpClientWithPreemptiveAuth(token));
  }

  public ServerApi getServerApiOrThrow(String connectionId) {
    var params = connectionRepository.getEndpointParams(connectionId);
    if (params.isEmpty()) {
      var error = new ResponseError(SonarLintRpcErrorCode.CONNECTION_NOT_FOUND, "Connection '" + connectionId + "' is gone", connectionId);
      throw new ResponseErrorException(error);
    }
    return new ServerApi(params.get(), awareHttpClientProvider.getHttpClient(connectionId));
  }

  /**
   * Used to do SonarCloud requests before knowing the organization
   */
  public ServerApi getForSonarCloudNoOrg(Either<TokenDto, UsernamePasswordDto> credentials) {
    var endpointParams = new EndpointParams(SonarCloudConnectionConfiguration.getSonarCloudUrl(), true, null);
    var httpClient = getClientFor(credentials);
    return new ServerApi(new ServerApiHelper(endpointParams, httpClient));
  }

  public ServerApi getForTransientConnection(Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection) {
    var endpointParams = transientConnection.map(
      sq -> new EndpointParams(sq.getServerUrl(), false, null),
      sc -> new EndpointParams(SonarCloudConnectionConfiguration.getSonarCloudUrl(), true, sc.getOrganization()));
    var httpClient = getClientFor(transientConnection
      .map(TransientSonarQubeConnectionDto::getCredentials, TransientSonarCloudConnectionDto::getCredentials));
    return new ServerApi(new ServerApiHelper(endpointParams, httpClient));
  }

  private HttpClient getClientFor(Either<TokenDto, UsernamePasswordDto> credentials) {
    return credentials.map(
      tokenDto -> httpClientProvider.getHttpClientWithPreemptiveAuth(tokenDto.getToken()),
      userPass -> httpClientProvider.getHttpClientWithPreemptiveAuth(userPass.getUsername(), userPass.getPassword()));
  }

}
