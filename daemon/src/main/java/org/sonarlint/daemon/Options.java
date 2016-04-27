/*
 * SonarLint Daemon
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;

import java.text.ParseException;

public class Options {
  private static final Logger LOGGER = LoggerFactory.getLogger(Options.class);
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
  public String getPort() {
    return port;
  }

  public static void printUsage() {
    LOGGER.info("");
    LOGGER.info("usage: sonarlint-daemon [options]");
    LOGGER.info("");
    LOGGER.info("Options:");
    LOGGER.info(" -h,--help              Display help information");
    LOGGER.info(" --port <port>          Network port to listen to");
  }

}
