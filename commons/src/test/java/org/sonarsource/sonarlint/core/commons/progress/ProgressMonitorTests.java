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
package org.sonarsource.sonarlint.core.commons.progress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalMatchers;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ProgressMonitorTests {
  private ProgressMonitor progress;
  private ClientProgressMonitor monitor;

  @BeforeEach
  void setUp() {
    monitor = mock(ClientProgressMonitor.class);
    progress = new ProgressMonitor(monitor);
  }

  @Test
  void testMsg() {
    progress.setProgressAndCheckCancel("msg", 0.5f);

    verify(monitor).setMessage("msg");
    verify(monitor).setFraction(0.5f);
    verify(monitor).isCanceled();

    verifyNoMoreInteractions(monitor);
  }

  @Test
  void testCancelSection() {
    var r = mock(Runnable.class);
    progress.executeNonCancelableSection(r);

    verify(monitor).executeNonCancelableSection(r);
  }

  @Test
  void testCancel() {
    when(monitor.isCanceled()).thenReturn(true);
    assertThrows(CanceledException.class, () -> progress.checkCancel());
  }

  @Test
  void testProgress() {
    when(monitor.isCanceled()).thenReturn(false);
    progress.setProgress("msg", 0.5f);
    verify(monitor).setMessage("msg");
    verify(monitor).setFraction(0.5f);
  }

  @Test
  void testProgressSubMonitor() {
    when(monitor.isCanceled()).thenReturn(false);
    var subProgress = progress.subProgress(0.2f, 0.4f, "prefix");
    subProgress.setProgress("msg", 0.0f);
    verify(monitor).setMessage("prefix - msg");
    verify(monitor).setFraction(0.2f);
    subProgress.setProgress("msg2", 0.5f);
    verify(monitor).setMessage("prefix - msg2");
    verify(monitor).setFraction(0.3f);
    subProgress.setProgress("msg3", 1.0f);
    verify(monitor).setMessage("prefix - msg3");
    verify(monitor).setFraction(0.4f);
  }

  @Test
  void testProgressSubSubMonitor() {
    when(monitor.isCanceled()).thenReturn(false);
    var subProgress = progress.subProgress(0.2f, 0.4f, "prefix");
    var subSubProgress = subProgress.subProgress(0.5f, 1.0f, "subprefix");
    subSubProgress.setProgress("msg", 0.0f);
    verify(monitor).setMessage("prefix - subprefix - msg");
    verify(monitor).setFraction(0.3f);
    subSubProgress.setProgress("msg2", 0.5f);
    verify(monitor).setMessage("prefix - subprefix - msg2");
    verify(monitor).setFraction(AdditionalMatchers.eq(0.35f, 0.001f));
    subSubProgress.setProgress("msg3", 1.0f);
    verify(monitor).setMessage("prefix - subprefix - msg3");
    verify(monitor).setFraction(0.4f);
  }

  @Test
  void testNoMonitor() {
    progress = new ProgressMonitor(null);
    progress.checkCancel();
    progress.setProgressAndCheckCancel("msg", 0.5f);
  }
}
