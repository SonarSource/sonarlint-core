/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.stream;

import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.http.HttpConnectionListener;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventStreamTests {
  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private final ServerApiHelper apiHelper = mock(ServerApiHelper.class);
  private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
  private final ArgumentCaptor<HttpConnectionListener> listenerCaptor = ArgumentCaptor.forClass(HttpConnectionListener.class);
  private final ArgumentCaptor<Consumer<String>> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
  private final ArrayList<Event> receivedEvents = new ArrayList<>();
  private final ClientLogOutput logOutput = logTester.getLogOutput();
  private EventStream stream;

  @BeforeEach
  void setUp() {
    stream = new EventStream(apiHelper, executor);
    stream.onEvent(receivedEvents::add);
  }

  @Test
  void should_log_when_connected() {
    stream.connect("wsPath", logOutput);
    verify(apiHelper).getEventStream(eq("wsPath"), listenerCaptor.capture(), any());

    listenerCaptor.getValue().onConnected();

    assertThat(logTester.logs())
      .containsOnly(
        "Connecting to server event-stream at 'wsPath'...",
        "Connected to server event-stream");
  }

  @Test
  void should_notify_consumer_when_event_received() {
    stream.connect("wsPath", logOutput);
    verify(apiHelper).getEventStream(eq("wsPath"), listenerCaptor.capture(), consumerCaptor.capture());
    var scheduledFuture = mock(ScheduledFuture.class);
    when(executor.schedule(any(Runnable.class), anyLong(), any())).thenReturn(scheduledFuture);
    listenerCaptor.getValue().onConnected();
    consumerCaptor.getValue().accept("event: type\ndata: data\n\n");

    assertThat(receivedEvents)
      .extracting("type", "data")
      .containsOnly(tuple("type", "data"));
  }

  @Test
  void should_not_retry_when_unauthorized() {
    stream.connect("wsPath", logOutput);
    verify(apiHelper).getEventStream(eq("wsPath"), listenerCaptor.capture(), any());

    listenerCaptor.getValue().onError(401);

    verifyNoInteractions(executor);
    assertThat(logTester.logs())
      .containsOnly(
        "Connecting to server event-stream at 'wsPath'...",
        "Cannot connect to server event-stream, unauthorized");
  }

  @Test
  void should_not_retry_when_forbidden() {
    stream.connect("wsPath", logOutput);
    verify(apiHelper).getEventStream(eq("wsPath"), listenerCaptor.capture(), any());

    listenerCaptor.getValue().onError(403);

    verifyNoInteractions(executor);
    assertThat(logTester.logs())
      .containsOnly(
        "Connecting to server event-stream at 'wsPath'...",
        "Cannot connect to server event-stream, forbidden");
  }

  @Test
  void should_not_retry_when_api_not_found() {
    stream.connect("wsPath", logOutput);
    verify(apiHelper).getEventStream(eq("wsPath"), listenerCaptor.capture(), any());

    listenerCaptor.getValue().onError(404);

    verifyNoInteractions(executor);
    assertThat(logTester.logs())
      .containsOnly(
        "Connecting to server event-stream at 'wsPath'...",
        "Server events not supported by the server");
  }

  @Test
  void should_retry_when_server_error() {
    stream.connect("wsPath", logOutput);
    verify(apiHelper).getEventStream(eq("wsPath"), listenerCaptor.capture(), any());

    listenerCaptor.getValue().onError(500);

    verify(executor).schedule(any(Runnable.class), eq(60L), eq(TimeUnit.SECONDS));
    assertThat(logTester.logs())
      .containsOnly(
        "Connecting to server event-stream at 'wsPath'...",
        "Cannot connect to server event-stream, retrying in 60s");
  }

  @Test
  void should_reconnect_when_disconnected() {
    stream.connect("wsPath", logOutput);
    verify(apiHelper).getEventStream(eq("wsPath"), listenerCaptor.capture(), any());
    var listener = listenerCaptor.getValue();
    var scheduledFuture = mock(ScheduledFuture.class);
    when(executor.schedule(any(Runnable.class), anyLong(), any())).thenReturn(scheduledFuture);
    listener.onConnected();

    listener.onClosed();

    verify(apiHelper).getEventStream(eq("wsPath"), eq(listener), any());
    assertThat(logTester.logs())
      .containsOnly(
        "Connecting to server event-stream at 'wsPath'...",
        "Connected to server event-stream",
        "Disconnected from server event-stream, reconnecting now",
        "Connecting to server event-stream at 'wsPath'...");
  }

  @Test
  void should_stop_retrying_after_failed_attempts() {
    stream.connect("wsPath", logOutput);

    for (int attemptNumber = 0; attemptNumber < 9; attemptNumber++) {
      verify(apiHelper).getEventStream(eq("wsPath"), listenerCaptor.capture(), any());
      listenerCaptor.getValue().onError(null);
      ArgumentCaptor<Runnable> callableCaptor = ArgumentCaptor.forClass(Runnable.class);
      verify(executor).schedule(callableCaptor.capture(), anyLong(), any());
      clearInvocations(executor);
      clearInvocations(apiHelper);
      callableCaptor.getValue().run();
    }
    verify(apiHelper).getEventStream(eq("wsPath"), listenerCaptor.capture(), any());
    listenerCaptor.getValue().onError(null);
    verifyNoInteractions(executor);

    assertThat(logTester.logs())
      .contains(
        "Connecting to server event-stream at 'wsPath'...",
        "Cannot connect to server event-stream, retrying in 15360s",
        "Cannot connect to server event-stream, stop retrying");
  }

  @Test
  void should_reconnect_when_no_heart_beat_received_for_a_while() {
    var scheduledFuture = mock(ScheduledFuture.class);
    ArgumentCaptor<Runnable> callableCaptor = ArgumentCaptor.forClass(Runnable.class);
    when(executor.schedule(callableCaptor.capture(), anyLong(), any())).thenReturn(scheduledFuture);
    stream.connect("wsPath", logOutput);
    verify(apiHelper).getEventStream(eq("wsPath"), listenerCaptor.capture(), any());
    var listener = listenerCaptor.getValue();

    listener.onConnected();

    callableCaptor.getValue().run();

    verify(apiHelper).getEventStream(eq("wsPath"), eq(listener), any());
  }

  @Test
  void should_cancel_request_when_closing_stream() {
    var asyncRequest = mock(HttpClient.AsyncRequest.class);
    when(apiHelper.getEventStream(eq("wsPath"), any(), any())).thenReturn(asyncRequest);
    stream.connect("wsPath", logOutput);

    stream.close();

    verify(asyncRequest).cancel();
  }

  @Test
  void should_cancel_delayed_retry_when_closing_stream() {
    var scheduledFuture = mock(ScheduledFuture.class);
    when(executor.schedule(any(Runnable.class), anyLong(), any())).thenReturn(scheduledFuture);
    stream.connect("wsPath", logOutput);
    verify(apiHelper).getEventStream(eq("wsPath"), listenerCaptor.capture(), any());
    listenerCaptor.getValue().onError(null);

    stream.close();

    verify(scheduledFuture).cancel(true);
  }

  @Test
  void should_close_executor_when_closing_stream() {
    stream.close();

    verify(executor).shutdownNow();
  }
}
