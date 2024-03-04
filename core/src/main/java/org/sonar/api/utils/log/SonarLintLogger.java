/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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

import org.sonarsource.sonarlint.core.client.api.common.LogOutput.Level;
import org.sonarsource.sonarlint.core.log.EventArgUtil;
import org.sonarsource.sonarlint.core.log.LogOutputDelegator;
import org.sonarsource.sonarlint.core.log.MessageFormat;

/**
 * This class can't be moved to another package because {@link BaseLogger} is not public.
 */
public class SonarLintLogger extends BaseLogger {
  private final LogOutputDelegator logOutput;

  public SonarLintLogger(LogOutputDelegator logOutput) {
    this.logOutput = logOutput;
  }

  @Override
  void doTrace(String msg) {
    // no trace logs
  }

  @Override
  void doTrace(String msg, Object arg) {
    // no trace logs
  }

  @Override
  void doTrace(String msg, Object arg1, Object arg2) {
    // no trace logs
  }

  @Override
  void doTrace(String msg, Object... args) {
    // no trace logs
  }

  @Override
  void doDebug(String msg) {
    logOutput.log(msg, Level.DEBUG);

  }

  @Override
  void doDebug(String msg, Object arg) {
    doLogExtractingThrowable(Level.DEBUG, msg, new Object[] {arg});

  }

  @Override
  void doDebug(String msg, Object arg1, Object arg2) {
    doLogExtractingThrowable(Level.DEBUG, msg, new Object[] {arg1, arg2});
  }

  @Override
  void doDebug(String msg, Object... args) {
    doLogExtractingThrowable(Level.DEBUG, msg, args);
  }

  @Override
  void doInfo(String msg) {
    logOutput.log(msg, Level.INFO);

  }

  @Override
  void doInfo(String msg, Object arg) {
    doLogExtractingThrowable(Level.INFO, msg, new Object[] {arg});
  }

  @Override
  void doInfo(String msg, Object arg1, Object arg2) {
    doLogExtractingThrowable(Level.INFO, msg, new Object[] {arg1, arg2});
  }

  @Override
  void doInfo(String msg, Object... args) {
    doLogExtractingThrowable(Level.INFO, msg, args);
  }

  @Override
  void doWarn(String msg) {
    logOutput.log(msg, Level.WARN);
  }

  @Override
  void doWarn(String msg, Throwable thrown) {
    logOutput.log(msg, Level.WARN, thrown);
  }

  @Override
  void doWarn(String msg, Object arg) {
    doLogExtractingThrowable(Level.WARN, msg, new Object[] {arg});
  }

  @Override
  void doWarn(String msg, Object arg1, Object arg2) {
    doLogExtractingThrowable(Level.WARN, msg, new Object[] {arg1, arg2});
  }

  @Override
  void doWarn(String msg, Object... args) {
    doLogExtractingThrowable(Level.WARN, msg, args);
  }

  @Override
  void doError(String msg) {
    logOutput.log(msg, Level.ERROR);
  }

  @Override
  void doError(String msg, Object arg) {
    doLogExtractingThrowable(Level.ERROR, msg, new Object[] {arg});
  }

  @Override
  void doError(String msg, Object arg1, Object arg2) {
    doLogExtractingThrowable(Level.ERROR, msg, new Object[] {arg1, arg2});
  }

  @Override
  void doError(String msg, Object... args) {
    doLogExtractingThrowable(Level.ERROR, msg, args);
  }

  @Override
  void doError(String msg, Throwable thrown) {
    logOutput.log(msg, Level.ERROR, thrown);
  }

  private void doLogExtractingThrowable(Level level, String msg, Object[] argArray) {
    Throwable extractedThrowable = EventArgUtil.extractThrowable(argArray);
    if (EventArgUtil.successfulExtraction(extractedThrowable)) {
      Object[] trimmedArgArray = EventArgUtil.trimmedCopy(argArray);
      logOutput.log(MessageFormat.format(msg, trimmedArgArray), level, extractedThrowable);
    } else {
      logOutput.log(MessageFormat.format(msg, argArray), level);
    }
  }

  @Override
  public boolean isTraceEnabled() {
    return false;
  }

  @Override
  public boolean isDebugEnabled() {
    return true;
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
