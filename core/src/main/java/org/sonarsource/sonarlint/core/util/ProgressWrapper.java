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

import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.exceptions.CanceledException;

import javax.annotation.Nullable;

public class ProgressWrapper {
  private ProgressMonitor handler;

  public ProgressWrapper(@Nullable ProgressMonitor handler) {
    if (handler == null) {
      this.handler = new NoOpProgressMonitor();
    } else {
      this.handler = handler;
    }
  }

  public void checkCancel() {
    if (handler.isCanceled()) {
      handler.setMessage("Cancelling");
      throw new CanceledException();
    }
  }

  public void setProgress(String msg, float fraction) {
    handler.setMessage(msg);
    handler.setFraction(fraction);
  }

  public void setProgressAndCheckCancel(String msg, float fraction) {
    checkCancel();
    handler.setMessage(msg);
    handler.setFraction(fraction);
  }
  
  public void finishNonCancelableSection() {
    handler.finishNonCancelableSection();
  }
  
  public void startNonCancelableSection() {
    handler.startNonCancelableSection();
  }

  private static class NoOpProgressMonitor extends ProgressMonitor {

  }
}
