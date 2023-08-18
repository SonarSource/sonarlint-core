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
package testutils.websockets;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/endpoint", configurator = ServletAwareConfig.class)
public class WebSocketEndpoint {
  public static final String WS_CONNECTION_USER_PROPERTY_KEY = "wsConnection";

  @OnOpen
  public void onOpen(final Session session) {
    var wsConnection = getWsConnection(session);
    wsConnection.setSession(session);
  }

  @OnMessage
  public String handleTextMessage(Session session, String message) {
    var wsConnection = getWsConnection(session);
    wsConnection.addReceivedMessage(message);
    return wsConnection.pollPreparedAnswer();
  }

  @OnClose
  public void onClose(final Session session) {
    getWsConnection(session).setIsClosed();
  }

  @OnError
  public void onError(final Session session, final Throwable throwable) {
    getWsConnection(session).setIsError(throwable);
  }

  private static WebSocketConnection getWsConnection(Session session) {
    return (WebSocketConnection) session.getUserProperties().get(WS_CONNECTION_USER_PROPERTY_KEY);
  }
}
