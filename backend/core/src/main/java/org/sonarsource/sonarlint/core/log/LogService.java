/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.log;

import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.log.LogLevel;

public class LogService {

  public void setLogLevel(LogLevel newLevel) {
    SonarLintLogger.get().setLevel(convert(newLevel));
  }

  public static LogOutput.Level convert(LogLevel level) {
    return switch (level) {
      case OFF -> LogOutput.Level.OFF;
      case ERROR -> LogOutput.Level.ERROR;
      case INFO -> LogOutput.Level.INFO;
      case WARN -> LogOutput.Level.WARN;
      case DEBUG -> LogOutput.Level.DEBUG;
      case TRACE -> LogOutput.Level.TRACE;
    };
  }

}
