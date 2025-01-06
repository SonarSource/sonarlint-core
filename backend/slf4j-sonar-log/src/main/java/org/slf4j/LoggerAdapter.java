/*
 * SonarLint Core - SLF4J log adaptor
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.slf4j;

import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level;

/**
 * A slf4j logger that delegates all logs to a sonar API logger. 
 */
public class LoggerAdapter implements Logger {
  private final Level loggerLevel;
  private final SonarLintLogger sonarLogger;

  public LoggerAdapter(SonarLintLogger sonarLogger) {
    this(sonarLogger, Level.DEBUG);
  }

  public LoggerAdapter(SonarLintLogger sonarLogger, Level loggerLevel) {
    this.sonarLogger = sonarLogger;
    this.loggerLevel = loggerLevel;
  }

  @Override
  public String getName() {
    return "Adapter to sonar api logs";
  }

  @Override
  public boolean isTraceEnabled() {
    return loggerLevel.isEnabledForLevel(Level.TRACE);
  }

  @Override
  public void trace(String msg) {
    if (isTraceEnabled()) {
      sonarLogger.trace(msg);
    }
  }

  @Override
  public void trace(String format, Object arg) {
    if (isTraceEnabled()) {
      sonarLogger.trace(format, arg);
    }
  }

  @Override
  public void trace(String format, Object arg1, Object arg2) {
    if (isTraceEnabled()) {
      sonarLogger.trace(format, arg1, arg2);
    }
  }

  @Override
  public void trace(String format, Object[] argArray) {
    if (isTraceEnabled()) {
      sonarLogger.trace(format, argArray);
    }
  }

  @Override
  public void trace(String msg, Throwable t) {
    if (isTraceEnabled()) {
      sonarLogger.trace(msg, t);
    }
  }

  @Override
  public boolean isTraceEnabled(Marker marker) {
    return isTraceEnabled();
  }

  @Override
  public void trace(Marker marker, String msg) {
    trace(msg);
  }

  @Override
  public void trace(Marker marker, String format, Object arg) {
    trace(format, arg);
  }

  @Override
  public void trace(Marker marker, String format, Object arg1, Object arg2) {
    trace(format, arg1, arg2);
  }

  @Override
  public void trace(Marker marker, String format, Object[] argArray) {
    trace(format, argArray);
  }

  @Override
  public void trace(Marker marker, String msg, Throwable t) {
    trace(msg, t);
  }

  @Override
  public boolean isDebugEnabled() {
    return loggerLevel.isEnabledForLevel(Level.DEBUG);
  }

  @Override
  public void debug(String msg) {
    if (isDebugEnabled()) {
      sonarLogger.debug(msg);
    }
  }

  @Override
  public void debug(String format, Object arg) {
    if (isDebugEnabled()) {
      sonarLogger.debug(format, arg);
    }
  }

  @Override
  public void debug(String format, Object arg1, Object arg2) {
    if (isDebugEnabled()) {
      sonarLogger.debug(format, arg1, arg2);
    }
  }

  @Override
  public void debug(String format, Object[] argArray) {
    if (isDebugEnabled()) {
      sonarLogger.debug(format, argArray);
    }
  }

  @Override
  public void debug(String msg, Throwable t) {
    if (isDebugEnabled()) {
      sonarLogger.debug(msg);
    }
  }

  @Override
  public boolean isDebugEnabled(Marker marker) {
    return isDebugEnabled();
  }

  @Override
  public void debug(Marker marker, String msg) {
    debug(msg);
  }

  @Override
  public void debug(Marker marker, String format, Object arg) {
    debug(format, arg);
  }

  @Override
  public void debug(Marker marker, String format, Object arg1, Object arg2) {
    debug(format, arg1, arg2);
  }

  @Override
  public void debug(Marker marker, String format, Object[] argArray) {
    debug(format, argArray);
  }

  @Override
  public void debug(Marker marker, String msg, Throwable t) {
    debug(msg, t);
  }

  @Override
  public boolean isInfoEnabled() {
    return loggerLevel.isEnabledForLevel(Level.INFO);
  }

  @Override
  public void info(String msg) {
    if (isInfoEnabled()) {
      sonarLogger.info(msg);
    }
  }

