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

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.LogOutput.Level;

/**
 * This is the logging facade to be used in SonarLint core.
 */
public class SonarLintLogger {
  private static final SonarLintLogger logger = new SonarLintLogger(new LogOutputDelegator());
  private final LogOutputDelegator logOutput;

  public static SonarLintLogger get() {
    return logger;
  }

  public static void setTarget(@Nullable LogOutput output) {
    logger.logOutput.setTarget(output);
  }

  /**
   * In some cases, the log output is not properly inherited by the "child" threads (especially when using shared thread pools).
   * We have to copy the log output manually, in a similar way to https://logback.qos.ch/manual/mdc.html#managedThreads
   */
  @CheckForNull
  public static LogOutput getTargetForCopy() {
    return logger.logOutput.getTarget();
  }

  SonarLintLogger(LogOutputDelegator logOutput) {
    this.logOutput = logOutput;
  }

  public void trace(String msg) {
    logOutput.log(msg, Level.TRACE, (Throwable) null);
  }

  public void trace(String msg, @Nullable Object arg) {
    doLogExtractingThrowable(Level.TRACE, msg, new Object[]{arg});
  }

  public void trace(String msg, @Nullable Object arg1, @Nullable Object arg2) {
    doLogExtractingThrowable(Level.TRACE, msg, new Object[]{arg1, arg2});
  }

  public void trace(String msg, Object... args) {
    doLogExtractingThrowable(Level.TRACE, msg, args);
  }

  public void debug(String msg) {
    logOutput.log(msg, Level.DEBUG, (Throwable) null);
  }

  public void debug(String msg, @Nullable Object arg) {
    doLogExtractingThrowable(Level.DEBUG, msg, new Object[]{arg});
  }

  public void debug(String msg, @Nullable Object arg1, @Nullable Object arg2) {
    doLogExtractingThrowable(Level.DEBUG, msg, new Object[]{arg1, arg2});
  }

  public void debug(String msg, Object... args) {
    doLogExtractingThrowable(Level.DEBUG, msg, args);
  }

  public void info(String msg) {
    logOutput.log(msg, Level.INFO, (Throwable) null);
  }

  public void info(String msg, @Nullable Object arg) {
    doLogExtractingThrowable(Level.INFO, msg, new Object[]{arg});
  }

  public void info(String msg, @Nullable Object arg1, @Nullable Object arg2) {
    doLogExtractingThrowable(Level.INFO, msg, new Object[]{arg1, arg2});
  }

  public void info(String msg, Object... args) {
    doLogExtractingThrowable(Level.INFO, msg, args);
  }

  public void warn(String msg) {
    logOutput.log(msg, Level.WARN, (Throwable) null);
  }

  public void warn(String msg, Throwable thrown) {
    logOutput.log(msg, Level.WARN, thrown);
  }

  public void warn(String msg, @Nullable Object arg) {
    doLogExtractingThrowable(Level.WARN, msg, new Object[]{arg});
  }

  public void warn(String msg, @Nullable Object arg1, @Nullable Object arg2) {
    doLogExtractingThrowable(Level.WARN, msg, new Object[]{arg1, arg2});
  }

  public void warn(String msg, Object... args) {
    doLogExtractingThrowable(Level.WARN, msg, args);
  }

  public void error(String msg) {
    logOutput.log(msg, Level.ERROR, (Throwable) null);
  }

  public void error(String msg, @Nullable Object arg) {
    doLogExtractingThrowable(Level.ERROR, msg, new Object[]{arg});
  }

  public void error(String msg, @Nullable Object arg1, @Nullable Object arg2) {
    doLogExtractingThrowable(Level.ERROR, msg, new Object[]{arg1, arg2});
  }

  public void error(String msg, Object... args) {
    doLogExtractingThrowable(Level.ERROR, msg, args);
  }

  public void error(String msg, Throwable thrown) {
    logOutput.log(msg, Level.ERROR, thrown);
  }

  private void doLogExtractingThrowable(Level level, String msg, Object[] argArray) {
    var tuple = MessageFormatter.arrayFormat(msg, argArray);
    logOutput.log(tuple.getMessage(), level, tuple.getThrowable());
  }

  public static String singlePlural(int count, String singular, String plural) {
    return count == 1 ? singular : plural;
  }

}
