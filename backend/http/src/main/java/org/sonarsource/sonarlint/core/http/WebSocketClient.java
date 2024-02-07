/*
 * SonarLint Core - HTTP
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
package org.sonarsource.sonarlint.core.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class WebSocketClient {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
  private static final String USER_AGENT_HEADER_NAME = "User-Agent";

  private final String userAgent;
  @Nullable
  private final String token;
  private final HttpClient httpClient;

  WebSocketClient(String userAgent, @Nullable String token, ExecutorService executor) {
    this.userAgent = userAgent;
    this.token = token;
    this.httpClient = HttpClient
      .newBuilder()
      // Don't use the default thread pool as it won't allow inheriting thread local variables
      .executor(executor)
      .build();
  }

  public CompletableFuture<WebSocket> createWebSocketConnection(String url, Consumer<String> messageConsumer, Runnable onClosedRunnable) {
    // TODO handle handshake or other errors
    var currentThreadOutput = SonarLintLogger.getTargetForCopy();
    return httpClient
      .newWebSocketBuilder()
      .header(AUTHORIZATION_HEADER_NAME, "Bearer " + token)
      .header(USER_AGENT_HEADER_NAME, userAgent)
      .buildAsync(URI.create(url), new MessageConsumerWrapper(messageConsumer, onClosedRunnable, currentThreadOutput));
  }

  private static class MessageConsumerWrapper implements WebSocket.Listener {
    private final Consumer<String> messageConsumer;
    private final Runnable onWebSocketInputClosedRunnable;
    private final LogOutput currentThreadOutput;

    public MessageConsumerWrapper(Consumer<String> messageConsumer, Runnable onWebSocketInputClosedRunnable, @Nullable LogOutput currentThreadOutput) {
      this.messageConsumer = messageConsumer;
      this.onWebSocketInputClosedRunnable = onWebSocketInputClosedRunnable;
      this.currentThreadOutput = currentThreadOutput;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      // HttpClient is calling downstream completablefutures on the CF common pool so the thread local variables are
      // not necessarily inherited
      // See
      // https://github.com/openjdk/jdk/blob/744e0893100d402b2b51762d57bcc2e99ab7fdcc/src/java.net.http/share/classes/jdk/internal/net/http/HttpClientImpl.java#L1069
      SonarLintLogger.setTarget(currentThreadOutput);
      LOG.debug("WebSocket opened");
      WebSocket.Listener.super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      SonarLintLogger.setTarget(currentThreadOutput);
      messageConsumer.accept(data.toString());
      return WebSocket.Listener.super.onText(webSocket, data, last);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      SonarLintLogger.setTarget(currentThreadOutput);
      LOG.error("Error occurred on the WebSocket", error);
      onWebSocketInputClosedRunnable.run();
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      SonarLintLogger.setTarget(currentThreadOutput);
      LOG.debug("WebSocket closed, status=" + statusCode + ", reason=" + reason);
      onWebSocketInputClosedRunnable.run();
      return null;
    }
  }

}