  @Override
  public void info(String format, Object arg) {
    if (isInfoEnabled()) {
      sonarLogger.info(format, arg);
    }
  }

  @Override
  public void info(String format, Object arg1, Object arg2) {
    if (isInfoEnabled()) {
      sonarLogger.info(format, arg1, arg2);
    }
  }

  @Override
  public void info(String format, Object[] argArray) {
    if (isInfoEnabled()) {
      sonarLogger.info(format, argArray);
    }
  }

  @Override
  public void info(String msg, Throwable t) {
    if (isInfoEnabled()) {
      sonarLogger.info(msg);
    }
  }

  @Override
  public boolean isInfoEnabled(Marker marker) {
    return isInfoEnabled();
  }

  @Override
  public void info(Marker marker, String msg) {
    info(msg);
  }

  @Override
  public void info(Marker marker, String format, Object arg) {
    info(format, arg);
  }

  @Override
  public void info(Marker marker, String format, Object arg1, Object arg2) {
    info(format, arg1, arg2);
  }

  @Override
  public void info(Marker marker, String format, Object[] argArray) {
    info(format, argArray);
  }

  @Override
  public void info(Marker marker, String msg, Throwable t) {
    info(msg, t);
  }

  @Override
  public boolean isWarnEnabled() {
    return loggerLevel.isEnabledForLevel(Level.WARN);
  }

  @Override
  public void warn(String msg) {
    if (isWarnEnabled()) {
      sonarLogger.warn(msg);
    }
  }

  @Override
  public void warn(String format, Object arg) {
    if (isWarnEnabled()) {
      sonarLogger.warn(format, arg);
    }
  }

  @Override
  public void warn(String format, Object arg1, Object arg2) {
    if (isWarnEnabled()) {
      sonarLogger.warn(format, arg1, arg2);
    }
  }

  @Override
  public void warn(String format, Object[] argArray) {
    if (isWarnEnabled()) {
      sonarLogger.warn(format, argArray);
    }
  }

  @Override
  public void warn(String msg, Throwable t) {
    if (isWarnEnabled()) {
      sonarLogger.warn(msg, t);
    }
  }

  @Override
  public boolean isWarnEnabled(Marker marker) {
    return isWarnEnabled();
  }

  @Override
  public void warn(Marker marker, String msg) {
    warn(msg);
  }

  @Override
  public void warn(Marker marker, String format, Object arg) {
    warn(format, arg);
  }

  @Override
  public void warn(Marker marker, String format, Object arg1, Object arg2) {
    warn(format, arg1, arg2);
  }

  @Override
  public void warn(Marker marker, String format, Object[] argArray) {
    warn(format, argArray);
  }

  @Override
  public void warn(Marker marker, String msg, Throwable t) {
    warn(msg);
  }

  @Override
  public boolean isErrorEnabled() {
    return loggerLevel.isEnabledForLevel(Level.ERROR);
  }

  @Override
  public void error(String msg) {
    if (isErrorEnabled()) {
      sonarLogger.error(msg);
    }
  }

  @Override
  public void error(String format, Object arg) {
    if (isErrorEnabled()) {
      sonarLogger.error(format, arg);
    }
  }

  @Override
  public void error(String format, Object arg1, Object arg2) {
    if (isErrorEnabled()) {
      sonarLogger.error(format, arg1, arg2);
    }
  }

  @Override
  public void error(String format, Object[] argArray) {
    if (isErrorEnabled()) {
      sonarLogger.error(format, argArray);
    }
  }

  @Override
  public void error(String msg, Throwable t) {
    if (isErrorEnabled()) {
      sonarLogger.error(msg);
    }
  }

  @Override
  public boolean isErrorEnabled(Marker marker) {
    return isErrorEnabled();
  }

  @Override
  public void error(Marker marker, String msg) {
    error(msg);
  }

  @Override
  public void error(Marker marker, String format, Object arg) {
    error(format, arg);
  }

  @Override
  public void error(Marker marker, String format, Object arg1, Object arg2) {
    error(format, arg1, arg2);
  }

  @Override
  public void error(Marker marker, String format, Object[] argArray) {
    error(format, argArray);
  }

  @Override
  public void error(Marker marker, String msg, Throwable t) {
    error(msg, t);
  }

}
