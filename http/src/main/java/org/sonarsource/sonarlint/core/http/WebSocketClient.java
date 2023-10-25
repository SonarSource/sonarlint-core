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

import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static org.sonarsource.sonarlint.core.commons.concurrent.ThreadFactories.threadWithNamePrefix;

public class WebSocketClient {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  @Nullable
  private final String token;
  private final ExecutorService executor;
  private final HttpClient httpClient;

  WebSocketClient(@Nullable String token) {
    this.token = token;
    this.executor = Executors.newCachedThreadPool(threadWithNamePrefix("sonarcloud-websocket-"));
    this.httpClient = HttpClient
      .newBuilder()
      // Don't use the default thread pool as it won't allow inheriting thread local variables
      .executor(executor)
      .build();
  }

  public CompletableFuture<WebSocket> createWebSocketConnection(String url, Consumer<String> messageConsumer, Runnable onClosedRunnable) {
    // TODO handle handshake or other errors
    return httpClient
      .newWebSocketBuilder()
      .header("Authorization", "Bearer " + token)
      .buildAsync(URI.create(url), new MessageConsummerWrapper(messageConsumer, onClosedRunnable));
  }

  private class MessageConsummerWrapper implements WebSocket.Listener {
    private final Consumer<String> messageConsumer;
    private final Runnable onClosedRunnable;

    public MessageConsummerWrapper(Consumer<String> messageConsumer, Runnable onClosedRunnable) {
      this.messageConsumer = messageConsumer;
      this.onClosedRunnable = onClosedRunnable;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      LOG.debug("WebSocket opened");
      WebSocket.Listener.super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      messageConsumer.accept(data.toString());
      return WebSocket.Listener.super.onText(webSocket, data, last);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      LOG.error("Error occurred on the WebSocket", error);
      finalizeWebSocket();
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      LOG.debug("WebSocket closed, status=" + statusCode + ", reason=" + reason);
      // ack the close
      try {
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        // uncompleted future means the closing has been handled already (default is null)
        return new CompletableFuture<>();
      } catch (ExecutionException e) {
        LOG.debug("Cannot ack WebSocket close");
      }
      finalizeWebSocket();
      // uncompleted future means the closing has been handled already (default is null)
      return new CompletableFuture<>();
    }

    private void finalizeWebSocket() {
      try {
        onClosedRunnable.run();
      } finally {
        if (!MoreExecutors.shutdownAndAwaitTermination(executor, 1, TimeUnit.SECONDS)) {
          LOG.warn("Unable to stop web socket executor service in a timely manner");
        }
      }
    }
  }


}
