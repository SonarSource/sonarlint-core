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

class DebounceComputer<V> {
  private final Function<SonarLintCancelMonitor, V> valueComputer;
  private final ExecutorServiceShutdownWatchable<?> executorService;
  @Nullable
  private final Listener<V> listener;
  private CompletableFuture<V> valueFuture = new CompletableFuture<>();
  private CompletableFuture<V> computeFuture;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  public interface Listener<V> {

    void afterComputedValueRefreshed(@Nullable V oldValue, @Nullable V newValue);

  }

  public DebounceComputer(Function<SonarLintCancelMonitor, V> valueComputer, ExecutorServiceShutdownWatchable<?> executorService, @Nullable Listener<V> listener) {
    this.valueComputer = valueComputer;
    this.executorService = executorService;
    this.listener = listener;
  }

  public void scheduleComputation() {
    lock.writeLock().lock();
    try {
      if (computeFuture != null) {
        computeFuture.cancel(false);
        computeFuture = null;
      }
      // Even if we have canceled the previous computation, the previous valueFuture can still be completed later, so we will create a new one
      // that will be returned to new callers. We will also try to complete the old valueFuture, in case the cancellation was successful.
      var oldValueFuture = valueFuture;
      valueFuture = new CompletableFuture<>();

      var previousValue = oldValueFuture.getNow(null);

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
      newComputeFuture.whenCompleteAsync((newValue, error) -> whenComputeCompleted(newValue, error, previousValue, oldValueFuture), executorService);
      computeFuture = newComputeFuture;
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void whenComputeCompleted(@Nullable V newValue, @Nullable Throwable error, @Nullable V previousValue, CompletableFuture<V> oldValueFuture) {
    lock.writeLock().lock();
    try {
      computeFuture = null;
      if (error instanceof CancellationException) {
        return;
      }
      try {
        if (error == null && listener != null) {
          listener.afterComputedValueRefreshed(previousValue, newValue);
        }
      } finally {
        if (error != null) {
          oldValueFuture.completeExceptionally(error);
          valueFuture.completeExceptionally(error);
        } else {
          oldValueFuture.complete(newValue);
          valueFuture.complete(newValue);
        }
      }
    } finally {
      lock.writeLock().unlock();
    }
  }


  public void refresh() {
    scheduleComputation();
  }

  public CompletableFuture<V> getValueFuture() {
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
