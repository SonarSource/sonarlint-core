/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.util.LoggedErrorHandler;

class LogCallbackAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
  private final InheritableThreadLocal<LogOutput> tlTtarget;
  private final InheritableThreadLocal<LoggedErrorHandler> errorHandler;
  private final Appender<ILoggingEvent> defaultAppender;

  LogCallbackAppender(Appender<ILoggingEvent> defaultAppender) {
    this.tlTtarget = new InheritableThreadLocal<>();
    this.errorHandler = new InheritableThreadLocal<>();
    this.defaultAppender = defaultAppender;
  }

  public void setTarget(@Nullable LogOutput target) {
    this.tlTtarget.set(target);
  }

  public void setErrorHandler(@Nullable LoggedErrorHandler errorHandler) {
    this.errorHandler.set(errorHandler);
  }

  @Override
  protected void append(ILoggingEvent event) {
    LogOutput target = tlTtarget.get();
    if (target == null) {
      defaultAppender.doAppend(event);
      return;
    }

    String msg;
    if (event.getThrowableProxy() == null) {
      msg = event.getFormattedMessage();
    } else {
      ExtendedThrowableProxyConverter throwableConverter = new ExtendedThrowableProxyConverter();
      throwableConverter.start();
      msg = event.getFormattedMessage() + "\n" + throwableConverter.convert(event);
      throwableConverter.stop();
    }

    handleErrors(event);
    target.log(msg, translate(event.getLevel()));
  }

  private void handleErrors(ILoggingEvent event) {
    if (event.getLevel().equals(Level.ERROR)) {
      LoggedErrorHandler handler = errorHandler.get();
      if (handler != null) {
        handler.handleError(event.getFormattedMessage());
        if (event.getThrowableProxy() != null) {
          handler.handleException(event.getThrowableProxy().getClassName());
        }
      }
    }
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
