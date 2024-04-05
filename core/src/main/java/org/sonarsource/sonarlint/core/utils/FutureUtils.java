/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.utils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class FutureUtils {

  private static final long WAITING_FREQUENCY = 100;
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public static void waitForTask(CancelChecker cancelChecker, Future<?> task, String taskName, Duration timeoutDuration) {
    try {
      waitForFutureWithTimeout(cancelChecker, task, timeoutDuration);
    } catch (TimeoutException ex) {
      task.cancel(true);
      LOG.error(taskName + " task expired", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      LOG.error(taskName + " task failed", ex);
    }
  }

  public static void waitForTasks(CancelChecker indicator, List<Future<?>> tasks, String taskName, Duration timeoutDuration) {
    for (var f : tasks) {
      waitForTask(indicator, f, taskName, timeoutDuration);
    }
  }

  private static void waitForFutureWithTimeout(CancelChecker cancelChecker, Future<?> future, Duration durationTimeout)
    throws InterruptedException, ExecutionException, TimeoutException {
    long counter = 0;
    while (counter < durationTimeout.toMillis()) {
      counter += WAITING_FREQUENCY;
      if (cancelChecker.isCanceled()) {
        future.cancel(true);
        return;
      }
      try {
        future.get(WAITING_FREQUENCY, TimeUnit.MILLISECONDS);
        return;
      } catch (TimeoutException ignored) {
        continue;
      } catch (InterruptedException | CancellationException e) {
        throw new InterruptedException("Interrupted");
      }
    }
    throw new TimeoutException();
  }

  private FutureUtils() {
    // utility class
  }
}
