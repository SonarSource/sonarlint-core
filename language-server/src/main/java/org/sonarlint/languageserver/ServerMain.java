/*
 * SonarLint Language Server
 * Copyright (C) 2009-2019 SonarSource SA
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

public class ServerMain {

  private ServerMain() {
  }

  public static void main(String... args) {
    if (args.length < 1) {
      System.err.println("Usage: java -jar sonarlint-server.jar <jsonRpcPort> [file:///path/to/analyzer1.jar [file:///path/to/analyzer2.jar] ...]");
      System.exit(1);
    }
    int jsonRpcPort = parsePortArgument(args);

    Collection<URL> analyzers = new ArrayList<>();
    if (args.length > 1) {
      for (int i = 1; i < args.length; i++) {
        try {
          analyzers.add(new URL(args[i]));
        } catch (MalformedURLException e) {
          System.err.println("Invalid " + i + "th argument. Expected an URL.");
          e.printStackTrace(System.err);
          System.exit(1);
        }
      }
    }

    System.out.println("Binding to " + jsonRpcPort);
    try {
      SonarLintLanguageServer.bySocket(jsonRpcPort, analyzers);
    } catch (IOException e) {
      System.err.println("Unable to connect to the client");
      e.printStackTrace(System.err);
      System.exit(1);
      return;
    }

  }

  private static int parsePortArgument(String... args) {
    try {
      return Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      System.err.println("Invalid port provided as first parameter");
      e.printStackTrace(System.err);
      System.exit(1);
    }
    return 0;
  }

}
