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
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ExecutorServiceShutdownWatchable;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;

/**
 * A cache with async computation of values, and supporting cancellation.
 * "Smart" because when a computation is cancelled, it will return to the previous callers the result of the new computation.
 */
public class SmartCancelableLoadingCache<K, V> implements AutoCloseable {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ExecutorServiceShutdownWatchable<?> executorService;
  private final String threadName;
  private final BiFunction<K, SonarLintCancelMonitor, V> valueComputer;
  private final ConcurrentHashMap<K, DebounceComputer<V>> cache = new ConcurrentHashMap<>();

  @Nullable
  private final Listener<K, V> listener;

  public interface Listener<K, V> {

    void afterCachedValueRefreshed(K key, @Nullable V oldValue, @Nullable V newValue);

  }

  public SmartCancelableLoadingCache(String threadName, BiFunction<K, SonarLintCancelMonitor, V> valueComputer) {
    this(threadName, valueComputer, null);
  }

  public SmartCancelableLoadingCache(String threadName, BiFunction<K, SonarLintCancelMonitor, V> valueComputer, @Nullable Listener<K, V> listener) {
    this.executorService = new ExecutorServiceShutdownWatchable<>(Executors.newSingleThreadExecutor(r -> new Thread(r, threadName)));
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
    cache.compute(key, (k, v) -> {
      if (v == null) {
        return newValueAndScheduleComputation(k);
      } else {
        v.scheduleComputationAsync();
        return v;
      }
    });
  }

  public V get(K key) {
    return cache.computeIfAbsent(key, this::newValueAndScheduleComputation).get();
  }

  private DebounceComputer<V> newValueAndScheduleComputation(K k) {
    var value = new DebounceComputer<>(c -> valueComputer.apply(k, c), executorService, (oldValue, newValue) -> {
      if (listener != null && !Objects.equals(oldValue, newValue)) {
        listener.afterCachedValueRefreshed(k, oldValue, newValue);
      }
    });
    value.scheduleComputationAsync();
    return value;
  }


  @Override
  public void close() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop " + threadName + " executor service in a timely manner");
    }
  }

}
