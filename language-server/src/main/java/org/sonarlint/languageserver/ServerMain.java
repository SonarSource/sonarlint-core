/*
 * SonarLint Language Server
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarlint.languageserver;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerMain {

  private static final Logger LOG = LoggerFactory.getLogger(ServerMain.class);

  private ServerMain() {
  }

  public static void main(String[] args) {
    if (args.length < 2) {
      LOG.error("Usage: java -jar sonarlint-server.jar <jsonRpcPort> <httpPort>");
      System.exit(1);
    }
    int jsonRpcPort = Integer.parseInt(args[0]);
    int httpPort = Integer.parseInt(args[1]);

    LOG.info("Connecting to {}", jsonRpcPort);
    SonarLintLanguageServer languageServer;
    try {
      languageServer = new SonarLintLanguageServer(jsonRpcPort);
    } catch (IOException e) {
      LOG.error("Unable to connect to the client", e);
      System.exit(1);
      return;
    }

    LOG.info("Starting HTTP server on {}", httpPort);
    try {
      new RuleDescriptionHttpServer(httpPort, languageServer.getEngine()).start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
    } catch (IOException e) {
      LOG.error("Unable to start the HTTP server", e);
      languageServer.shutdown();
      languageServer.exit();
      System.exit(1);
    }
  }

}
