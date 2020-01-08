/*
 * SonarLint Daemon
 * Copyright (C) 2009-2020 SonarSource SA
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
package org.sonarlint.daemon;

import java.text.ParseException;
import javax.annotation.CheckForNull;

public class Options {
  private boolean help = false;
  private String port = null;

  public static Options parse(String[] args) throws ParseException {
    Options options = new Options();

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];

      if ("-h".equals(arg) || "--help".equals(arg)) {
        options.help = true;
      } else if ("--port".equals(arg)) {
        i++;
        checkAdditionalArg(i, args.length, arg);
        options.port = args[i];
      } else {
        throw new ParseException("Unrecognized option: " + arg, i);
      }
    }

    return options;
  }

  private static void checkAdditionalArg(int i, int argsLength, String arg) throws ParseException {
    if (i >= argsLength) {
      throw new ParseException("Missing argument for option " + arg, i);
    }
  }

  public boolean isHelp() {
    return this.help;
  }

  @CheckForNull
  public Integer getPort() {
    return port == null ? null : Integer.parseInt(port);
  }

  public static void printUsage() {
    System.out.println("");
    System.out.println("usage: sonarlint-daemon [options]");
    System.out.println("");
    System.out.println("Options:");
    System.out.println(" -h,--help              Display help information");
    System.out.println(" --port <port>          Network port to listen to");
  }

}
