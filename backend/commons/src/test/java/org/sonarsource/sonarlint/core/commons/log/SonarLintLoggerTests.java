/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.log;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sonarsource.sonarlint.core.commons.log.LogOutput.Level;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

class SonarLintLoggerTests {
  private static final NullPointerException THROWN = new NullPointerException();
  private final LogOutput output = mock(LogOutput.class);
  private final SonarLintLogger logger = new SonarLintLogger();

  @BeforeEach
  void prepare() {
    logger.setTarget(output);
  }

  @Test
  void should_log_error() {
    logger.error("msg1");
    logger.error("msg", (Object) null);
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var emptyArgs = new Object[0];
    logger.error("msg", emptyArgs);
    logger.error("msg {}", "a");
    logger.error("msg {} {}", "a", "a");
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var args = new Object[] {"b"};
    logger.error("msg {}", args);
    logger.error("msg with ex", THROWN);

    var inOrder = Mockito.inOrder(output);
    inOrder.verify(output).log("msg1", Level.ERROR, null);
    inOrder.verify(output, times(2)).log("msg", Level.ERROR, null);
    inOrder.verify(output).log("msg a", Level.ERROR, null);
    inOrder.verify(output).log("msg a a", Level.ERROR, null);
    inOrder.verify(output).log("msg b", Level.ERROR, null);
    inOrder.verify(output).log(eq("msg with ex"), eq(Level.ERROR), argThat(arg -> arg.contains("NullPointerException")));
  }

  @Test
  void should_log_warn() {
    logger.warn("msg1");
    logger.warn("msg", (Object) null);
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var emptyArgs = new Object[0];
    logger.warn("msg", emptyArgs);
    logger.warn("msg {}", "a");
    logger.warn("msg {} {}", "a", "a");
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var args = new Object[] {"b"};
    logger.warn("msg {}", args);
    logger.warn("msg with ex", THROWN);

    var inOrder = Mockito.inOrder(output);
    inOrder.verify(output).log("msg1", Level.WARN, null);
    inOrder.verify(output, times(2)).log("msg", Level.WARN, null);
    inOrder.verify(output).log("msg a", Level.WARN, null);
    inOrder.verify(output).log("msg a a", Level.WARN, null);
    inOrder.verify(output).log("msg b", Level.WARN, null);
    inOrder.verify(output).log(eq("msg with ex"), eq(Level.WARN), argThat(arg -> arg.contains("NullPointerException")));
  }

  @Test
  void should_log_info() {
    logger.info("msg1");
    logger.info("msg", (Object) null);
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var emptyArgs = new Object[0];
    logger.info("msg", emptyArgs);
    logger.info("msg {}", "a");
    logger.info("msg {} {}", "a", "a");
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var args = new Object[] {"b"};
    logger.info("msg {}", args);

    var inOrder = Mockito.inOrder(output);
    inOrder.verify(output).log("msg1", Level.INFO, null);
    inOrder.verify(output, times(2)).log("msg", Level.INFO, null);
    inOrder.verify(output).log("msg a", Level.INFO, null);
    inOrder.verify(output).log("msg a a", Level.INFO, null);
    inOrder.verify(output).log("msg b", Level.INFO, null);
  }

  @Test
  void should_log_debug() {
    logger.debug("msg1");
    logger.debug("msg", (Object) null);
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var emptyArgs = new Object[0];
    logger.debug("msg", emptyArgs);
    logger.debug("msg {}", "a");
    logger.debug("msg {} {}", "a", "a");
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var args = new Object[] {"b"};
    logger.debug("msg {}", args);

    var inOrder = Mockito.inOrder(output);
    inOrder.verify(output).log("msg1", Level.DEBUG, null);
    inOrder.verify(output, times(2)).log("msg", Level.DEBUG, null);
    inOrder.verify(output).log("msg a", Level.DEBUG, null);
    inOrder.verify(output).log("msg a a", Level.DEBUG, null);
    inOrder.verify(output).log("msg b", Level.DEBUG, null);
  }

  @Test
  void should_log_trace() {
    logger.trace("msg1");
    logger.trace("msg", (Object) null);
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var emptyArgs = new Object[0];
    logger.trace("msg", emptyArgs);
    logger.trace("msg {}", "a");
    logger.trace("msg {} {}", "a", "a");
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    var args = new Object[] {"b"};
    logger.trace("msg {}", args);

    var inOrder = Mockito.inOrder(output);
    inOrder.verify(output).log("msg1", Level.TRACE, null);
    inOrder.verify(output, times(2)).log("msg", Level.TRACE, null);
    inOrder.verify(output).log("msg a", Level.TRACE, null);
    inOrder.verify(output).log("msg a a", Level.TRACE, null);
    inOrder.verify(output).log("msg b", Level.TRACE, null);
  }

  // SLCORE-292
  @Test
  void extract_throwable_from_format_params() {
    var throwable = new Throwable("thrown");
    logger.error("msg", (Object) throwable);
    logger.error("msg {}", "a", throwable);
    logger.error("msg {} {}", "a", "a", throwable);

    var inOrder = Mockito.inOrder(output);
    inOrder.verify(output).log(eq("msg"), eq(Level.ERROR), argThat(arg -> arg.contains("thrown")));
    inOrder.verify(output).log(eq("msg a"), eq(Level.ERROR), argThat(arg -> arg.contains("thrown")));
    inOrder.verify(output).log(eq("msg a a"), eq(Level.ERROR), argThat(arg -> arg.contains("thrown")));
  }
}
