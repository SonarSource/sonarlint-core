/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.websocket;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.push.ServerEvent;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.EventParser;
import org.sonarsource.sonarlint.core.websocket.parsing.QualityGateChangedEventParser;

public class SonarCloudWebSocket {

  public static String getUrl() {
    return System.getProperty("sonarlint.internal.sonarcloud.websocket.url", "wss://events-api.sonarcloud.io/");
  }
  private static final Map<String, EventParser<?>> parsersByType = Map.of(
    "QualityGateChanged", new QualityGateChangedEventParser());

  private static final String PROJECT_FILTER_TYPE = "PROJECT";
  private static final Gson gson = new Gson();
  private WebSocket ws;
  private final History history = new History();
  private final ScheduledExecutorService sonarCloudWebSocketScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "sonarcloud-websocket-scheduled-jobs"));

  public static SonarCloudWebSocket create(HttpClient httpClient, Consumer<ServerEvent> serverEventConsumer, Runnable connectionEndedRunnable) {
    var webSocket = new SonarCloudWebSocket();
    webSocket.ws = httpClient.createWebSocketConnection(getUrl(), rawEvent -> webSocket.handleRawMessage(rawEvent, serverEventConsumer), connectionEndedRunnable);
    webSocket.sonarCloudWebSocketScheduler.scheduleAtFixedRate(webSocket::cleanUpMessageHistory, 0, 5, TimeUnit.MINUTES);
    webSocket.sonarCloudWebSocketScheduler.schedule(connectionEndedRunnable, 119, TimeUnit.MINUTES);
    webSocket.sonarCloudWebSocketScheduler.scheduleAtFixedRate(webSocket::keepAlive, 9, 9, TimeUnit.MINUTES);
    return webSocket;
  }

  private void keepAlive() {
    this.ws.sendText("{\"action\": \"keep_alive\",\"statusCode\":200}", true);
  }

  private void cleanUpMessageHistory() {
    history.forgetOlderThan(Duration.ofMinutes(1));
  }

  private SonarCloudWebSocket() {
  }

  public void subscribe(String projectKey) {
    send("subscribe", projectKey);
  }

  public void unsubscribe(String projectKey) {
    send("unsubscribe", projectKey);
  }

  private void send(String messageType, String projectKey) {
    var unsubscribePayload = new WebSocketEventSubscribePayload(messageType, parsersByType.keySet().toArray(new String[0]), PROJECT_FILTER_TYPE, projectKey);

    var jsonString = gson.toJson(unsubscribePayload);

    SonarLintLogger.get().debug("sent '" + messageType + "' for project '" + projectKey + "'");
    this.ws.sendText(jsonString, true);
  }

  private void handleRawMessage(String message, Consumer<ServerEvent> serverEventConsumer) {
    if (history.exists(message)) {
      // SC implements at least 1 time delivery, so we need to de-duplicate the messages
      return;
    }
    history.recordMessage(message);
    try {
      var wsEvent = gson.fromJson(message, WebSocketEvent.class);
      parse(wsEvent).ifPresent(serverEventConsumer);
      SonarLintLogger.get().debug("Server event received: " + message, ClientLogOutput.Level.DEBUG);
    } catch (Exception e) {
      SonarLintLogger.get().error("Malformed event received: " + message, e);
    }
  }

  private static Optional<? extends ServerEvent> parse(WebSocketEvent event) {
    var eventType = event.event;
    if (eventType == null) {
      return Optional.empty();
    } else if (!parsersByType.containsKey(eventType)) {
      SonarLintLogger.get().error("Unknown '{}' event type ", eventType);
      return Optional.empty();
    }
    try {
      return parsersByType.get(eventType).parse(event.data.toString());
    } catch (Exception e) {
      SonarLintLogger.get().error("Cannot parse '{}' received event", eventType, e);
    }
    return Optional.empty();
  }

  public void close() {
    if (this.ws != null) {
      // output could already be closed if an error occurred
      if (!this.ws.isOutputClosed()) {
        try {
          // close output
          this.ws.sendClose(WebSocket.NORMAL_CLOSURE, "").get();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
          SonarLintLogger.get().error("Cannot close the WebSocket output", e);
        }
      }
      if (!this.ws.isInputClosed()) {
        // close input
        this.ws.abort();
      }
      this.ws = null;
    }
    if (!MoreExecutors.shutdownAndAwaitTermination(sonarCloudWebSocketScheduler, 1, TimeUnit.SECONDS)) {
      SonarLintLogger.get().warn("Unable to stop SonarCloud WebSocket job scheduler in a timely manner");
    }
  }

  public boolean isOpen() {
    return ws != null && !ws.isInputClosed() && !ws.isOutputClosed();
  }

  private static class WebSocketEvent {
    private String event;
    private JsonObject data;
  }
}
