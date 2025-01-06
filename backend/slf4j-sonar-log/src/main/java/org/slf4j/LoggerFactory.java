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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level;

/**
 * This class has the same signature has the {@link LoggerFactory} of slf4j.
 * Instead of finding an implementation of slf4j, it creates a bridge between the slf4j API and the sonar logging API.
 * It will always return the same slf4j Logger. This logger forwards all logs to a sonar API logger. 
 */
public class LoggerFactory {
  private static final LoggerAdapter defaultLogger = new LoggerAdapter(SonarLintLogger.get());
  private static final Map<LoggerAdapter, Set<String>> loggerPrefixesMap = new HashMap<>();

  static {
    loggerPrefixesMap.put(new LoggerAdapter(SonarLintLogger.get(), Level.OFF), Set.of("com.networknt.schema"));
  }

  private LoggerFactory() {
    // only static methods
  }

  public static Logger getLogger(String name) {
    return loggerPrefixesMap.entrySet().stream()
      .filter(entry -> entry.getValue().stream().anyMatch(name::startsWith))
      .map(Map.Entry::getKey)
      .findFirst()
      .orElse(defaultLogger);
  }

  public static Logger getLogger(Class clazz) {
    return getLogger(clazz.getName());
  }
}
