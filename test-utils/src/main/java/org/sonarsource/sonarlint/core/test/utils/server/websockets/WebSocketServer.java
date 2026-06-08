/*
 * SonarLint Core - Test Utils
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
package org.sonarsource.sonarlint.core.test.utils.server.websockets;

import jakarta.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

public class WebSocketServer {
  private Impl impl = new Impl(0);
  private final Map<WebSocket, WebSocketConnection> connectionsBySocket = Collections.synchronizedMap(new LinkedHashMap<>());

  public void start() {
    impl.startSync();
  }

  public void stop() {
    try {
      impl.stop();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted", e);
    }
  }

  public void restart() {
    var port = getPort();
    stop();
    connectionsBySocket.clear();
    impl = new Impl(port);
    start();
  }

  public int getPort() {
    return impl.getPort();
  }

  public URI getUri() {
    return URI.create("ws://localhost:" + getPort());
  }

  public List<WebSocketConnection> getConnections() {
    return connectionsBySocket.values().stream().toList();
  }

  public class Impl extends org.java_websocket.server.WebSocketServer {
    private final CountDownLatch started = new CountDownLatch(1);

    public Impl(int port) {
      super(new InetSocketAddress(port));
    }

    void startSync() {
      this.start();
      // start is asynchronous, need to wait for onStart to be called for port to be assigned and to consider server is up
      try {
        if (!started.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out while starting WebSocket server");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted", e);
      }
    }

    @Override
    public void onOpen(WebSocket socket, ClientHandshake handshake) {
      connectionsBySocket.put(socket, createWsConnection(socket, handshake));
    }

    @Override
    public void onClose(WebSocket socket, int code, String reason, boolean remote) {
      var connection = connectionsBySocket.get(socket);
      if (connection != null) {
        connection.setIsClosed();
      }
    }

    @Override
    public void onMessage(WebSocket socket, String message) {
      var connection = connectionsBySocket.get(socket);
      if (connection != null) {
        connection.addReceivedMessage(message);
      }
    }

    @Override
    public void onError(@Nullable WebSocket socket, Exception ex) {
      if (socket != null) {
        var connection = connectionsBySocket.get(socket);
        if (connection != null) {
          connection.setIsError(ex);
        }
      }
    }

    @Override
    public void onStart() {
      started.countDown();
    }

    private static WebSocketConnection createWsConnection(WebSocket conn, ClientHandshake handshake) {
      return new WebSocketConnection(new WebSocketRequest(handshake.getFieldValue("Authorization"), handshake.getFieldValue("User-Agent")), conn);
    }
  }
}
