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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.java_websocket.WebSocket;

public class WebSocketConnection {
  private final WebSocketRequest request;
  private boolean isOpened = true;
  private final List<String> receivedMessages = new CopyOnWriteArrayList<>();
  private Throwable throwable;
  private final WebSocket session;

  public WebSocketConnection(WebSocketRequest request, WebSocket session) {
    this.request = request;
    this.session = session;
  }

  public String getAuthorizationHeader() {
    return request.authorizationHeader();
  }

  public String getUserAgent() {
    return request.userAgent();
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
    if (!isOpened) {
      throw new IllegalStateException("Cannot send a message, the WebSocket is not opened");
    }
    if (throwable != null) {
      throw new IllegalStateException("Cannot send a message, the WebSocket previously errored", throwable);
    }
    session.send(message);
  }

  public void close() {
    session.close();
  }
}
