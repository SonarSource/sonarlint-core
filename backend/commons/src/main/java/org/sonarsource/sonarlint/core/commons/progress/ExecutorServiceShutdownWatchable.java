/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2024 SonarSource SA
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

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class ExecutorServiceShutdownWatchable<E extends ExecutorService> implements ExecutorService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final E wrapped;

  private final Deque<WeakReference<SonarLintCancelMonitor>> monitorsToCancelOnShutdown = new ConcurrentLinkedDeque<>();

  public ExecutorServiceShutdownWatchable(E wrapped) {
    this.wrapped = wrapped;
  }

  public E getWrapped() {
    return wrapped;
  }

  public void cancelOnShutdown(SonarLintCancelMonitor monitor) {
    if (wrapped.isShutdown()) {
      monitor.cancel();
    } else {
      monitorsToCancelOnShutdown.add(new WeakReference<>(monitor));
      cleanGoneMonitors();
    }
  }

  private void cleanGoneMonitors() {
    monitorsToCancelOnShutdown.removeIf(ref -> ref.get() == null);
  }

  @Override
  public void shutdown() {
    wrapped.shutdown();
    cancelMonitors();
  }

  @Override
  public List<Runnable> shutdownNow() {
    var result = wrapped.shutdownNow();
    cancelMonitors();
    return result;
  }

  private void cancelMonitors() {
    monitorsToCancelOnShutdown.forEach(w -> {
      var monitor = w.get();
      if (monitor != null) {
        try {
          monitor.cancel();
        } catch (Exception e) {
          LOG.error("Failed to cancel on shutdown", e);
        }
      }
    });
  }

  @Override
  public boolean isShutdown() {
    return wrapped.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return wrapped.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return wrapped.awaitTermination(timeout, unit);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return wrapped.submit(task);
  }


  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return wrapped.submit(task, result);
  }


  @Override
  public Future<?> submit(Runnable task) {
    return wrapped.submit(task);
  }


  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return wrapped.invokeAll(tasks);
  }


  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
    return wrapped.invokeAll(tasks, timeout, unit);
  }


  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    return wrapped.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return wrapped.invokeAny(tasks, timeout, unit);
  }

  @Override
  public void execute(Runnable command) {
    wrapped.execute(command);
  }
}
