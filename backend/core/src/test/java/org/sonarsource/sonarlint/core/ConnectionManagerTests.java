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

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.connection.ServerConnection;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.exception.UnauthorizedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class ConnectionManagerTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private final ConnectionConfigurationRepository connectionRepository = mock(ConnectionConfigurationRepository.class);
  private final ConnectionAwareHttpClientProvider awareHttpClientProvider = mock(ConnectionAwareHttpClientProvider.class);
  private final HttpClientProvider httpClientProvider = mock(HttpClientProvider.class);
  private final SonarLintRpcClient client = mock(SonarLintRpcClient.class);
  private final ConnectionManager underTest = new ConnectionManager(connectionRepository, awareHttpClientProvider, httpClientProvider,
    SonarCloudActiveEnvironment.prodEu(), client);

  @Test
  void getServerApi_for_sonarqube() {
    var endpointParams = mock(EndpointParams.class);
    when(endpointParams.getBaseUrl()).thenReturn("");
    when(connectionRepository.getEndpointParams("sq1")).thenReturn(Optional.of(endpointParams));
    var httpClient = mock(HttpClient.class);
    when(awareHttpClientProvider.getHttpClient("sq1", false)).thenReturn(httpClient);
    when(awareHttpClientProvider.getHttpClient()).thenReturn(httpClient);
    var httpResponse = mock(HttpClient.Response.class);
    when(httpResponse.isSuccessful()).thenReturn(true);
    when(httpResponse.bodyAsString()).thenReturn("{\"id\": \"20160308094653\",\"version\": \"9.9\",\"status\": \"UP\"}");
    when(httpClient.getAsync("/api/system/status")).thenReturn(CompletableFuture.completedFuture(httpResponse));

    var serverApi = underTest.getServerApi("sq1");

    assertThat(serverApi).isPresent();
  }

  @Test
  void getServerApi_for_sonarqube_notConnected() {
    var httpClient = mock(HttpClient.class);
    when(httpClientProvider.getHttpClientWithPreemptiveAuth("token", false)).thenReturn(httpClient);
    when(awareHttpClientProvider.getHttpClient()).thenReturn(httpClient);
    var httpResponse = mock(HttpClient.Response.class);
    when(httpResponse.isSuccessful()).thenReturn(true);
    when(httpResponse.bodyAsString()).thenReturn("{\"id\": \"20160308094653\",\"version\": \"10.0\",\"status\": \"UP\"}");
    when(httpClient.getAsync("sq_notConnected/api/system/status")).thenReturn(CompletableFuture.completedFuture(httpResponse));

    var serverApi = underTest.getServerApi("sq_notConnected", null, "token");
    assertThat(serverApi.isSonarCloud()).isFalse();
  }

  @Test
  void getServerApi_for_sonarcloud() {
    var endpointParams = mock(EndpointParams.class);
    when(endpointParams.getBaseUrl()).thenReturn("");
    when(connectionRepository.getEndpointParams("sc1")).thenReturn(Optional.of(endpointParams));
    var httpClient = mock(HttpClient.class);
    when(awareHttpClientProvider.getHttpClient("sc1", true)).thenReturn(httpClient);
    when(awareHttpClientProvider.getHttpClient()).thenReturn(httpClient);
    var httpResponse = mock(HttpClient.Response.class);
    when(httpResponse.isSuccessful()).thenReturn(true);
    when(httpResponse.bodyAsString()).thenReturn("{\"id\": \"20160308094653\",\"version\": \"9.9\",\"status\": \"UP\"}");
    when(httpClient.getAsync("/api/system/status")).thenReturn(CompletableFuture.completedFuture(httpResponse));

    var serverApi = underTest.getServerApi("sc1");

    assertThat(serverApi).isPresent();
  }

  @Test
  void getServerApi_for_sonarcloud_with_trailing_slash_notConnected() {
    var httpClient = mock(HttpClient.class);
    when(httpClientProvider.getHttpClientWithPreemptiveAuth("token", true)).thenReturn(httpClient);

    var serverApi = underTest.getServerApi("https://sonarcloud.io/", "organization", "token");
    assertThat(serverApi.isSonarCloud()).isTrue();
  }

  @Test
  void getServerApi_for_sonarcloud_notConnected() {
    var httpClient = mock(HttpClient.class);
    when(httpClientProvider.getHttpClientWithPreemptiveAuth("token", true)).thenReturn(httpClient);

    var serverApi = underTest.getServerApi("https://sonarcloud.io", "organization", "token");
    assertThat(serverApi.isSonarCloud()).isTrue();
  }

  @Test
  void getServerApi_returns_empty_if_connection_doesnt_exists() {
    when(connectionRepository.getConnectionById("sc1")).thenReturn(null);
    var httpClient = mock(HttpClient.class);
    when(awareHttpClientProvider.getHttpClient("sc1", true)).thenReturn(httpClient);

    var serverApi = underTest.getServerApi("sc1");

    assertThat(serverApi).isEmpty();
  }

  @Test
  void getServerApi_returns_empty_if_client_cant_provide_httpclient() {
    when(connectionRepository.getConnectionById("sc1")).thenReturn(new SonarCloudConnectionConfiguration(URI.create("http://server1"), "sc1", "myorg", "EU", true));
    when(awareHttpClientProvider.getHttpClient("sc1", true)).thenReturn(null);

    var serverApi = underTest.getServerApi("sc1");

    assertThat(serverApi).isEmpty();
  }

  @Test
  void should_log_invalid_connection() {
    var connectionId = "connectionId";
    ConnectionManager spy = spy(underTest);
    var serverApi = mock(ServerApi.class);
    var serverConnection = new ServerConnection(connectionId, serverApi, client);
    doReturn(Optional.of(serverConnection)).when(spy).tryGetConnection(connectionId);

    // switch connection to invalid state
    spy.withValidConnection(connectionId, api -> {
      throw new UnauthorizedException("401");
    });
    // attempt to get connection
    spy.withValidConnection(connectionId, api -> {});

    assertThat(logTester.logs()).contains("Connection 'connectionId' is invalid");
  }
}
