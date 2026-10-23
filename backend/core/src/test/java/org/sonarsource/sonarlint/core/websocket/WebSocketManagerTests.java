/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.websocket;

import java.net.URI;
import java.net.http.WebSocket;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.http.WebSocketClient;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.springframework.context.ApplicationEventPublisher;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.websocket.WebSocketManager.RETRY_INITIAL_DELAY_PROPERTY;

@ExtendWith(SystemStubsExtension.class)
class WebSocketManagerTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @SystemStub
  private SystemProperties systemProperties;

  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final SonarQubeClientManager sonarQubeClientManager = mock(SonarQubeClientManager.class);
  private final ConfigurationRepository configurationRepository = mock(ConfigurationRepository.class);
  private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
  private final WebSocketClient webSocketClient = mock(WebSocketClient.class);
  private final URI websocketEndpointUri = URI.create("wss://test.example.com/websocket");
  private WebSocketManager webSocketManager;

  @BeforeEach
  void setUp() {
    systemProperties.set(RETRY_INITIAL_DELAY_PROPERTY, "60");
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(executor).execute(any(Runnable.class));
    when(sonarQubeClientManager.getValidWebSocketClient("connectionId")).thenReturn(Optional.of(webSocketClient));
    webSocketManager = new WebSocketManager(eventPublisher, sonarQubeClientManager, configurationRepository, websocketEndpointUri, executor);
  }

  @Test
  void should_schedule_retry_when_connection_fails() {
    var wsFuture = failingConnection();
    var scheduledFuture = mock(ScheduledFuture.class);
    when(executor.schedule(any(Runnable.class), anyLong(), any())).thenReturn(scheduledFuture);

    webSocketManager.createConnectionIfNeeded("connectionId");
    wsFuture.completeExceptionally(new RuntimeException("connection failed"));

    verify(executor).schedule(any(Runnable.class), eq(60L), eq(TimeUnit.SECONDS));
    assertThat(logTester.logs()).contains("Cannot connect to SonarCloud WebSocket, retrying in 60s");
  }

  @Test
  void should_schedule_retry_when_connection_fails_synchronously() {
    var wsFuture = new CompletableFuture<WebSocket>();
    wsFuture.completeExceptionally(new RuntimeException("connection failed"));
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);
    var scheduledFuture = mock(ScheduledFuture.class);
    when(executor.schedule(any(Runnable.class), anyLong(), any())).thenReturn(scheduledFuture);

    webSocketManager.createConnectionIfNeeded("connectionId");

    verify(executor).schedule(any(Runnable.class), eq(60L), eq(TimeUnit.SECONDS));
    assertThat(logTester.logs()).contains("Cannot connect to SonarCloud WebSocket, retrying in 60s");
  }

  @Test
  void should_use_exponential_backoff_between_retries() {
    var scheduledFuture = mock(ScheduledFuture.class);
    when(executor.schedule(any(Runnable.class), anyLong(), any())).thenReturn(scheduledFuture);

    var firstFuture = failingConnection();
    webSocketManager.createConnectionIfNeeded("connectionId");
    firstFuture.completeExceptionally(new RuntimeException("connection failed"));
    assertThat(logTester.logs()).contains("Cannot connect to SonarCloud WebSocket, retrying in 60s");

    runScheduledRetry(scheduledFuture);
    assertThat(logTester.logs()).contains("Cannot connect to SonarCloud WebSocket, retrying in 120s");

    runScheduledRetry(scheduledFuture);
    assertThat(logTester.logs()).contains("Cannot connect to SonarCloud WebSocket, retrying in 240s");
    verify(executor).schedule(any(Runnable.class), eq(240L), eq(TimeUnit.SECONDS));
  }

  @Test
  void should_stop_retrying_after_max_attempts() {
    var scheduledFuture = mock(ScheduledFuture.class);
    when(executor.schedule(any(Runnable.class), anyLong(), any())).thenReturn(scheduledFuture);

    var firstFuture = failingConnection();
    webSocketManager.createConnectionIfNeeded("connectionId");
    firstFuture.completeExceptionally(new RuntimeException("connection failed"));

    for (int attemptNumber = 0; attemptNumber < 8; attemptNumber++) {
      runScheduledRetry(scheduledFuture);
    }

    ArgumentCaptor<Runnable> lastRetryCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).schedule(lastRetryCaptor.capture(), anyLong(), any());
    clearInvocations(executor);
    var lastWsFuture = failingConnection();
    lastRetryCaptor.getValue().run();
    lastWsFuture.completeExceptionally(new RuntimeException("connection failed"));

    verify(executor, never()).schedule(any(Runnable.class), anyLong(), any());
    assertThat(logTester.logs()).contains(
      "Cannot connect to SonarCloud WebSocket, retrying in 15360s",
      "Cannot connect to SonarCloud WebSocket, stop retrying");
  }

  @Test
  void should_reset_attempt_counter_on_new_independent_connection_attempt_after_exhaustion() {
    var scheduledFuture = mock(ScheduledFuture.class);
    when(executor.schedule(any(Runnable.class), anyLong(), any())).thenReturn(scheduledFuture);

    var firstFuture = failingConnection();
    webSocketManager.createConnectionIfNeeded("connectionId");
    firstFuture.completeExceptionally(new RuntimeException("connection failed"));

    for (int attemptNumber = 0; attemptNumber < 8; attemptNumber++) {
      runScheduledRetry(scheduledFuture);
    }

    ArgumentCaptor<Runnable> lastRetryCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).schedule(lastRetryCaptor.capture(), anyLong(), any());
    clearInvocations(executor);
    var lastWsFuture = failingConnection();
    lastRetryCaptor.getValue().run();
    lastWsFuture.completeExceptionally(new RuntimeException("connection failed"));
    verify(executor, never()).schedule(any(Runnable.class), anyLong(), any());

    clearInvocations(executor);
    var newAttemptFuture = failingConnection();
    when(executor.schedule(any(Runnable.class), anyLong(), any())).thenReturn(scheduledFuture);
    webSocketManager.createConnectionIfNeeded("connectionId");
    newAttemptFuture.completeExceptionally(new RuntimeException("connection failed again"));

    verify(executor).schedule(any(Runnable.class), eq(60L), eq(TimeUnit.SECONDS));
    assertThat(logTester.logs()).contains("Cannot connect to SonarCloud WebSocket, retrying in 60s");
  }

  @Test
  void should_cancel_pending_retry_when_shutting_down() {
    var wsFuture = failingConnection();
    var scheduledFuture = mock(ScheduledFuture.class);
    when(executor.schedule(any(Runnable.class), anyLong(), any())).thenReturn(scheduledFuture);

    webSocketManager.createConnectionIfNeeded("connectionId");
    wsFuture.completeExceptionally(new RuntimeException("connection failed"));

    webSocketManager.shutdown();

    verify(scheduledFuture).cancel(true);
  }

  @Test
  void should_reset_attempt_counter_after_successful_connection() {
    var failingFuture = failingConnection();
    var scheduledFuture = mock(ScheduledFuture.class);
    when(executor.schedule(any(Runnable.class), anyLong(), any())).thenReturn(scheduledFuture);

    webSocketManager.createConnectionIfNeeded("connectionId");
    failingFuture.completeExceptionally(new RuntimeException("connection failed"));

    ArgumentCaptor<Runnable> retryCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).schedule(retryCaptor.capture(), eq(60L), eq(TimeUnit.SECONDS));
    clearInvocations(executor);

    var succeedingFuture = new CompletableFuture<WebSocket>();
    var mockWebSocket = mock(WebSocket.class);
    when(mockWebSocket.isInputClosed()).thenReturn(false);
    when(mockWebSocket.isOutputClosed()).thenReturn(false);
    ArgumentCaptor<Runnable> onClosedCaptor = ArgumentCaptor.forClass(Runnable.class);
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), onClosedCaptor.capture())).thenReturn(succeedingFuture);
    when(mockWebSocket.sendClose(any(Integer.class), any())).thenAnswer(invocation -> {
      onClosedCaptor.getValue().run();
      return CompletableFuture.completedFuture(null);
    });
    when(executor.schedule(any(Runnable.class), anyLong(), any())).thenReturn(scheduledFuture);
    retryCaptor.getValue().run();
    succeedingFuture.complete(mockWebSocket);
    assertThat(webSocketManager.hasOpenConnection()).isTrue();

    clearInvocations(executor);
    var reopenFuture = failingConnection();
    when(executor.schedule(any(Runnable.class), anyLong(), any())).thenReturn(scheduledFuture);
    webSocketManager.reopenConnection("connectionId", "force reopen");
    reopenFuture.completeExceptionally(new RuntimeException("connection failed again"));

    verify(executor).schedule(any(Runnable.class), eq(60L), eq(TimeUnit.SECONDS));
  }

  private CompletableFuture<WebSocket> failingConnection() {
    var wsFuture = new CompletableFuture<WebSocket>();
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);
    return wsFuture;
  }

  private void runScheduledRetry(ScheduledFuture scheduledFuture) {
    ArgumentCaptor<Runnable> retryCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).schedule(retryCaptor.capture(), anyLong(), any());
    clearInvocations(executor);
    var nextFuture = failingConnection();
    when(executor.schedule(any(Runnable.class), anyLong(), any())).thenReturn(scheduledFuture);
    retryCaptor.getValue().run();
    nextFuture.completeExceptionally(new RuntimeException("connection failed"));
  }
}
