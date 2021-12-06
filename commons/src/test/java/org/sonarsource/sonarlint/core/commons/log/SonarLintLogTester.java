/*
 * SonarLint Commons
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
package org.sonarsource.sonarlint.core.commons.log;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level;

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
 *   LogTesterJUnit5 logTester = new LogTesterJUnit5();
 *
 *   &#064;org.junit.jupiter.api.Test
 *   public void test_log() {
 *     new MyClass().doSomething();
 *
 *     assertThat(logTester.logs()).containsOnly("foo");
 *   }
 * }
 * </pre>
 *
 */
public class SonarLintLogTester implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

  private final Queue<String> logs = new ConcurrentLinkedQueue<>();
  private final Map<Level, Queue<String>> logsByLevel = new ConcurrentHashMap<>();

  @Override
  public void beforeTestExecution(ExtensionContext context) throws Exception {
    SonarLintLogger.setTarget(new ClientLogOutput() {

      @Override
      public void log(String formattedMessage, Level level) {
        logs.add(formattedMessage);
        logsByLevel.computeIfAbsent(level, l -> new ConcurrentLinkedQueue<>()).add(formattedMessage);
      }
    });
  }

  @Override
  public void afterTestExecution(ExtensionContext context) throws Exception {
    logs.clear();
    logsByLevel.clear();
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
}
