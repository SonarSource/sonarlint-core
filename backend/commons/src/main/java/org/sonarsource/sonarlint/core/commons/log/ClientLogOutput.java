/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.log;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Allow to redirect SonarLint logs to a custom output on client side
 */
public interface ClientLogOutput {

  void log(String formattedMessage, Level level);

  enum Level {
    ERROR, WARN, INFO, DEBUG, TRACE;
  }

  static String stackTraceToString(Throwable t) {
    var stringWriter = new StringWriter();
    var printWriter = new PrintWriter(stringWriter);
    t.printStackTrace(printWriter);
    return stringWriter.toString();
  }
}
