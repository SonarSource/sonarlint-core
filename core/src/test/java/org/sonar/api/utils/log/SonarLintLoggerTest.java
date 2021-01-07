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
import org.sonarsource.sonarlint.core.client.api.common.LogOutput.Level;
import org.sonarsource.sonarlint.core.log.LogOutputDelegator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class SonarLintLoggerTest {
  private LogOutputDelegator delegator = mock(LogOutputDelegator.class);
  private SonarLintLogger logger = new SonarLintLogger(delegator);

  @Test
  public void should_not_log_trace() {
    logger.doTrace("msg");
    logger.doTrace("msg", "a");
    logger.doTrace("msg", "a", "a");
    logger.doTrace("msg", new Object[] {"a"});

    verifyZeroInteractions(delegator);
  }

  @Test
  public void should_log_error() {
    logger.doError("msg");
    logger.doError("msg {}", "a");
    logger.doError("msg {} {}", "a", "a");
    logger.doError("msg {}", new Object[] {"b"});

    verify(delegator).log("msg", Level.ERROR);
    verify(delegator).log("msg a", Level.ERROR);
    verify(delegator).log("msg a a", Level.ERROR);
    verify(delegator).log("msg b", Level.ERROR);
  }

  @Test
  public void level_is_always_debug() {
    assertThat(logger.setLevel(LoggerLevel.INFO)).isFalse();
    assertThat(logger.getLevel()).isEqualTo(LoggerLevel.DEBUG);

  }
}
