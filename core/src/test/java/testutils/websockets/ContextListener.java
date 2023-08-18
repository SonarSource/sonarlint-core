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

import jakarta.servlet.ServletContextEvent;
import org.apache.tomcat.websocket.server.WsContextListener;
import org.apache.tomcat.websocket.server.WsServerContainer;

import static org.apache.tomcat.websocket.server.Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE;

public class ContextListener extends WsContextListener {
  @Override
  public void contextInitialized(final ServletContextEvent sce) {
    super.contextInitialized(sce);

    sce.getServletContext().addListener(new RequestListener());
    var sc = (WsServerContainer) sce.getServletContext().getAttribute(SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
    try {
      sc.addEndpoint(WebSocketEndpoint.class);
    } catch (jakarta.websocket.DeploymentException e) {
      throw new RuntimeException(e);
    }
  }
}
