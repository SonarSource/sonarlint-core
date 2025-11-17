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
package org.sonarsource.sonarlint.core.websocket;

import java.io.IOException;
import java.net.URI;
import java.net.http.WebSocket;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.http.WebSocketClient;
import org.sonarsource.sonarlint.core.serverapi.push.SonarServerEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SonarCloudWebSocketTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private WebSocketClient webSocketClient;
  private WebSocket mockWebSocket;
  private CompletableFuture<WebSocket> wsFuture;
  private Consumer<SonarServerEvent> serverEventConsumer;
  private Runnable connectionEndedRunnable;
  private SonarCloudWebSocket sonarCloudWebSocket;
  private URI testUri;

  @BeforeEach
  void setUp() {
    webSocketClient = mock(WebSocketClient.class);
    mockWebSocket = mock(WebSocket.class);
    wsFuture = new CompletableFuture<>();
    serverEventConsumer = mock(Consumer.class);
    connectionEndedRunnable = mock(Runnable.class);
    testUri = URI.create("wss://test.example.com/websocket");
  }

  @Test
  void should_create_websocket_connection_successfully() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);
    when(mockWebSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(null));

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.complete(mockWebSocket);

    assertThat(sonarCloudWebSocket).isNotNull();
    verify(webSocketClient).createWebSocketConnection(eq(testUri), any(Consumer.class), any(Runnable.class));
    assertThat(logTester.logs()).anyMatch(log -> log.contains("Creating WebSocket connection to " + testUri));
  }

  @Test
  void should_handle_connection_failure_with_generic_exception() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.completeExceptionally(new RuntimeException("Generic error"));

    assertThat(sonarCloudWebSocket).isNotNull();
    assertThat(logTester.logs(LogOutput.Level.ERROR)).anyMatch(log -> log.contains("Error while trying to create WebSocket connection for " + testUri));
  }

  @Test
  void should_close_websocket_connection_with_proper_completion() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);
    when(mockWebSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(null));
    when(mockWebSocket.isOutputClosed()).thenReturn(false);
    when(mockWebSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.complete(mockWebSocket);
    var onClosedCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(webSocketClient).createWebSocketConnection(any(URI.class), any(Consumer.class), onClosedCaptor.capture());

    // Simulate the WebSocket input being closed by the server BEFORE calling close
    onClosedCaptor.getValue().run();
    // Now call close - it should complete immediately since webSocketInputClosed is already completed
    sonarCloudWebSocket.close("Test reason");

    verify(mockWebSocket).sendClose(WebSocket.NORMAL_CLOSURE, "");
    assertThat(logTester.logs()).anyMatch(log -> log.contains("Closing SonarCloud WebSocket connection, reason=Test reason"));
    assertThat(logTester.logs()).anyMatch(log -> log.contains("Waiting for SonarCloud WebSocket input to be closed..."));
    assertThat(logTester.logs()).anyMatch(log -> log.contains("SonarCloud WebSocket closed"));
  }

  @Test
  void should_handle_close_execution_exception() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);
    when(mockWebSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(null));
    when(mockWebSocket.isOutputClosed()).thenReturn(false);
    when(mockWebSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Close failed")));

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.complete(mockWebSocket);

    sonarCloudWebSocket.close("Test reason");

    verify(mockWebSocket).sendClose(WebSocket.NORMAL_CLOSURE, "");
    assertThat(logTester.logs(LogOutput.Level.ERROR)).anyMatch(log -> log.contains("Cannot close the WebSocket output"));
  }

  @Test
  void should_handle_unresolved_address_exception_during_close() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);
    when(mockWebSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(null));
    when(mockWebSocket.isOutputClosed()).thenReturn(false);
    when(mockWebSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.failedFuture(new UnresolvedAddressException()));

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.complete(mockWebSocket);

    // Capture the onClosedRunnable callback and complete it to avoid timeout
    var onClosedCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(webSocketClient).createWebSocketConnection(any(URI.class), any(Consumer.class), onClosedCaptor.capture());
    onClosedCaptor.getValue().run();

    sonarCloudWebSocket.close("Test reason");

    verify(mockWebSocket).sendClose(WebSocket.NORMAL_CLOSURE, "");
    assertThat(logTester.logs(LogOutput.Level.DEBUG)).anyMatch(log -> log.contains("WebSocket could not be closed gracefully"));
  }

  @Test
  void should_handle_ioexception_with_output_closed_message() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);
    when(mockWebSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(null));
    when(mockWebSocket.isOutputClosed()).thenReturn(false);
    when(mockWebSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.failedFuture(new IOException("Output closed")));

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.complete(mockWebSocket);

    // Capture the onClosedRunnable callback and complete it to avoid timeout
    var onClosedCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(webSocketClient).createWebSocketConnection(any(URI.class), any(Consumer.class), onClosedCaptor.capture());
    onClosedCaptor.getValue().run();

    sonarCloudWebSocket.close("Test reason");

    verify(mockWebSocket).sendClose(WebSocket.NORMAL_CLOSURE, "");
    assertThat(logTester.logs(LogOutput.Level.DEBUG)).anyMatch(log -> log.contains("WebSocket could not be closed gracefully"));
  }

  @Test
  void should_handle_ioexception_with_closed_output_message() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);
    when(mockWebSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(null));
    when(mockWebSocket.isOutputClosed()).thenReturn(false);
    when(mockWebSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.failedFuture(new IOException("closed output")));

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.complete(mockWebSocket);

    // Capture the onClosedRunnable callback and complete it to avoid timeout
    var onClosedCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(webSocketClient).createWebSocketConnection(any(URI.class), any(Consumer.class), onClosedCaptor.capture());
    onClosedCaptor.getValue().run();

    sonarCloudWebSocket.close("Test reason");

    verify(mockWebSocket).sendClose(WebSocket.NORMAL_CLOSURE, "");
    assertThat(logTester.logs(LogOutput.Level.DEBUG)).anyMatch(log -> log.contains("WebSocket could not be closed gracefully"));
  }

  @Test
  void should_handle_ioexception_with_different_message() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);
    when(mockWebSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(null));
    when(mockWebSocket.isOutputClosed()).thenReturn(false);
    when(mockWebSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.failedFuture(new IOException("Connection reset")));

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.complete(mockWebSocket);

    // Capture the onClosedRunnable callback and complete it to avoid timeout
    var onClosedCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(webSocketClient).createWebSocketConnection(any(URI.class), any(Consumer.class), onClosedCaptor.capture());
    onClosedCaptor.getValue().run();

    sonarCloudWebSocket.close("Test reason");

    verify(mockWebSocket).sendClose(WebSocket.NORMAL_CLOSURE, "");
    assertThat(logTester.logs(LogOutput.Level.ERROR)).anyMatch(log -> log.contains("Cannot close the WebSocket output"));
  }

  @Test
  void should_handle_already_closed_websocket() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);
    when(mockWebSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(null));
    when(mockWebSocket.isOutputClosed()).thenReturn(true);

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.complete(mockWebSocket);

    sonarCloudWebSocket.close("Test reason");

    verify(mockWebSocket, never()).sendClose(anyInt(), anyString());
  }

  @Test
  void should_handle_failed_websocket_future() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.completeExceptionally(new RuntimeException("Connection failed"));

    sonarCloudWebSocket.close("Test reason");

    assertThat(logTester.logs()).anyMatch(log -> log.contains("WebSocket connection was already closed, skipping close operation"));
  }

  @Test
  void should_handle_pending_websocket_future() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class)))
      .thenReturn(wsFuture);

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);

    sonarCloudWebSocket.close("Test reason");

    assertThat(logTester.logs()).anyMatch(log -> log.contains("WebSocket connection was still pending, cancelled"));
  }

  @Test
  void should_check_if_websocket_is_open() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);
    when(mockWebSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(null));
    when(mockWebSocket.isInputClosed()).thenReturn(false);
    when(mockWebSocket.isOutputClosed()).thenReturn(false);

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.complete(mockWebSocket);

    assertThat(sonarCloudWebSocket.isOpen()).isTrue();
  }

  @Test
  void should_return_false_when_websocket_is_closed() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);
    when(mockWebSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(null));
    when(mockWebSocket.isInputClosed()).thenReturn(true);
    when(mockWebSocket.isOutputClosed()).thenReturn(false);

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.complete(mockWebSocket);

    assertThat(sonarCloudWebSocket.isOpen()).isFalse();
  }

  @Test
  void should_return_false_when_websocket_future_is_not_done() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);

    assertThat(sonarCloudWebSocket.isOpen()).isFalse();
  }

  @Test
  void should_return_false_when_websocket_future_failed() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.completeExceptionally(new RuntimeException("Connection failed"));

    assertThat(sonarCloudWebSocket.isOpen()).isFalse();
  }

  @Test
  void should_return_false_when_websocket_future_is_cancelled() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.cancel(true);

    assertThat(sonarCloudWebSocket.isOpen()).isFalse();
  }

  @Test
  void should_handle_connection_ended_callback() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);
    when(mockWebSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(null));

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.complete(mockWebSocket);

    var onClosedCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(webSocketClient).createWebSocketConnection(any(URI.class), any(Consumer.class), onClosedCaptor.capture());

    // Simulate connection ended
    onClosedCaptor.getValue().run();

    verify(connectionEndedRunnable).run();
  }

  @Test
  void should_not_call_connection_ended_callback_when_closing_initiated() {
    when(webSocketClient.createWebSocketConnection(any(URI.class), any(Consumer.class), any(Runnable.class))).thenReturn(wsFuture);
    when(mockWebSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(null));

    sonarCloudWebSocket = SonarCloudWebSocket.create(testUri, webSocketClient, serverEventConsumer, connectionEndedRunnable);
    wsFuture.complete(mockWebSocket);

    // Close the connection first
    sonarCloudWebSocket.close("Test reason");

    var onClosedCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(webSocketClient).createWebSocketConnection(any(URI.class), any(Consumer.class), onClosedCaptor.capture());

    // Simulate connection ended after closing was initiated
    onClosedCaptor.getValue().run();

    // Should not call the callback since closing was initiated
    verify(connectionEndedRunnable, never()).run();
  }

} 
