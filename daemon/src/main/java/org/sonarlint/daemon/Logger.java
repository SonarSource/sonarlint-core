/*
 * SonarLint Daemon
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
package org.sonarlint.daemon;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
  private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

  public synchronized void info(String log) {
    System.out.println(format(log, "INFO"));
  }

  private static String format(String log, String level) {
    return String.format("%s [%s] %s %s", TIME_FORMAT.format(new Date()), Thread.currentThread().getName(), level, log);
  }

  public synchronized void error(String log, Exception e) {
    System.err.println(format(log, "ERROR"));
    e.printStackTrace();
  }
}
