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

import javax.annotation.Nullable;

public class ProgressMonitor {

  private final ClientProgressMonitor clientMonitor;
  private final float offset;
  private final float factor;
  private final String msgPrefix;
  private volatile boolean canceled;

  private ProgressMonitor(float offset, float factor, @Nullable String msgPrefix, @Nullable ClientProgressMonitor clientMonitor) {
    this.offset = offset;
    this.factor = factor;
    this.msgPrefix = msgPrefix;
    this.clientMonitor = clientMonitor == null ? new NoOpProgressMonitor() : clientMonitor;
  }

  public ProgressMonitor(@Nullable ClientProgressMonitor clientMonitor) {
    this(0.0f, 1.0f, null, clientMonitor);
  }

  public ProgressMonitor subProgress(float fromFraction, float toFraction, String msgPrefix) {
    return new ProgressMonitor(offset + fromFraction * factor, (toFraction - fromFraction) * factor, prependPrefix(msgPrefix), clientMonitor);
  }

  public void checkCancel() {
    if (isCanceled()) {
      clientMonitor.setMessage("Cancelling");
      throw new CanceledException();
    }
  }

  public boolean isCanceled() {
    return canceled || clientMonitor.isCanceled();
  }

  public void cancel() {
    canceled = true;
  }

  public void setProgress(String msg, float fraction) {
    clientMonitor.setMessage(prependPrefix(msg));
    setFraction(fraction);
  }

  private String prependPrefix(String suffix) {
    return this.msgPrefix != null ? (this.msgPrefix + " - " + suffix) : suffix;
  }

  public void setProgressAndCheckCancel(String msg, float fraction) {
    checkCancel();
    setProgress(msg, fraction);
  }

  private void setFraction(float fraction) {
    clientMonitor.setFraction(offset + fraction * factor);
  }

  public void executeNonCancelableSection(Runnable r) {
    clientMonitor.executeNonCancelableSection(r);
  }

  private static class NoOpProgressMonitor implements ClientProgressMonitor {

    @Override
    public void setMessage(String msg) {
      // no-op
    }

    @Override
    public void setFraction(float fraction) {
      // no-op
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {
      // no-op
    }

  }
}
