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

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

import static testutils.websockets.WebSocketEndpoint.WS_CONNECTION_USER_PROPERTY_KEY;
import static testutils.websockets.WebSocketServer.CONNECTION_REPOSITORY_ATTRIBUTE_KEY;

public class ServletAwareConfig extends ServerEndpointConfig.Configurator {
  @Override
  public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
    var webSocketConnection = new WebSocketConnection(request.getHeaders().get("Authorization").get(0));
    getWebSocketConnectionRepository(request).add(webSocketConnection);
    config.getUserProperties().put(WS_CONNECTION_USER_PROPERTY_KEY, webSocketConnection);
    super.modifyHandshake(config, request, response);
  }

  private static WebSocketConnectionRepository getWebSocketConnectionRepository(HandshakeRequest request) {
    HttpSession httpSession = (HttpSession) request.getHttpSession();
    return (WebSocketConnectionRepository) httpSession.getServletContext().getAttribute(CONNECTION_REPOSITORY_ATTRIBUTE_KEY);
  }

}
