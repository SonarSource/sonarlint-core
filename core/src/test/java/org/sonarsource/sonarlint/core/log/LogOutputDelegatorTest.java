/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.log;

import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput.Level;
import org.sonarsource.sonarlint.core.util.LoggedErrorHandler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

public class LogOutputDelegatorTest {
  private LogOutputDelegator delegator = new LogOutputDelegator();

  @Test
  public void should_not_throw_exception_when_not_set() {
    delegator.log("asd", Level.DEBUG);
  }

  @Test
  public void should_delegate() {
    LogOutput output = mock(LogOutput.class);
    delegator.setTarget(output);
    delegator.log("asd", Level.DEBUG);
    verify(output).log("asd", Level.DEBUG);
  }

  @Test
  public void should_remove_delegate() {
    LogOutput output = mock(LogOutput.class);
    delegator.setTarget(output);
    delegator.setTarget(null);
    delegator.log("asd", Level.DEBUG);
    verifyZeroInteractions(output);
  }

  @Test
  public void should_report_error_message() {
    LoggedErrorHandler handler = mock(LoggedErrorHandler.class);
    delegator.setErrorHandler(handler);

    delegator.log("msg1", Level.INFO);
    delegator.log("msg2", Level.ERROR);
    verify(handler).handleError("msg2");
    verifyNoMoreInteractions(handler);
  }

  @Test
  public void should_report_throwables() {
    LoggedErrorHandler handler = mock(LoggedErrorHandler.class);
    delegator.setErrorHandler(handler);

    delegator.logError("msg", new NullPointerException());
    verify(handler).handleError("msg");
    verify(handler).handleException("java.lang.NullPointerException");
    verifyNoMoreInteractions(handler);
  }
}
