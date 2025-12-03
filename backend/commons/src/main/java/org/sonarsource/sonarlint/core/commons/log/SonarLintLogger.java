/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.log;

import io.sentry.Sentry;
import io.sentry.SentryLogLevel;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.LogOutput.Level;

/**
 * This is the logging facade to be used in SonarLint core.
 */
public class SonarLintLogger {
  private static final SonarLintLogger logger = new SonarLintLogger();
  private Level currentLevel = Level.OFF;

  public static SonarLintLogger get() {
    return logger;
  }

  private final InheritableThreadLocal<LogOutput> target = new InheritableThreadLocal<>();

  SonarLintLogger() {
    // singleton class
  }

  public void setTarget(@Nullable LogOutput target) {
    this.target.set(target);
  }

  /**
   * In some cases, the log output is not properly inherited by the "child" threads (especially when using shared thread pools).
   * We have to copy the log output manually, in a similar way to https://logback.qos.ch/manual/mdc.html#managedThreads
   */
  @CheckForNull
  public LogOutput getTargetForCopy() {
    return this.target.get();
  }

  public void trace(String msg) {
    log(msg, Level.TRACE, (Throwable) null);
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
    log(msg, Level.DEBUG, (Throwable) null);
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
    log(msg, Level.INFO, (Throwable) null);
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
    log(msg, Level.WARN, (Throwable) null);
  }

  public void warn(String msg, Throwable thrown) {
    log(msg, Level.WARN, thrown);
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
    log(msg, Level.ERROR, (Throwable) null);
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
    log(msg, Level.ERROR, thrown);
  }

  private void doLogExtractingThrowable(Level level, String msg, Object[] argArray) {
    var tuple = MessageFormatter.arrayFormat(msg, argArray);
    log(tuple.getMessage(), level, tuple.getThrowable());
  }

  private void log(@Nullable String formattedMessage, Level level, @Nullable Throwable t) {
    if (currentLevel.isMoreVerboseOrEqual(level) && (formattedMessage != null || t != null)) {
      var stacktrace = t == null ? null : LogOutput.stackTraceToString(t);
      log(formattedMessage, level, stacktrace);
    }
  }

  private void log(@Nullable String formattedMessage, Level level, @Nullable String stackTrace) {
    var output = Optional.ofNullable(target.get()).orElseThrow(() -> {
      var noLogOutputConfigured = new IllegalStateException("No log output configured");
      noLogOutputConfigured.printStackTrace(System.err);
      return noLogOutputConfigured;
    });
    if (output != null) {
      output.log(formattedMessage, level, stackTrace);
      Sentry.logger().log(getSentryLogLevel(level), formattedMessage);
    }
  }

  private static SentryLogLevel getSentryLogLevel(Level level) {
    try {
      return SentryLogLevel.valueOf(level.name());
    } catch (IllegalArgumentException notSupported) {
      // Current log levels map nicely almost 1:1, but this may change later
      return SentryLogLevel.ERROR;
    }
  }

  /**
   * Append an 's' at the end of the word
   */
  public static String singlePlural(int count, String singular) {
    return count == 1 ? singular : (singular + "s");
  }

  public void setLevel(Level newLevel) {
    this.currentLevel = newLevel;
  }
}
