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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.sonarsource.sonarlint.core.commons.log.LogOutput.Level;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class LogOutputDelegatorTests {
  private final LogOutputDelegator delegator = new LogOutputDelegator();
  private final LogOutput output = mock(LogOutput.class);

  @Test
  void should_throw_exception_when_not_set() {
    var e = assertThrows(IllegalStateException.class, () -> delegator.log("asd", Level.DEBUG));
    assertThat(e).hasMessage("No log output configured");
  }

  @Test
  void should_delegate() {
    delegator.setTarget(output);
    delegator.log("asd", Level.DEBUG);
    verify(output).log("asd", Level.DEBUG);
  }

  @Test
  void should_remove_delegate() {
    delegator.setTarget(output);
    delegator.setTarget(null);
    var e = assertThrows(IllegalStateException.class, () -> delegator.log("asd", Level.DEBUG));
    assertThat(e).hasMessage("No log output configured");
  }

  @Test
  void should_report_throwables() {
    delegator.setTarget(output);
    delegator.log("msg", Level.ERROR, new NullPointerException("error"));
    verify(output).log("msg", Level.ERROR);
    verify(output).log(ArgumentMatchers.startsWith("java.lang.NullPointerException: error"), ArgumentMatchers.eq(Level.ERROR));
    verifyNoMoreInteractions(output);
  }

  @Test
  void handle_nulls() {
    delegator.setTarget(output);
    delegator.log(null, Level.ERROR, null);
    verifyNoInteractions(output);
  }

  @Test
  void should_not_log_skipped_message() {
    var messageToSkip = "Skipping section 'introduction' for rule 'S123', content is empty";
    delegator.setTarget(output);
    delegator.log(messageToSkip, Level.DEBUG, null);
    verifyNoInteractions(output);
  }
}
