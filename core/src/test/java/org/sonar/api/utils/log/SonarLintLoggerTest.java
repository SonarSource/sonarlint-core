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

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput.Level;
import org.sonarsource.sonarlint.core.log.LogOutputDelegator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

public class SonarLintLoggerTest {
  private final LogOutputDelegator delegator = mock(LogOutputDelegator.class);
  private final SonarLintLogger logger = new SonarLintLogger(delegator);

  @Test
  public void should_not_log_trace() {
    logger.doTrace("msg");
    logger.doTrace("msg", "a");
    logger.doTrace("msg", "a", "a");
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    Object[] args = new Object[] {"a"};
    logger.doTrace("msg", args);

    verifyNoInteractions(delegator);
  }

  @Test
  public void should_log_error() {
    logger.doError("msg");
    logger.doError("msg", (Object) null);
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    Object[] emptyArgs = new Object[0];
    logger.doError("msg", emptyArgs);
    logger.doError("msg {}", "a");
    logger.doError("msg {} {}", "a", "a");
    // Keep a separate variable to avoid Eclipse refactoring into a non varargs method
    Object[] args = new Object[] {"b"};
    logger.doError("msg {}", args);

    InOrder inOrder = Mockito.inOrder(delegator);
    inOrder.verify(delegator, times(3)).log("msg", Level.ERROR);
    inOrder.verify(delegator).log("msg a", Level.ERROR);
    inOrder.verify(delegator).log("msg a a", Level.ERROR);
    inOrder.verify(delegator).log("msg b", Level.ERROR);
  }

  @Test
  public void level_is_always_debug() {
    assertThat(logger.setLevel(LoggerLevel.INFO)).isFalse();
    assertThat(logger.getLevel()).isEqualTo(LoggerLevel.DEBUG);
  }

  // SLCORE-292
  @Test
  public void extract_throwable_from_format_params() {
    Throwable throwable = new Throwable("thrown");
    logger.doError("msg", (Object) throwable);
    logger.doError("msg {}", "a", throwable);
    logger.doError("msg {} {}", "a", "a", throwable);

    InOrder inOrder = Mockito.inOrder(delegator);
    inOrder.verify(delegator).log("msg", Level.ERROR, throwable);
    inOrder.verify(delegator).log("msg a", Level.ERROR, throwable);
    inOrder.verify(delegator).log("msg a a", Level.ERROR, throwable);
  }
}
