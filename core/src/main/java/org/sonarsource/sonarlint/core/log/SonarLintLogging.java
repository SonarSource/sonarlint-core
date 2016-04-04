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
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;

public class SonarLintLogging {
  private static final String CUSTOM_APPENDER_NAME = "custom_stream";
  private static final String DEFAULT_APPENDER_NAME = "default_appender";

  private static LogCallbackAppender appender;

  static {
    init();
  }

  private SonarLintLogging() {
    // static only
  }

  public static void set(LogOutput output) {
    appender.setTarget(output);
  }

  private static void init() {
    ConsoleAppender<ILoggingEvent> defaultAppender = new ConsoleAppender<>();
    defaultAppender.setContext( (LoggerContext) LoggerFactory.getILoggerFactory());
    defaultAppender.setName(DEFAULT_APPENDER_NAME);
    LevelFilter levelFilter = new LevelFilter();
    levelFilter.setLevel(Level.ERROR);
    defaultAppender.addFilter(levelFilter);

    // if output is not set for thread, keep default behavior (configured to stdout)
    setCustomRootAppender(defaultAppender);
  }

  private static void setCustomRootAppender(Appender<ILoggingEvent> defaultAppender) {
    Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    // by default it has an appender logging to stdout
    logger.detachAndStopAllAppenders();
    appender = new LogCallbackAppender(defaultAppender);
    appender.setName(CUSTOM_APPENDER_NAME);
    appender.start();

    logger.addAppender(appender);
  }
}
