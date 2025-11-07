/*
 * SonarLint Core - Test Utils
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.test.utils.server.sse;

import java.io.File;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;

public class SSEServer {

  public static final int DEFAULT_PORT = 54321;
  private Tomcat tomcat;
  private SSEServlet sseServlet;

  public void start() {
    try {
      var baseDir = new File("").getAbsoluteFile().getParentFile().getPath();
      tomcat = new Tomcat();
      tomcat.setBaseDir(baseDir);
      tomcat.setPort(DEFAULT_PORT);
      var context = tomcat.addContext("", baseDir);
      sseServlet = new SSEServlet();
      Tomcat.addServlet(context, "sse", sseServlet).addMapping("/");
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
    return "http://localhost:" + DEFAULT_PORT;
  }

  public void sendEventToAllClients(String eventPayload) {
    sseServlet.sendEventToAllClients(eventPayload);
  }

}
