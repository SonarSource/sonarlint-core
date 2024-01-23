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

import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelChecker;

/**
 * A cache with async computation of values, and supporting cancellation.
 * "Smart" because when a computation is cancelled, it will return to the previous callers the result of the new computation.
 */
public class SmartCancelableLoadingCache<K, V> implements AutoCloseable {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ExecutorService executorService;
  private final String threadName;
  private final BiFunction<K, SonarLintCancelChecker, V> valueComputer;
  private final ConcurrentHashMap<K, ValueAndComputeFutures> cache = new ConcurrentHashMap<>();

  private class ValueAndComputeFutures {
    private final K key;
    private volatile CompletableFuture<V> valueFuture = new CompletableFuture<>();
    private volatile CompletableFuture<V> computeFuture;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public ValueAndComputeFutures(K key) {
      this.key = key;
      scheduleComputation();
    }

    private void scheduleComputation() {
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

        CompletableFuture<SonarLintCancelChecker> start = new CompletableFuture<>();
        CompletableFuture<V> result = start.thenApplyAsync(cancelChecker -> valueComputer.apply(key, cancelChecker), executorService);
        start.complete(new SonarLintCancelChecker(result));
        result.whenCompleteAsync((newValue, error) -> whenComputeCompleted(newValue, error, previousValue, oldValueFuture), executorService);
        computeFuture = result;
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
          if (listener != null) {
            listener.afterCachedValueRefreshed(key, previousValue, newValue);
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

  @Nullable
  private final Listener<K, V> listener;

  public interface Listener<K, V> {

    void afterCachedValueRefreshed(K key, @Nullable V oldValue, @Nullable V newValue);

  }

  public SmartCancelableLoadingCache(String threadName, BiFunction<K, SonarLintCancelChecker, V> valueComputer) {
    this(threadName, valueComputer, null);
  }

  public SmartCancelableLoadingCache(String threadName, BiFunction<K, SonarLintCancelChecker, V> valueComputer, @Nullable Listener<K, V> listener) {
    this.executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, threadName));
    this.threadName = threadName;
    this.valueComputer = valueComputer;
    this.listener = listener;
  }


  /**
   * Clear the cached value for this key. Attempt to cancel the computation if it is still running.
   * Awaiting #get() will throw a {@link CancellationException}.
   */
  public void clear(K key) {
    var valueAndComputeFutures = cache.remove(key);
    if (valueAndComputeFutures != null) {
      valueAndComputeFutures.cancel();
    }
  }

  /**
   * Force a new computation for this key. Ensure {@link Listener#afterCachedValueRefreshed(Object, Object, Object)} is called.
   * Awaiting #get() will receive the newly computed value
   */
  public void refreshAsync(K key) {
    var valueAndComputeFutures = cache.get(key);
    if (valueAndComputeFutures == null) {
      cache.computeIfAbsent(key, ValueAndComputeFutures::new);
    } else {
      valueAndComputeFutures.refresh();
    }
  }

  public V get(K key) {
    var resultFuture = cache.computeIfAbsent(key, ValueAndComputeFutures::new);
    return resultFuture.getValueFuture().join();
  }


  @Override
  public void close() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop " + threadName + " executor service in a timely manner");
    }
  }

}
