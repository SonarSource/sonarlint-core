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
package org.sonarsource.sonarlint.core.util;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.exceptions.CanceledException;

public class ProgressWrapper {

  private final ProgressMonitor handler;
  private final float offset;
  private final float factor;
  private final String msgPrefix;

  private ProgressWrapper(float offset, float factor, @Nullable String msgPrefix, @Nullable ProgressMonitor handler) {
    this.offset = offset;
    this.factor = factor;
    this.msgPrefix = msgPrefix;
    if (handler == null) {
      this.handler = new NoOpProgressMonitor();
    } else {
      this.handler = handler;
    }
  }

  public ProgressWrapper(@Nullable ProgressMonitor handler) {
    this(0.0f, 1.0f, null, handler);
  }

  public ProgressWrapper subProgress(float fromFraction, float toFraction, String msgPrefix) {
    return new ProgressWrapper(offset + fromFraction * factor, (toFraction - fromFraction) * factor, prependPrefix(msgPrefix), handler);
  }

  public void checkCancel() {
    if (handler.isCanceled()) {
      handler.setMessage("Cancelling");
      throw new CanceledException();
    }
  }

  public boolean isCanceled() {
    return handler.isCanceled();
  }

  public void setProgress(String msg, float fraction) {
    handler.setMessage(prependPrefix(msg));
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
    handler.setFraction(offset + fraction * factor);
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
