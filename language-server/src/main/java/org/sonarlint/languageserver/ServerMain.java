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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerMain {

  private static final Logger LOG = LoggerFactory.getLogger(ServerMain.class);

  private ServerMain() {
  }

  public static void main(String... args) {
    if (args.length < 1) {
      LOG.error("Usage: java -jar sonarlint-server.jar <jsonRpcPort> [file:///path/to/analyzer1.jar [file:///path/to/analyzer2.jar] ...]");
      System.exit(1);
    }
    int jsonRpcPort = parsePortArgument(args);

    Collection<URL> analyzers = new ArrayList<>();
    if (args.length > 1) {
      for (int i = 1; i < args.length; i++) {
        try {
          analyzers.add(new URL(args[i]));
        } catch (MalformedURLException e) {
          LOG.error("Invalid " + i + "th argument. Expected an URL.", e);
          System.exit(1);
        }
      }
    }

    LOG.info("Binding to {}", jsonRpcPort);
    try {
      SonarLintLanguageServer.bySocket(jsonRpcPort, analyzers);
    } catch (IOException e) {
      LOG.error("Unable to connect to the client", e);
      System.exit(1);
      return;
    }

  }

  private static int parsePortArgument(String... args) {
    try {
      return Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      LOG.error("Invalid port provided as first parameter", e);
      System.exit(1);
    }
    return 0;
  }

}
