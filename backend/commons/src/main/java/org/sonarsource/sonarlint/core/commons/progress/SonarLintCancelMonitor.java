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

import java.util.Deque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;

public class SonarLintCancelMonitor {

  private boolean canceled;
  private final Deque<Runnable> downstreamCancelAction = new ConcurrentLinkedDeque<>();

  public synchronized void cancel() {
    canceled = true;
    downstreamCancelAction.forEach(Runnable::run);
    downstreamCancelAction.clear();
  }

  public boolean isCanceled() {
    return canceled;
  }

  public void checkCanceled() {
    if (canceled) {
      throw new CancellationException();
    }
  }

  public synchronized void onCancel(Runnable action) {
    if (canceled) {
      action.run();
    } else {
      this.downstreamCancelAction.add(action);
    }
  }

  public void watchForShutdown(ExecutorServiceShutdownWatchable<?> executorService) {
    executorService.addShutdownHook(this::cancel);
  }
}
