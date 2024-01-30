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
package org.sonarsource.sonarlint.core.commons;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.progress.ExecutorServiceShutdownWatchable;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;

/**
 * The goal of this class is to debounce calls to a function that computes a value. Multiple threads can be blocked on the {@link #get()} method, waiting for the end of the computation.
 * If a {@link #scheduleComputationAsync()} is called while a computation is in progress, an attempt will be made to cancel the current computation, and a new computation will be scheduled.
 * Last feature: it is possible to register a listener that will be notified only after a successful computation (not after a cancellation).
 */
class DebounceComputer<V> {
  private final Function<SonarLintCancelMonitor, V> valueComputer;
  private final ExecutorServiceShutdownWatchable<?> executorService;
  @Nullable
  private final Listener<V> listener;
  private CompletableFuture<V> valueFuture = new CompletableFuture<>();
  @Nullable
  private CompletableFuture<V> computeFuture;
  // The last computed value (a compute task went to completion without cancellation). Can be null if the compute task failed.
  @Nullable
  private V value;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  public interface Listener<V> {

    void afterComputedValueRefreshed(@Nullable V oldValue, @Nullable V newValue);

  }

  public DebounceComputer(Function<SonarLintCancelMonitor, V> valueComputer, ExecutorServiceShutdownWatchable<?> executorService, @Nullable Listener<V> listener) {
    this.valueComputer = valueComputer;
    this.executorService = executorService;
    this.listener = listener;
  }

  public V get() {
    return getValueFuture().join();
  }

  public void scheduleComputationAsync() {
    lock.writeLock().lock();
    try {
      if (computeFuture != null) {
        computeFuture.cancel(false);
        try {
          computeFuture.join();
        } catch (Exception ignore) {
          // expected Cancellation exception, but we can ignore any other error since we are going to compute a new value anyway
        }
        computeFuture = null;
      }
      if (valueFuture.isDone()) {
        valueFuture = new CompletableFuture<>();
      }
      var cancelMonitor = new SonarLintCancelMonitor();
      cancelMonitor.watchForShutdown(executorService);
      CompletableFuture<V> newComputeFuture = CompletableFuture.supplyAsync(() -> {
        cancelMonitor.checkCanceled();
        return valueComputer.apply(cancelMonitor);
      }, executorService);
      newComputeFuture.whenComplete((newValue, error) -> {
        if (error instanceof CancellationException) {
          cancelMonitor.cancel();
        }
      });
      newComputeFuture.whenComplete(this::whenComputeCompleted);
      computeFuture = newComputeFuture;
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void whenComputeCompleted(@Nullable V newValue, @Nullable Throwable error) {
    lock.writeLock().lock();
    try {
      computeFuture = null;
      if (error instanceof CancellationException) {
        return;
      }
      var previousValue = value;
      value = newValue;
      try {
        if (listener != null) {
          listener.afterComputedValueRefreshed(previousValue, newValue);
        }
      } finally {
        if (error != null) {
          valueFuture.completeExceptionally(error);
        } else {
          valueFuture.complete(newValue);
        }
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  private CompletableFuture<V> getValueFuture() {
    lock.readLock().lock();
    try {
      return valueFuture;
    } finally {
      lock.readLock().unlock();
    }
  }

  public void cancel() {
    lock.writeLock().lock();
    try {
      if (computeFuture != null) {
        computeFuture.cancel(false);
        computeFuture = null;
      }
      valueFuture.cancel(false);
    } finally {
      lock.writeLock().unlock();
    }
  }

}
