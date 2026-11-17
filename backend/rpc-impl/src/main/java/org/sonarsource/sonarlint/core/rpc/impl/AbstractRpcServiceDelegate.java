/*
 * SonarLint Core - RPC Implementation
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.sonarsource.sonarlint.core.SonarLintMDC;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ExecutorServiceShutdownWatchable;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.springframework.beans.factory.BeanFactory;

abstract class AbstractRpcServiceDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractRpcServiceDelegate.class);
  private static final String SHUTTING_DOWN_MESSAGE = "SonarLint backend is shutting down";

  private final Supplier<BeanFactory> beanFactorySupplier;
  private final ExecutorServiceShutdownWatchable<?> requestsExecutor;
  private final Executor requestAndNotificationsSequentialExecutor;
  private final Supplier<RpcClientLogOutput> logOutputSupplier;

  protected AbstractRpcServiceDelegate(SonarLintRpcServerImpl server) {
    this.beanFactorySupplier = server::getInitializedApplicationContext;
    this.requestsExecutor = server.getRequestsExecutor();
    this.requestAndNotificationsSequentialExecutor = server.getRequestAndNotificationsSequentialExecutor();
    this.logOutputSupplier = server::getLogOutput;
  }

  protected <T> T getBean(Class<T> clazz) {
    return beanFactorySupplier.get().getBean(clazz);
  }

  protected <R> CompletableFuture<R> requestAsync(Function<SonarLintCancelMonitor, R> code) {
    return requestAsync(code, null);
  }

  protected <R> CompletableFuture<R> requestAsync(Function<SonarLintCancelMonitor, R> code, @Nullable String configScopeId) {
    if (executorsAreShuttingDown()) {
      return shuttingDown();
    }
    var cancelMonitor = new SonarLintCancelMonitor();
    cancelMonitor.watchForShutdown(requestsExecutor);
    try {
      // First we schedule the processing of the request on the sequential executor, to maintain ordering of notifications, requests, responses, and cancellations
      // We can maybe cancel early
      var sequentialFuture = CompletableFuture.runAsync(cancelMonitor::checkCanceled, requestAndNotificationsSequentialExecutor);
      // Then requests are processed asynchronously to not block the processing of notifications, responses and cancellations
      var requestFuture = cancellationInsteadOfRejection(sequentialFuture.thenApplyAsync(unused -> computeWithLogger(() -> {
        cancelMonitor.checkCanceled();
        return code.apply(cancelMonitor);
      }, configScopeId), requestsExecutor));
      cancelMonitorWhenCancelled(requestFuture, cancelMonitor);
      return requestFuture;
    } catch (RejectedExecutionException e) {
      LOG.debug(SHUTTING_DOWN_MESSAGE, e);
      return shuttingDown();
    }
  }

  protected <R> CompletableFuture<R> requestFutureAsync(Function<SonarLintCancelMonitor, CompletableFuture<R>> code, @Nullable String configScopeId) {
    if (executorsAreShuttingDown()) {
      return shuttingDown();
    }
    var cancelMonitor = new SonarLintCancelMonitor();
    cancelMonitor.watchForShutdown(requestsExecutor);
    try {
      // First we schedule the processing of the request on the sequential executor, to maintain ordering of notifications, requests, responses, and cancellations
      // We can maybe cancel early
      var sequentialFuture = CompletableFuture.runAsync(cancelMonitor::checkCanceled, requestAndNotificationsSequentialExecutor);
      // Then requests are processed asynchronously to not block the processing of notifications, responses and cancellations
      var requestFuture = cancellationInsteadOfRejection(sequentialFuture.thenComposeAsync(unused -> computeWithLogger(() -> {
        cancelMonitor.checkCanceled();
        return code.apply(cancelMonitor);
      }, configScopeId), requestsExecutor));
      cancelMonitorWhenCancelled(requestFuture, cancelMonitor);
      return requestFuture;
    } catch (RejectedExecutionException e) {
      LOG.debug(SHUTTING_DOWN_MESSAGE, e);
      return shuttingDown();
    }
  }

  protected CompletableFuture<Void> runAsync(Consumer<SonarLintCancelMonitor> code, @Nullable String configScopeId) {
    if (executorsAreShuttingDown()) {
      return shuttingDown();
    }
    var cancelMonitor = new SonarLintCancelMonitor();
    cancelMonitor.watchForShutdown(requestsExecutor);
    try {
      // First we schedule the processing of the request on the sequential executor, to maintain ordering of notifications, requests, responses, and cancellations
      // We can maybe cancel early
      var sequentialFuture = CompletableFuture.runAsync(cancelMonitor::checkCanceled, requestAndNotificationsSequentialExecutor);
      // Then requests are processed asynchronously to not block the processing of notifications, responses and cancellations
      var requestFuture = cancellationInsteadOfRejection(sequentialFuture.<Void>thenApplyAsync(unused -> {
        doWithLogger(() -> {
          cancelMonitor.checkCanceled();
          code.accept(cancelMonitor);
        }, configScopeId);
        return null;
      }, requestsExecutor));
      cancelMonitorWhenCancelled(requestFuture, cancelMonitor);
      return requestFuture;
    } catch (RejectedExecutionException e) {
      LOG.debug(SHUTTING_DOWN_MESSAGE, e);
      return shuttingDown();
    }
  }

  /**
   * We don't want to risk a long notification to block the message processor thread and to prevent cancellation of requests,
   * so we are also moving notifications to a separate thread pool. Still we want to preserve ordering of requests and notifications.
   */
  protected void notify(Runnable code) {
    notify(code, null);
  }

  protected void notify(Runnable code, @Nullable String configScopeId) {
    if (isShutdown(requestAndNotificationsSequentialExecutor)) {
      return;
    }
    try {
      requestAndNotificationsSequentialExecutor.execute(() -> doWithLogger(() -> {
        try {
          code.run();
        } catch (Throwable throwable) {
          SonarLintLogger.get().error("Error when handling notification", throwable);
        }
      }, configScopeId));
    } catch (RejectedExecutionException e) {
      LOG.debug("Dropping a notification during backend shutdown", e);
    }
  }

  private boolean executorsAreShuttingDown() {
    return isShutdown(requestAndNotificationsSequentialExecutor) || isShutdown(requestsExecutor);
  }

  private static boolean isShutdown(Executor executor) {
    return executor instanceof ExecutorService executorService && executorService.isShutdown();
  }

  private static <R> CompletableFuture<R> shuttingDown() {
    return CompletableFuture.failedFuture(new CancellationException(SHUTTING_DOWN_MESSAGE));
  }

  /**
   * A pool that is already shutting down rejects the task and lsp4j reports that as an internal error.
   * Translate it to a cancellation so the client is told the request was cancelled.
   */
  private static <R> CompletableFuture<R> cancellationInsteadOfRejection(CompletableFuture<R> future) {
    return future.exceptionally(error -> {
      if (isRejectedExecution(error)) {
        throw new CancellationException(SHUTTING_DOWN_MESSAGE);
      }
      if (error instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new CompletionException(error);
    });
  }

  private static boolean isRejectedExecution(Throwable error) {
    var current = error;
    while (current != null) {
      if (current instanceof RejectedExecutionException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static void cancelMonitorWhenCancelled(CompletableFuture<?> future, SonarLintCancelMonitor cancelMonitor) {
    future.whenComplete((result, error) -> {
      if (error instanceof CancellationException || (error != null && error.getCause() instanceof CancellationException)) {
        cancelMonitor.cancel();
      }
    });
  }

  private void doWithLogger(Runnable code, @Nullable String configScopeId) {
    SonarLintLogger.get().setTarget(logOutputSupplier.get());
    SonarLintMDC.putConfigScopeId(configScopeId);
    logOutputSupplier.get().setConfigScopeId(configScopeId);
    try {
      code.run();
    } finally {
      MDC.clear();
      SonarLintLogger.get().setTarget(null);
      logOutputSupplier.get().setConfigScopeId(null);
    }
  }

  private <G> G computeWithLogger(Supplier<G> code, @Nullable String configScopeId) {
    SonarLintLogger.get().setTarget(logOutputSupplier.get());
    SonarLintMDC.putConfigScopeId(configScopeId);
    logOutputSupplier.get().setConfigScopeId(configScopeId);
    try {
      return code.get();
    } finally {
      MDC.clear();
      SonarLintLogger.get().setTarget(null);
      logOutputSupplier.get().setConfigScopeId(null);
    }
  }

}
