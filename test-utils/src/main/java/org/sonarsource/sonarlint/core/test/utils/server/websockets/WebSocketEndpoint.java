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

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.sonarsource.sonarlint.core.test.utils.server.websockets.WebSocketServer.CONNECTION_REPOSITORY_ATTRIBUTE_KEY;

@ServerEndpoint(value = "/endpoint", configurator = ServletAwareConfig.class)
public class WebSocketEndpoint {
  private static final Logger LOG = Logger.getLogger(WebSocketEndpoint.class.getName());
  public static final String WS_REQUEST_KEY = "wsRequest";
  private WebSocketConnection connection;

  @OnOpen
  public void onOpen(final Session session) {
    connection = createWsConnection(session);
  }

  @OnMessage
  public void handleTextMessage(Session session, String message) {
    LOG.log(Level.INFO, "Message received by web socket server: {0}", message);
    connection.addReceivedMessage(message);
  }

  @OnClose
  public void onClose(final Session session) {
    connection.setIsClosed();
  }

  @OnError
  public void onError(final Session session, final Throwable throwable) {
    connection.setIsError(throwable);
  }

  private static WebSocketConnection createWsConnection(Session session) {
    var connectionRepository = getWebSocketConnectionRepository(session);
    var webSocketRequest = (WebSocketRequest) session.getUserProperties().get(WS_REQUEST_KEY);
    var webSocketConnection = new WebSocketConnection(webSocketRequest, session);
    connectionRepository.add(webSocketConnection);
    return webSocketConnection;
  }

  private static WebSocketConnectionRepository getWebSocketConnectionRepository(Session session) {
    return (WebSocketConnectionRepository) session.getUserProperties().get(CONNECTION_REPOSITORY_ATTRIBUTE_KEY);
  }
}
