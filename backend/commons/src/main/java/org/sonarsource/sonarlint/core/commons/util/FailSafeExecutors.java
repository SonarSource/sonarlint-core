/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.commons.util;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * This class should always be preferred to {@link java.util.concurrent.Executors}, except for a few cases regarding RPC read/write threads.
 */
public class FailSafeExecutors {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private FailSafeExecutors() {
    // utility class
  }

  public static ExecutorService newSingleThreadExecutor(String threadName) {
    return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), r -> new Thread(r, threadName)) {
      @Override
      protected void afterExecute(Runnable task, @Nullable Throwable throwable) {
        var extractedThrowable = extractThrowable(task, throwable);
        if (extractedThrowable != null) {
          LOG.error("An error occurred while executing a task in " + threadName, extractedThrowable);
        }
        super.afterExecute(task, throwable);
      }
    };
  }

  public static ScheduledExecutorService newSingleThreadScheduledExecutor(String threadName) {
    return new ScheduledThreadPoolExecutor(1, r -> new Thread(r, threadName)) {
      @Override
      protected void afterExecute(Runnable task, @Nullable Throwable throwable) {
        var extractedThrowable = extractThrowable(task, throwable);
        if (extractedThrowable != null) {
          LOG.error("An error occurred while executing a scheduled task in " + threadName, extractedThrowable);
        }
        super.afterExecute(task, throwable);
      }
    };
  }

  public static ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
    return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), threadFactory) {
      @Override
      protected void afterExecute(Runnable task, @Nullable Throwable throwable) {
        var extractedThrowable = extractThrowable(task, throwable);
        if (extractedThrowable != null) {
          LOG.error("An error occurred while executing a task in " + Thread.currentThread(), extractedThrowable);
        }
        super.afterExecute(task, throwable);
      }
    };
  }

  @CheckForNull
  private static Throwable extractThrowable(Runnable task, @Nullable Throwable throwable) {
    if (throwable != null) {
      return throwable;
    }
    if (task instanceof FutureTask<?> futureTask) {
      try {
        if (futureTask.isDone() && !futureTask.isCancelled()) {
          futureTask.get();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (ExecutionException e) {
        return e.getCause();
      } catch (CancellationException e) {
        // nothing to do
      }
    }
    return null;
  }
}
