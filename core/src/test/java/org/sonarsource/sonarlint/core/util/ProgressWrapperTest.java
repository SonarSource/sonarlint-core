/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.util;

import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.exceptions.CanceledException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ProgressWrapperTest {
  private ProgressWrapper progress;
  private ProgressMonitor monitor;

  @Before
  public void setUp() {
    monitor = mock(ProgressMonitor.class);
    progress = new ProgressWrapper(monitor);
  }

  @Test
  public void testMsg() {
    progress.setProgressAndCheckCancel("msg", 0.5f);

    verify(monitor).setMessage("msg");
    verify(monitor).setFraction(0.5f);
    verify(monitor).isCanceled();

    verifyNoMoreInteractions(monitor);
  }

  @Test
  public void testCancelSection() {
    progress.finishNonCancelableSection();
    progress.startNonCancelableSection();

    verify(monitor).finishNonCancelableSection();
    verify(monitor).startNonCancelableSection();
  }

  @Test(expected = CanceledException.class)
  public void testCancel() {
    when(monitor.isCanceled()).thenReturn(true);
    progress.checkCancel();
  }
  
  @Test
  public void testProgress() {
    when(monitor.isCanceled()).thenReturn(true);
    progress.setProgress("msg", 0.5f);
    verify(monitor).setMessage("msg");
    verify(monitor).setFraction(0.5f);
  }

  @Test
  public void testNoMonitor() {
    progress = new ProgressWrapper(null);
    progress.checkCancel();
    progress.setProgressAndCheckCancel("msg", 0.5f);
  }
}
