/*
 * SonarLint Core - Plugin Commons
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
package org.sonarsource.sonarlint.core.plugin.commons.sonarapi;

import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class SonarLintLoggerImpl implements Logger {

  private static final SonarLintLogger logger = SonarLintLogger.get();

  @Override
  public boolean isTraceEnabled() {
    return true;
  }

  @Override
  public void trace(String msg) {
    logger.trace(msg);
  }

  @Override
  public void trace(String pattern, @Nullable Object arg) {
    logger.trace(pattern, arg);
  }

  @Override
  public void trace(String msg, @Nullable Object arg1, @Nullable Object arg2) {
    logger.trace(msg, arg1, arg2);
  }

  @Override
  public void trace(String msg, Object... args) {
    logger.trace(msg, args);
  }

  @Override
  public boolean isDebugEnabled() {
    // Always produce debug logs, the filtering will be handled on client side
    return true;
  }

  @Override
  public void debug(String msg) {
    logger.debug(msg);
  }

  @Override
  public void debug(String pattern, @Nullable Object arg) {
    logger.debug(pattern, arg);
  }

  @Override
  public void debug(String msg, @Nullable Object arg1, @Nullable Object arg2) {
    logger.debug(msg, arg1, arg2);
  }

  @Override
  public void debug(String msg, Object... args) {
    logger.debug(msg, args);
  }

  @Override
  public void info(String msg) {
    logger.info(msg);
  }

  @Override
  public void info(String msg, @Nullable Object arg) {
    logger.info(msg, arg);
  }

  @Override
  public void info(String msg, @Nullable Object arg1, @Nullable Object arg2) {
    logger.info(msg, arg1, arg2);
  }

  @Override
  public void info(String msg, Object... args) {
    logger.info(msg, args);
  }

  @Override
  public void warn(String msg) {
    logger.warn(msg);
  }

  @Override
  public void warn(String msg, Throwable throwable) {
    logger.warn(msg, throwable);
  }

  @Override
  public void warn(String msg, @Nullable Object arg) {
    logger.warn(msg, arg);
  }

  @Override
  public void warn(String msg, @Nullable Object arg1, @Nullable Object arg2) {
    logger.warn(msg, arg1, arg2);
  }

  @Override
  public void warn(String msg, Object... args) {
    logger.warn(msg, args);
  }

  @Override
  public void error(String msg) {
    logger.error(msg);
  }

  @Override
  public void error(String msg, @Nullable Object arg) {
    logger.error(msg, arg);
  }

  @Override
  public void error(String msg, @Nullable Object arg1, @Nullable Object arg2) {
    logger.error(msg, arg1, arg2);
  }

  @Override
  public void error(String msg, Object... args) {
    logger.error(msg, args);
  }

  @Override
  public void error(String msg, Throwable thrown) {
    logger.error(msg, thrown);
  }

  @Override
  public boolean setLevel(LoggerLevel level) {
    return false;
  }

  @Override
  public LoggerLevel getLevel() {
    return LoggerLevel.TRACE;
  }

}
