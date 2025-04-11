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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.serverapi.exception.UnauthorizedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SonarQubeClientManagerTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private final ConnectionConfigurationRepository connectionRepository = mock(ConnectionConfigurationRepository.class);
  private final ConnectionAwareHttpClientProvider awareHttpClientProvider = mock(ConnectionAwareHttpClientProvider.class);
  private final HttpClientProvider httpClientProvider = mock(HttpClientProvider.class);
  private SonarLintRpcClient client;
  private SonarQubeClientManager underTest;

  @BeforeEach
  void setUp() {
    client = mock(SonarLintRpcClient.class);
    underTest = new SonarQubeClientManager(connectionRepository, awareHttpClientProvider, httpClientProvider,
      SonarCloudActiveEnvironment.prod(), client);
  }

  @Test
  void getServerApi_for_sonarqube() {
    when(connectionRepository.getConnectionById("sq1")).thenReturn(new SonarQubeConnectionConfiguration("sq1", "serverUrl", true));
    var httpClient = mock(HttpClient.class);
    when(awareHttpClientProvider.getHttpClient("sq1", false)).thenReturn(httpClient);
    when(awareHttpClientProvider.getHttpClient()).thenReturn(httpClient);
    var httpResponse = mock(HttpClient.Response.class);
    when(httpResponse.isSuccessful()).thenReturn(true);
    when(httpResponse.bodyAsString()).thenReturn("{\"id\": \"20160308094653\",\"version\": \"9.9\",\"status\": \"UP\"}");
    when(httpClient.getAsyncAnonymous("serverUrl/api/system/status")).thenReturn(CompletableFuture.completedFuture(httpResponse));

    var connection = underTest.getClientOrThrow("sq1");

    assertThat(connection.isActive()).isTrue();
  }

  @Test
  void getServerApi_for_sonarcloud() {
    when(connectionRepository.getConnectionById("sc1")).thenReturn(new SonarCloudConnectionConfiguration(SonarCloudRegion.EU.getProductionUri(), SonarCloudRegion.EU.getApiProductionUri(), "sc1", "organizationKey", SonarCloudRegion.EU,
      false));
    var httpClient = mock(HttpClient.class);
    when(awareHttpClientProvider.getHttpClient("sc1", true)).thenReturn(httpClient);
    when(awareHttpClientProvider.getHttpClient()).thenReturn(httpClient);
    var httpResponse = mock(HttpClient.Response.class);
    when(httpResponse.isSuccessful()).thenReturn(true);
    when(httpResponse.bodyAsString()).thenReturn("{\"id\": \"20160308094653\",\"version\": \"9.9\",\"status\": \"UP\"}");
    when(httpClient.getAsyncAnonymous("/api/system/status")).thenReturn(CompletableFuture.completedFuture(httpResponse));

    var connection = underTest.getClientOrThrow("sc1");

    assertThat(connection.isActive()).isTrue();
  }

  @Test
  void getServerApi_for_sonarcloud_with_trailing_slash_notConnected() {
    when(connectionRepository.getConnectionById("sc1")).thenReturn(new SonarCloudConnectionConfiguration(URI.create(SonarCloudRegion.EU.getProductionUri().toString() + "/"), SonarCloudRegion.EU.getApiProductionUri(), "sc1", "organizationKey", SonarCloudRegion.EU,
      false));
    var httpClient = mock(HttpClient.class);
    when(awareHttpClientProvider.getHttpClient("sc1", true)).thenReturn(httpClient);
    when(awareHttpClientProvider.getHttpClient()).thenReturn(httpClient);
    var httpResponse = mock(HttpClient.Response.class);
    when(httpResponse.isSuccessful()).thenReturn(true);
    when(httpResponse.bodyAsString()).thenReturn("{\"id\": \"20160308094653\",\"version\": \"9.9\",\"status\": \"UP\"}");
    when(httpClient.getAsyncAnonymous("/api/system/status")).thenReturn(CompletableFuture.completedFuture(httpResponse));

    var connection = underTest.getClientOrThrow("sc1");

    assertThat(connection.isActive()).isTrue();
  }

  @Test
  void getServerApi_returns_empty_if_connection_doesnt_exists() {
    when(connectionRepository.getConnectionById("sc1")).thenReturn(null);
    var httpClient = mock(HttpClient.class);
    when(awareHttpClientProvider.getHttpClient("sc1", true)).thenReturn(httpClient);

    var throwable = catchThrowable(() -> underTest.getClientOrThrow("sc1"));

    assertThat(throwable.getMessage()).isEqualTo("Connection 'sc1' is gone");
  }

  @Test
  void should_log_invalid_connection_and_notify_client() {
    var connectionId = "connectionId";
    when(connectionRepository.getConnectionById(connectionId))
      .thenReturn(new SonarCloudConnectionConfiguration(URI.create("http://server1"), URI.create("http://server1"), connectionId, "myorg", SonarCloudRegion.EU, true));

    // switch connection to invalid state
    underTest.withActiveClient(connectionId, api -> {
      throw new UnauthorizedException("401");
    });
    // attempt to get connection
    var calledTheSecondTime = new AtomicBoolean();
    underTest.withActiveClient(connectionId, api -> {
      calledTheSecondTime.set(true);
    });

    assertThat(logTester.logs()).contains("Connection 'connectionId' is invalid");
    assertThat(calledTheSecondTime).isFalse();
    verify(client, times(1)).invalidToken(any());
  }
}
