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
package org.sonar.api.utils.log;

import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * This class can't be moved to another package because {@link BaseLogger} is not public. And we have to extend BaseLogger to please {@link DefaultProfiler}.
 */
public class SonarApiLogger extends BaseLogger {

  private static final SonarLintLogger logger = SonarLintLogger.get();

  @Override
  public boolean isTraceEnabled() {
    return false;
  }

  @Override
  public void doTrace(String msg) {
  }

  @Override
  public void doTrace(String pattern, Object arg) {
  }

  @Override
  public void doTrace(String msg, Object arg1, Object arg2) {
  }

  @Override
  public void doTrace(String msg, Object... args) {
  }

  @Override
  public boolean isDebugEnabled() {
    // Always produce debug logs, the filtering will be handled on client side
    return true;
  }

  @Override
  public void doDebug(String msg) {
    logger.debug(msg);
  }

  @Override
  public void doDebug(String pattern, Object arg) {
    logger.debug(pattern, arg);
  }

  @Override
  public void doDebug(String msg, Object arg1, Object arg2) {
    logger.debug(msg, arg1, arg2);
  }

  @Override
  public void doDebug(String msg, Object... args) {
    logger.debug(msg, args);
  }

  @Override
  public void doInfo(String msg) {
    logger.info(msg);
  }

  @Override
  public void doInfo(String msg, Object arg) {
    logger.info(msg, arg);
  }

  @Override
  public void doInfo(String msg, Object arg1, Object arg2) {
    logger.info(msg, arg1, arg2);
  }

  @Override
  public void doInfo(String msg, Object... args) {
    logger.info(msg, args);
  }

  @Override
  public void doWarn(String msg) {
    logger.warn(msg);
  }

  @Override
  public void doWarn(String msg, Throwable throwable) {
    logger.warn(msg, throwable);
  }

  @Override
  public void doWarn(String msg, Object arg) {
    logger.warn(msg, arg);
  }

  @Override
  public void doWarn(String msg, Object arg1, Object arg2) {
    logger.warn(msg, arg1, arg2);
  }

  @Override
  public void doWarn(String msg, Object... args) {
    logger.warn(msg, args);
  }

  @Override
  public void doError(String msg) {
    logger.error(msg);
  }

  @Override
  public void doError(String msg, Object arg) {
    logger.error(msg, arg);
  }

  @Override
  public void doError(String msg, Object arg1, Object arg2) {
    logger.error(msg, arg1, arg2);
  }

  @Override
  public void doError(String msg, Object... args) {
    logger.error(msg, args);
  }

  @Override
  public void doError(String msg, Throwable thrown) {
    logger.error(msg, thrown);
  }

  @Override
  public boolean setLevel(LoggerLevel level) {
    return false;
  }

  @Override
  public LoggerLevel getLevel() {
    return LoggerLevel.DEBUG;
  }

}
