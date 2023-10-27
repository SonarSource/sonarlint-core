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
package org.sonarsource.sonarlint.core.websocket;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.push.ServerEvent;
import org.sonarsource.sonarlint.core.http.WebSocketClient;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.EventParser;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.IssueChangedEventParser;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.SecurityHotspotChangedEventParser;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.SecurityHotspotClosedEventParser;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.SecurityHotspotRaisedEventParser;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.TaintVulnerabilityClosedEventParser;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.TaintVulnerabilityRaisedEventParser;
import org.sonarsource.sonarlint.core.websocket.parsing.SmartNotificationEventParser;

public class SonarCloudWebSocket {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public static String getUrl() {
    return System.getProperty("sonarlint.internal.sonarcloud.websocket.url", "wss://events-api.sonarcloud.io/");
  }

  private static final Map<String, EventParser<?>> parsersByTypeForProjectFilter = Map.of(
    "QualityGateChanged", new SmartNotificationEventParser("QUALITY_GATE"),
    "IssueChanged", new IssueChangedEventParser(),
    "SecurityHotspotClosed", new SecurityHotspotClosedEventParser(),
    "SecurityHotspotRaised", new SecurityHotspotRaisedEventParser(),
    "SecurityHotspotChanged", new SecurityHotspotChangedEventParser(),
    "TaintVulnerabilityClosed", new TaintVulnerabilityClosedEventParser(),
    "TaintVulnerabilityRaised", new TaintVulnerabilityRaisedEventParser());
  private static final Map<String, EventParser<?>> parsersByTypeForProjectUserFilter = Map.of(
    "MyNewIssues", new SmartNotificationEventParser("NEW_ISSUES"));
  private static final Map<String, EventParser<?>> parsersByType = Stream.of(parsersByTypeForProjectFilter, parsersByTypeForProjectUserFilter)
    .flatMap(map -> map.entrySet().stream())
    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  private static final String PROJECT_FILTER_TYPE = "PROJECT";
  private static final String PROJECT_USER_FILTER_TYPE = "PROJECT_USER";
  private static final Gson gson = new Gson();
  private CompletableFuture<WebSocket> wsFuture;
  private final History history = new History();
  private final ScheduledExecutorService sonarCloudWebSocketScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "sonarcloud-websocket-scheduled-jobs"));
  private WebSocket ws;

  public static SonarCloudWebSocket create(WebSocketClient webSocketClient, Consumer<ServerEvent> serverEventConsumer, Runnable connectionEndedRunnable) {
    var webSocket = new SonarCloudWebSocket();
    var currentThreadOutput = SonarLintLogger.getTargetForCopy();
    LOG.info("Creating websocket connection to " + getUrl());
    webSocket.wsFuture = webSocketClient.createWebSocketConnection(getUrl(), rawEvent -> webSocket.handleRawMessage(rawEvent, serverEventConsumer), connectionEndedRunnable);
    webSocket.wsFuture.thenAccept(ws -> {
      SonarLintLogger.setTarget(currentThreadOutput);
      webSocket.sonarCloudWebSocketScheduler.scheduleAtFixedRate(webSocket::cleanUpMessageHistory, 0, 5, TimeUnit.MINUTES);
      webSocket.sonarCloudWebSocketScheduler.schedule(connectionEndedRunnable, 119, TimeUnit.MINUTES);
      webSocket.sonarCloudWebSocketScheduler.scheduleAtFixedRate(() -> keepAlive(ws), 9, 9, TimeUnit.MINUTES);
    });
    webSocket.wsFuture.exceptionally(t -> {
      SonarLintLogger.setTarget(currentThreadOutput);
      LOG.error("Error while trying to create websocket connection for " + getUrl(), t);
      return null;
    });
    return webSocket;
  }

  private static void keepAlive(WebSocket ws) {
    ws.sendText("{\"action\": \"keep_alive\",\"statusCode\":200}", true);
  }

  private void cleanUpMessageHistory() {
    history.forgetOlderThan(Duration.ofMinutes(1));
  }

  private SonarCloudWebSocket() {
  }

  public void subscribe(String projectKey) {
    send("subscribe", projectKey, parsersByTypeForProjectFilter, PROJECT_FILTER_TYPE);
    send("subscribe", projectKey, parsersByTypeForProjectUserFilter, PROJECT_USER_FILTER_TYPE);
  }

  public void unsubscribe(String projectKey) {
    send("unsubscribe", projectKey, parsersByTypeForProjectFilter, PROJECT_FILTER_TYPE);
    send("unsubscribe", projectKey, parsersByTypeForProjectUserFilter, PROJECT_USER_FILTER_TYPE);
  }

  private void send(String messageType, String projectKey, Map<String, EventParser<?>> parsersByType, String filter) {
    var eventsKey = parsersByType.keySet().toArray(new String[0]);
    Arrays.sort(eventsKey);
    var payload = new WebSocketEventSubscribePayload(messageType, eventsKey, filter, projectKey);

    var jsonString = gson.toJson(payload);
    this.wsFuture.thenAccept(ws -> {
      SonarLintLogger.get().debug("sent '" + messageType + "' for project '" + projectKey + "'");
      ws.sendText(jsonString, true);
    });
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
    }

    if (parsersByType.containsKey(eventType)) {
      return tryParsing(parsersByType.get(eventType), event);
    } else {
      SonarLintLogger.get().error("Unknown '{}' event type ", eventType);
      return Optional.empty();
    }
  }

  private static Optional<? extends ServerEvent> tryParsing(EventParser<? extends ServerEvent> eventParser, WebSocketEvent event) {
    try {
      return eventParser.parse(event.data.toString());
    } catch (Exception e) {
      SonarLintLogger.get().error("Cannot parse '{}' received event", event.event, e);
      return Optional.empty();
    }
  }

  public void close() {
    if (this.wsFuture != null) {
      // output could already be closed if an error occurred
      this.wsFuture.thenAccept(SonarCloudWebSocket::close);
      this.wsFuture = null;
    }
    if (!MoreExecutors.shutdownAndAwaitTermination(sonarCloudWebSocketScheduler, 1, TimeUnit.SECONDS)) {
      SonarLintLogger.get().warn("Unable to stop SonarCloud WebSocket job scheduler in a timely manner");
    }
  }

  private static void close(WebSocket ws) {
    if (!ws.isOutputClosed()) {
      try {
        // close output
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "").get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (ExecutionException e) {
        SonarLintLogger.get().error("Cannot close the WebSocket output", e);
      }
    }
    if (!ws.isInputClosed()) {
      // close input
      ws.abort();
    }
  }

  public boolean isOpen() {
    return wsFuture != null
      && wsFuture.isDone()
      && !wsFuture.isCompletedExceptionally()
      && !wsFuture.isCancelled()
      && !wsFuture.getNow(null).isInputClosed()
      && !wsFuture.getNow(null).isOutputClosed();
  }

  private static class WebSocketEvent {
    private String event;
    private JsonObject data;
  }
}
