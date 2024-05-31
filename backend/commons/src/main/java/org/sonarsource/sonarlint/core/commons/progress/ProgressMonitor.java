/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2024 SonarSource SA
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

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.progress.CanceledException;
import org.sonarsource.sonarlint.core.commons.api.progress.ClientProgressMonitor;

public class ProgressMonitor {
  public static ProgressMonitor wrapping(SonarLintCancelMonitor cancelMonitor) {
    return new ProgressMonitor(new ClientProgressMonitor() {
      @Override
      public boolean isCanceled() {
        return cancelMonitor.isCanceled();
      }
    });
  }

  private final ClientProgressMonitor clientMonitor;
  private volatile boolean canceled;

  public ProgressMonitor(@Nullable ClientProgressMonitor clientMonitor) {
    this.clientMonitor = clientMonitor == null ? new NoOpProgressMonitor() : clientMonitor;
  }

  public void checkCancel() {
    if (isCanceled()) {
      throw new CanceledException();
    }
  }

  public boolean isCanceled() {
    return canceled || clientMonitor.isCanceled();
  }

  public void cancel() {
    canceled = true;
  }

  private static class NoOpProgressMonitor implements ClientProgressMonitor {
  }
}
