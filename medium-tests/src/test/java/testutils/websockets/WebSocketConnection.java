/*
 * SonarLint Core - Medium Tests
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
package testutils.websockets;

import jakarta.websocket.Session;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WebSocketConnection {
  private final WebSocketRequest request;
  private boolean isOpened = true;
  private final List<String> receivedMessages = new CopyOnWriteArrayList<>();
  private Throwable throwable;
  private final Session session;

  public WebSocketConnection(WebSocketRequest request, Session session) {
    this.request = request;
    this.session = session;
  }

  public String getAuthorizationHeader() {
    return request.getAuthorizationHeader();
  }

  public String getUserAgent() {
    return request.getUserAgent();
  }

  public boolean isOpened() {
    return isOpened;
  }

  public List<String> getReceivedMessages() {
    return receivedMessages;
  }

  public void addReceivedMessage(String message) {
    receivedMessages.add(message);
  }

  void setIsClosed() {
    isOpened = false;
  }

  public void setIsError(Throwable throwable) {
    this.throwable = throwable;
  }

  public void sendMessage(String message) {
    if (session == null) {
      throw new IllegalStateException("Cannot send a message, session is null");
    }
    if (!isOpened) {
      throw new IllegalStateException("Cannot send a message, the WebSocket is not opened");
    }
    if (throwable != null) {
      throw new IllegalStateException("Cannot send a message, the WebSocket previously errored", throwable);
    }
    try {
      session.getBasicRemote().sendText(message);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void close() {
    if (session != null) {
      try {
        session.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
