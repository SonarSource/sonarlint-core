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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.push.ServerEvent;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.EventParser;
import org.sonarsource.sonarlint.core.websocket.parsing.QualityGateChangedEventParser;

public class SonarCloudWebSocket {
  public static final String WEBSOCKET_DEV_URL = "wss://squad-5-events-api.sc-dev.io/";
  public static final String WEBSOCKET_URL = "wss://events-api.sonarcloud.io/";
  private static final Map<String, EventParser<?>> parsersByType = Map.of(
    "QualityGateChanged", new QualityGateChangedEventParser());

  private WebSocket ws;
  private static final Gson gson = new Gson();

  public static SonarCloudWebSocket create(HttpClient httpClient, Consumer<ServerEvent> serverEventConsumer) {
    return new SonarCloudWebSocket(httpClient.createWebSocketConnection(WEBSOCKET_DEV_URL, rawEvent -> handleRawMessage(rawEvent, serverEventConsumer)));
  }

  public SonarCloudWebSocket(WebSocket ws) {
    this.ws = ws;
  }

  public void subscribe(String projectKey) {
    send("subscribe", projectKey);
  }

  public void unsubscribe(String projectKey) {
    send("unsubscribe", projectKey);
  }

  private void send(String messageType, String projectKey) {
    var unsubscribePayload = new WebSocketEventSubscribePayload(messageType, "QualityGateChanged", projectKey);

    var jsonString = gson.toJson(unsubscribePayload);

    SonarLintLogger.get().debug("sent '" + messageType + "' for project '" + projectKey + "'");
    this.ws.sendText(jsonString, true);
  }

  private static void handleRawMessage(String message, Consumer<ServerEvent> serverEventConsumer) {
    try {
      var wsEvent = gson.fromJson(message, WebSocketEvent.class);
      parse(wsEvent).ifPresent(serverEventConsumer);
      SonarLintLogger.get().debug("Server event received: " + message, ClientLogOutput.Level.DEBUG);
    } catch (Exception e) {
      SonarLintLogger.get().error("Malformed event received: " + message, e);
    }
  }

  private static Optional<? extends ServerEvent> parse(WebSocketEvent event) {
    var eventType = event.eventType;
    if (!parsersByType.containsKey(eventType)) {
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
      this.ws.sendClose(WebSocket.NORMAL_CLOSURE, "");
      this.ws = null;
    }
  }

  private static class WebSocketEvent {
    private String eventType;
    private JsonObject data;
  }
}
