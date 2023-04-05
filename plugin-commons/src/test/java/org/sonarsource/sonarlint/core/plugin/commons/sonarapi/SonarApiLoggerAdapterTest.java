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

import java.io.PrintWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;

class SonarApiLoggerAdapterTest {

  private static final NullPointerException THROWN = new NullPointerException() {
    @Override
    public void printStackTrace(PrintWriter s) {
      // dummy stacktrace to simplify assertions
      s.print("stacktrace");
    }
  };

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private final SonarApiLoggerAdapter underTest = new SonarApiLoggerAdapter();

  @Test
  void shouldHaveDebugAndTraceAlwaysEnabled() {
    assertThat(underTest.setLevel(LoggerLevel.ERROR)).isFalse();
    assertThat(underTest.isDebugEnabled()).isTrue();
    assertThat(underTest.isTraceEnabled()).isTrue();
    assertThat(underTest.getLevel()).isEqualTo(LoggerLevel.TRACE);
  }

  @Test
  void shouldRedirectTraceToSonarLintLogger() {
    underTest.trace("msg1");
    underTest.trace("msg2", (Object) null);
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var emptyArgs = new Object[0];
    underTest.trace("msg3", emptyArgs);
    underTest.trace("msg4 {}", "a");
    underTest.trace("msg5 {} {}", "a", "a");
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var args = new Object[] {"b"};
    underTest.trace("msg6 {}", args);

    assertThat(logTester.logs(ClientLogOutput.Level.TRACE)).containsExactly("msg1", "msg2", "msg3", "msg4 a", "msg5 a a", "msg6 b");
  }

  @Test
  void shouldRedirectDebugToSonarLintLogger() {
    underTest.debug("msg1");
    underTest.debug("msg2", (Object) null);
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var emptyArgs = new Object[0];
    underTest.debug("msg3", emptyArgs);
    underTest.debug("msg4 {}", "a");
    underTest.debug("msg5 {} {}", "a", "a");
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var args = new Object[] {"b"};
    underTest.debug("msg6 {}", args);

    assertThat(logTester.logs(ClientLogOutput.Level.DEBUG)).containsExactly("msg1", "msg2", "msg3", "msg4 a", "msg5 a a", "msg6 b");
  }

  @Test
  void shouldRedirectInfoToSonarLintLogger() {
    underTest.info("msg1");
    underTest.info("msg2", (Object) null);
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var emptyArgs = new Object[0];
    underTest.info("msg3", emptyArgs);
    underTest.info("msg4 {}", "a");
    underTest.info("msg5 {} {}", "a", "a");
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var args = new Object[] {"b"};
    underTest.info("msg6 {}", args);

    assertThat(logTester.logs(ClientLogOutput.Level.INFO)).containsExactly("msg1", "msg2", "msg3", "msg4 a", "msg5 a a", "msg6 b");
  }

  @Test
  void shouldRedirectWarnToSonarLintLogger() {
    underTest.warn("msg1");
    underTest.warn("msg2", (Object) null);
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var emptyArgs = new Object[0];
    underTest.warn("msg3", emptyArgs);
    underTest.warn("msg4 {}", "a");
    underTest.warn("msg5 {} {}", "a", "a");
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var args = new Object[] {"b"};
    underTest.warn("msg6 {}", args);
    underTest.warn("msg with ex", THROWN);

    assertThat(logTester.logs(ClientLogOutput.Level.WARN)).containsExactly("msg1", "msg2", "msg3", "msg4 a", "msg5 a a", "msg6 b", "msg with ex",
      "stacktrace");
  }

  @Test
  void shouldRedirectErrorToSonarLintLogger() {
    underTest.error("msg1");
    underTest.error("msg2", (Object) null);
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var emptyArgs = new Object[0];
    underTest.error("msg3", emptyArgs);
    underTest.error("msg4 {}", "a");
    underTest.error("msg5 {} {}", "a", "a");
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var args = new Object[] {"b"};
    underTest.error("msg6 {}", args);
    underTest.error("msg with ex", THROWN);

    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).containsExactly("msg1", "msg2", "msg3", "msg4 a", "msg5 a a", "msg6 b", "msg with ex",
      "stacktrace");
  }

}
