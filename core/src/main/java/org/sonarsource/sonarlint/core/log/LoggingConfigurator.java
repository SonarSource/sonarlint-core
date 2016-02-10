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
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.google.common.collect.Maps;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.client.api.LogOutput;

public class LoggingConfigurator {
  private static final String PROPERTY_ROOT_LOGGER_LEVEL = "ROOT_LOGGER_LEVEL";
  private static final String LEVEL_ROOT_VERBOSE = "DEBUG";
  private static final String LEVEL_ROOT_DEFAULT = "INFO";
  private static final String CUSTOM_APPENDER_NAME = "custom_stream";

  private LoggingConfigurator() {
  }

  public static void init(boolean verbose, @Nullable LogOutput listener) {
    Map<String, String> substitutionVariables = Maps.newHashMap();
    String level = verbose ? LEVEL_ROOT_VERBOSE : LEVEL_ROOT_DEFAULT;
    substitutionVariables.put(PROPERTY_ROOT_LOGGER_LEVEL, level);
    Logback.configure("/logback/logback.xml", substitutionVariables);

    // if not set, keep default behavior (configured to stdout through the file in classpath)
    if (listener != null) {
      setCustomRootAppender(level, listener);
    }
  }

  public static void setVerbose(boolean verbose) {
    Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    String level = verbose ? LEVEL_ROOT_VERBOSE : LEVEL_ROOT_DEFAULT;
    logger.setLevel(Level.toLevel(level));
  }

  private static void setCustomRootAppender(String level, LogOutput listener) {
    Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    if (logger.getAppender(CUSTOM_APPENDER_NAME) == null) {
      logger.detachAndStopAllAppenders();
      logger.addAppender(createAppender(listener));
    }
    logger.setLevel(Level.toLevel(level));
  }

  private static Appender<ILoggingEvent> createAppender(LogOutput target) {
    LogCallbackAppender appender = new LogCallbackAppender(target);
    appender.setName(CUSTOM_APPENDER_NAME);
    appender.start();

    return appender;
  }
}
