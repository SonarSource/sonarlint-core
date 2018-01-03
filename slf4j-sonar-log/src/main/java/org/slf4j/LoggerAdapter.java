/*
 * SonarLint slf4j log adaptor
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
package org.slf4j;

import org.sonar.api.utils.log.Loggers;

/**
 * A slf4j logger that delegates all logs to a sonar API logger. 
 */
public class LoggerAdapter implements Logger {
  private static final org.sonar.api.utils.log.Logger SONAR_LOGGER = Loggers.get("SonarLint");

  @Override
  public String getName() {
    return "Adapter to sonar api logs";
  }

  @Override
  public boolean isTraceEnabled() {
    return false;
  }

  @Override
  public void trace(String msg) {
    SONAR_LOGGER.trace(msg);
  }

  @Override
  public void trace(String format, Object arg) {
    SONAR_LOGGER.trace(format, arg);
  }

  @Override
  public void trace(String format, Object arg1, Object arg2) {
    SONAR_LOGGER.trace(format, arg1, arg2);

  }

  @Override
  public void trace(String format, Object[] argArray) {
    SONAR_LOGGER.trace(format, argArray);

  }

  @Override
  public void trace(String msg, Throwable t) {
    SONAR_LOGGER.trace(msg, t);
  }

  @Override
  public boolean isTraceEnabled(Marker marker) {
    return false;
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
    return true;
  }

  @Override
  public void debug(String msg) {
    SONAR_LOGGER.debug(msg);
  }

  @Override
  public void debug(String format, Object arg) {
    SONAR_LOGGER.debug(format, arg);
  }

  @Override
  public void debug(String format, Object arg1, Object arg2) {
    SONAR_LOGGER.debug(format, arg1, arg2);

  }

  @Override
  public void debug(String format, Object[] argArray) {
    SONAR_LOGGER.debug(format, argArray);

  }

  @Override
  public void debug(String msg, Throwable t) {
    SONAR_LOGGER.debug(msg);
  }

  @Override
  public boolean isDebugEnabled(Marker marker) {
    return true;
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
    return true;
  }

  @Override
  public void info(String msg) {
    SONAR_LOGGER.info(msg);
  }

  @Override
  public void info(String format, Object arg) {
    SONAR_LOGGER.info(format, arg);
  }

  @Override
  public void info(String format, Object arg1, Object arg2) {
    SONAR_LOGGER.info(format, arg1, arg2);

  }

  @Override
  public void info(String format, Object[] argArray) {
    SONAR_LOGGER.info(format, argArray);

  }

  @Override
  public void info(String msg, Throwable t) {
    SONAR_LOGGER.info(msg);
  }

  @Override
  public boolean isInfoEnabled(Marker marker) {
    return true;
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
    return true;
  }

  @Override
  public void warn(String msg) {
    SONAR_LOGGER.warn(msg);
  }

  @Override
  public void warn(String format, Object arg) {
    SONAR_LOGGER.warn(format, arg);
  }

  @Override
  public void warn(String format, Object arg1, Object arg2) {
    SONAR_LOGGER.warn(format, arg1, arg2);

  }

  @Override
  public void warn(String format, Object[] argArray) {
    SONAR_LOGGER.warn(format, argArray);

  }

  @Override
  public void warn(String msg, Throwable t) {
    SONAR_LOGGER.warn(msg);
  }

  @Override
  public boolean isWarnEnabled(Marker marker) {
    return true;
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
    warn(msg, t);

  }

  @Override
  public boolean isErrorEnabled() {
    return true;
  }

  @Override
  public void error(String msg) {
    SONAR_LOGGER.error(msg);
  }

  @Override
  public void error(String format, Object arg) {
    SONAR_LOGGER.error(format, arg);
  }

  @Override
  public void error(String format, Object arg1, Object arg2) {
    SONAR_LOGGER.error(format, arg1, arg2);

  }

  @Override
  public void error(String format, Object[] argArray) {
    SONAR_LOGGER.error(format, argArray);

  }

  @Override
  public void error(String msg, Throwable t) {
    SONAR_LOGGER.error(msg);
  }

  @Override
  public boolean isErrorEnabled(Marker marker) {
    return true;
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
