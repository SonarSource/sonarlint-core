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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
  private static final String API_SYSTEM_STATUS = "/api/system/status";

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
  void getClientOrThrow_for_sonarqube() {
    setupServerConnection("sqs1", "serverUrl");

    var connection = underTest.getClientOrThrow("sqs1");

    assertThat(connection.isActive()).isTrue();
  }

  @Test
  void getClientOrThrow_for_sonarcloud() {
    setupCloudConnection("sqc1", SonarCloudRegion.EU.getProductionUri(), SonarCloudRegion.EU.getApiProductionUri());

    var connection = underTest.getClientOrThrow("sqc1");

    assertThat(connection.isActive()).isTrue();
  }

  @Test
  void getClientOrThrow_for_sonarcloud_with_trailing_slash_notConnected() {
    URI uriWithSlash = URI.create(SonarCloudRegion.EU.getProductionUri() + "/");
    setupCloudConnection("sqc-with-slash", uriWithSlash, SonarCloudRegion.EU.getApiProductionUri());

    var connection = underTest.getClientOrThrow("sqc-with-slash");

    assertThat(connection.isActive()).isTrue();
  }

  @Test
  void getClientOrThrow_should_throw_if_connection_doesnt_exists() {
    var throwable = catchThrowable(() -> underTest.getClientOrThrow("sqc1"));

    assertThat(throwable.getMessage()).isEqualTo("Connection 'sqc1' is gone");
  }

  @Test
  void withActiveClient_should_execute_consumer_when_valid_client_exists() {
    setupServerConnection("sqs1", "serverUrl");
    var consumerExecuted = new AtomicBoolean(false);

    underTest.withActiveClient("sqs1", api -> consumerExecuted.set(true));

    assertThat(consumerExecuted.get()).isTrue();
  }

  @Test
  void withActiveClient_should_not_execute_consumer_when_connection_not_found() {
    when(connectionRepository.getConnectionById("nonexistent")).thenReturn(null);
    var consumerExecuted = new AtomicBoolean(false);

    underTest.withActiveClient("nonexistent", api -> consumerExecuted.set(true));

    assertThat(consumerExecuted.get()).isFalse();
    assertThat(logTester.logs()).contains("Connection 'nonexistent' is gone");
  }

  @Test
  void withActiveClient_should_not_execute_consumer_and_notify_user_when_client_becomes_inactive() {
    setupServerConnection("sqs1", "serverUrl");
    var consumerExecuted = new AtomicBoolean(false);

    underTest.withActiveClient("sqs1", api -> { throw new UnauthorizedException("401"); });
    underTest.withActiveClient("sqs1", api -> consumerExecuted.set(true));

    assertThat(consumerExecuted.get()).isFalse();
    assertThat(logTester.logs()).contains("Connection 'sqs1' is invalid");
    verify(client, times(1)).invalidToken(any());
  }

  @Test
  void withActiveClient_should_cache_clients_and_reuse_them() {
    setupServerConnection("sqs1", "serverUrl");
    var executionCount = new AtomicInteger(0);

    underTest.withActiveClient("sqs1", api -> executionCount.incrementAndGet());
    underTest.withActiveClient("sqs1", api -> executionCount.incrementAndGet());
    underTest.withActiveClient("sqs1", api -> executionCount.incrementAndGet());

    assertThat(executionCount.get()).isEqualTo(3);
    verify(awareHttpClientProvider, times(1)).getHttpClient("sqs1", false);
  }

  @Test
  void withActiveClientAndReturn_should_return_value_when_valid_client_exists() {
    setupServerConnection("sqs1", "serverUrl");

    var result = underTest.withActiveClientAndReturn("sqs1", api -> "test-result");

    assertThat(result).isPresent().get().isEqualTo("test-result");
  }

  @Test
  void withActiveClientAndReturn_should_return_empty_when_connection_not_found() {
    when(connectionRepository.getConnectionById("nonexistent")).thenReturn(null);
    var result = underTest.withActiveClientAndReturn("nonexistent", api -> "test-result");
    assertThat(result).isEmpty();
  }

  @Test
  void withActiveClientAndReturn_should_return_empty_when_client_inactive() {
    setupServerConnection("sqs1", "serverUrl");

    underTest.withActiveClient("sqs1", api -> { throw new UnauthorizedException("401"); });

    var result = underTest.withActiveClientAndReturn("sq1", api -> "test-result");
    assertThat(result).isEmpty();
  }

  @Test
  void withActiveClientFlatMapOptionalAndReturn_should_return_optional_when_valid_client_exists() {
    setupServerConnection("sqs1", "serverUrl");

    var result = underTest.withActiveClientFlatMapOptionalAndReturn("sqs1", api -> Optional.of("test-result"));

    assertThat(result).isPresent().get().isEqualTo("test-result");
  }

  @Test
  void withActiveClientFlatMapOptionalAndReturn_should_return_empty_when_function_returns_empty() {
    setupServerConnection("sqs1", "serverUrl");

    var result = underTest.withActiveClientFlatMapOptionalAndReturn("sqs1", api -> Optional.empty());

    assertThat(result).isEmpty();
  }

  @Test
  void withActiveClientFlatMapOptionalAndReturn_should_return_empty_when_connection_not_found() {
    var result = underTest.withActiveClientFlatMapOptionalAndReturn("nonexistent", api -> Optional.of("test-result"));

    assertThat(result).isEmpty();
  }

  @Test
  void withActiveClient_should_not_execute_consumer_when_invalid_credentials() {
    var httpClient = mock(HttpClient.class);
    when(awareHttpClientProvider.getHttpClient()).thenReturn(httpClient);
    setupSuccessfulStatusResponse(httpClient, "serverUrl" + API_SYSTEM_STATUS);
    when(connectionRepository.getConnectionById("connectionId"))
      .thenReturn(new SonarQubeConnectionConfiguration("connectionId", "serverUrl", true));
    when(awareHttpClientProvider.getHttpClient("connectionId", false))
      .thenThrow(new IllegalStateException("No token was provided"));

    var consumerExecuted = new AtomicBoolean(false);

    underTest.withActiveClient("connectionId", api -> consumerExecuted.set(true));

    assertThat(consumerExecuted.get()).isFalse();
  }

  private void setupServerConnection(String connectionId, String serverUrl) {
    when(connectionRepository.getConnectionById(connectionId))
      .thenReturn(new SonarQubeConnectionConfiguration(connectionId, serverUrl, true));
    var httpClient = mock(HttpClient.class);
    when(awareHttpClientProvider.getHttpClient(connectionId, false)).thenReturn(httpClient);
    when(awareHttpClientProvider.getHttpClient()).thenReturn(httpClient);
    setupSuccessfulStatusResponse(httpClient, serverUrl + API_SYSTEM_STATUS);
  }

  private void setupCloudConnection(String connectionId, URI prodUri, URI apiUri) {
    when(connectionRepository.getConnectionById(connectionId))
      .thenReturn(new SonarCloudConnectionConfiguration(prodUri, apiUri, connectionId, "organizationKey", SonarCloudRegion.EU, false));
    var httpClient = mock(HttpClient.class);
    when(awareHttpClientProvider.getHttpClient(connectionId, true)).thenReturn(httpClient);
    when(awareHttpClientProvider.getHttpClient()).thenReturn(httpClient);
    setupSuccessfulStatusResponse(httpClient, API_SYSTEM_STATUS);
  }

  private void setupSuccessfulStatusResponse(HttpClient httpClient, String statusPath) {
    var httpResponse = mock(HttpClient.Response.class);
    when(httpResponse.isSuccessful()).thenReturn(true);
    when(httpResponse.bodyAsString()).thenReturn("{\"id\": \"20160308094653\",\"version\": \"9.9\",\"status\": \"UP\"}");
    when(httpClient.getAsyncAnonymous(statusPath)).thenReturn(CompletableFuture.completedFuture(httpResponse));
  }
}
