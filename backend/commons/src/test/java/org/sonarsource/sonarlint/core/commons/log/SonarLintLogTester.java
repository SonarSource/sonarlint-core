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

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.commons.log.LogOutput.Level;

/**
 * <b>For tests only</b>
 * <br>
 * This JUnit 5 extension allows to access logs in tests.
 * <br>
 * Warning - not compatible with parallel execution of tests in the same JVM fork.
 * <br>
 * Example:
 * <pre>
 * public class MyClass {
 *   private final SonarLintLogger logger = SonarLintLogger.get();
 *
 *   public void doSomething() {
 *     logger.info("foo");
 *   }
 * }
 *
 * class MyClassTests {
 *   &#064;org.junit.jupiter.api.extension.RegisterExtension
 *   SonarLintLogTester logTester = new SonarLintLogTester();
 *
 *   &#064;org.junit.jupiter.api.Test
 *   public void test_log() {
 *     new MyClass().doSomething();
 *
 *     assertThat(logTester.logs()).containsOnly("foo");
 *   }
 * }
 * </pre>
 */
public class SonarLintLogTester implements BeforeTestExecutionCallback, AfterTestExecutionCallback, BeforeAllCallback, AfterAllCallback {

  private final Queue<String> logs = new ConcurrentLinkedQueue<>();
  private final Map<Level, Queue<String>> logsByLevel = new ConcurrentHashMap<>();
  private final LogOutput logOutput;

  private final ConcurrentListAppender<ILoggingEvent> listAppender = new ConcurrentListAppender<>();

  public SonarLintLogTester(boolean writeToStdOut) {
    logOutput = new LogOutput() {
      @Override
      public void log(@Nullable String formattedMessage, Level level, @Nullable String stacktrace) {
        if (formattedMessage != null) {
          logs.add(formattedMessage);
          logsByLevel.computeIfAbsent(level, l -> new ConcurrentLinkedQueue<>()).add(formattedMessage);
        }
        if (stacktrace != null) {
          logs.add(stacktrace);
          logsByLevel.computeIfAbsent(level, l -> new ConcurrentLinkedQueue<>()).add(stacktrace);
        }
        if (writeToStdOut) {
          System.out.println(level + " " + (formattedMessage != null ? formattedMessage : ""));
          if (stacktrace != null) {
            System.out.println(stacktrace);
          }
        }
      }
    };
  }

  public SonarLintLogTester() {
    this(false);
  }

  @Override
  public void beforeTestExecution(ExtensionContext context) throws Exception {
  }

  @Override
  public void afterTestExecution(ExtensionContext context) {
    clear();
  }

  public void clear() {
    logs.clear();
    logsByLevel.clear();
    listAppender.list.clear();
  }

  public LogOutput getLogOutput() {
    return logOutput;
  }

  /**
   * Logs in chronological order (item at index 0 is the oldest one)
   */
  public List<String> logs() {
    return List.copyOf(logs);
  }

  /**
   * Logs in chronological order (item at index 0 is the oldest one) for
   * a given level
   */
  public List<String> logs(Level level) {
    return Optional.ofNullable(logsByLevel.get(level)).map(List::copyOf).orElse(List.of());
  }

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    SonarLintLogger.setTarget(null);
    listAppender.stop();
    listAppender.list.clear();
    getRootLogger().detachAppender(listAppender);
  }

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    SonarLintLogger.setTarget(logOutput);
    getRootLogger().addAppender(listAppender);
    listAppender.start();
  }

  private static ch.qos.logback.classic.Logger getRootLogger() {
    return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
  }

  /**
   * Logs with arguments in chronological order (item at index 0 is the oldest one)
   */
  public List<ILoggingEvent> getSlf4jLogs() {
    return listAppender.list.stream().map(e -> (LoggingEvent) e)
      .collect(Collectors.toList());
  }

}
