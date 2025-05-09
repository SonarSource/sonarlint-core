/*
 * SonarLint Core - Test Utils
 * Copyright (C) 2016-2025 SonarSource SA
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

import java.io.File;
import java.util.List;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;

public class WebSocketServer {

  public static final int DEFAULT_PORT = 54321;
  public static final String CONNECTION_REPOSITORY_ATTRIBUTE_KEY = "connectionRepository";
  private Tomcat tomcat;
  private WebSocketConnectionRepository connectionRepository;
  private final int port;

  public WebSocketServer(int port) {
    this.port = port;
  }

  public WebSocketServer() {
    this(DEFAULT_PORT);
  }

  public void start() {
    try {
      var baseDir = new File("").getAbsoluteFile().getParentFile().getPath();
      tomcat = new Tomcat();
      tomcat.setBaseDir(baseDir);
      tomcat.setPort(port);
      var context = tomcat.addContext("", baseDir);
      connectionRepository = new WebSocketConnectionRepository();
      context.getServletContext().setAttribute(CONNECTION_REPOSITORY_ATTRIBUTE_KEY, connectionRepository);
      context.addApplicationListener(ContextListener.class.getName());
      Tomcat.addServlet(context, "dummy", new DefaultServlet()).addMapping("/");
      // needed to start the endpoint
      tomcat.getConnector();
      tomcat.start();
    } catch (LifecycleException e) {
      throw new IllegalStateException(e);
    }
  }

  public void stop() {
    try {
      tomcat.stop();
      tomcat.destroy();
    } catch (LifecycleException e) {
      throw new IllegalStateException(e);
    }
  }

  public String getUrl() {
    return "ws://localhost:" + port + "/endpoint";
  }

  public List<WebSocketConnection> getConnections() {
    return connectionRepository.getConnections();
  }

}
