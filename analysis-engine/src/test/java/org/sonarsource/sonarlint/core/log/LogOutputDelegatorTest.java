/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.log;

import org.junit.Test;
import org.mockito.Mockito;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput.Level;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

public class LogOutputDelegatorTest {
  private LogOutputDelegator delegator = new LogOutputDelegator();
  private LogOutput output = mock(LogOutput.class);

  @Test
  public void should_not_throw_exception_when_not_set() {
    delegator.log("asd", Level.DEBUG);
  }

  @Test
  public void should_delegate() {
    delegator.setTarget(output);
    delegator.log("asd", Level.DEBUG);
    verify(output).log("asd", Level.DEBUG);
  }

  @Test
  public void should_remove_delegate() {
    delegator.setTarget(output);
    delegator.setTarget(null);
    delegator.log("asd", Level.DEBUG);
    verifyZeroInteractions(output);
  }

  @Test
  public void should_report_throwables() {
    delegator.setTarget(output);
    delegator.log("msg", Level.ERROR, new NullPointerException("error"));
    verify(output).log("msg", Level.ERROR);
    verify(output).log(Mockito.startsWith("java.lang.NullPointerException: error"), Mockito.eq(Level.ERROR));
    verifyNoMoreInteractions(output);
  }
}
