/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;

import javax.annotation.Nullable;

class LogCallbackAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
  protected LogOutput target;
  protected boolean verbose = false;
  private final Appender<ILoggingEvent> defaultAppender;

  LogCallbackAppender(Appender<ILoggingEvent> defaultAppender) {
    this.defaultAppender = defaultAppender;
  }

  public void setTarget(@Nullable LogOutput target) {
    this.target = target;
  }

  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  @Override
  protected void append(ILoggingEvent event) {
    Level level = event.getLevel();
    if (!verbose && !level.isGreaterOrEqual(Level.INFO)) {
      return;
    }

    if (target == null) {
      defaultAppender.doAppend(event);
      return;
    }

    target.log(event.getFormattedMessage(), translate(level));
  }

  private static LogOutput.Level translate(Level level) {
    switch (level.toInt()) {
      case Level.ERROR_INT:
        return LogOutput.Level.ERROR;
      case Level.WARN_INT:
        return LogOutput.Level.WARN;
      case Level.INFO_INT:
        return LogOutput.Level.INFO;
      case Level.TRACE_INT:
        return LogOutput.Level.TRACE;
      case Level.DEBUG_INT:
      default:
        return LogOutput.Level.DEBUG;
    }
  }
}
