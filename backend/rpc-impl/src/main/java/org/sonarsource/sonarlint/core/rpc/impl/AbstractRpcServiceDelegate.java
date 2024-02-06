/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.slf4j.MDC;
import org.sonarsource.sonarlint.core.SonarLintMDC;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ExecutorServiceShutdownWatchable;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.springframework.beans.factory.BeanFactory;

abstract class AbstractRpcServiceDelegate {

  private final Supplier<BeanFactory> beanFactorySupplier;
  private final ExecutorServiceShutdownWatchable<?> requestsExecutor;
  private final ExecutorService requestAndNotificationsSequentialExecutor;
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
    var cancelMonitor = new SonarLintCancelMonitor();
    cancelMonitor.watchForShutdown(requestsExecutor);
    // First we schedule the processing of the request on the sequential executor, to maintain ordering of notifications, requests, responses, and cancellations
    // We can maybe cancel early
    var sequentialFuture = CompletableFuture.runAsync(cancelMonitor::checkCanceled, requestAndNotificationsSequentialExecutor);
    // Then requests are processed asynchronously to not block the processing of notifications, responses and cancellations
    var requestFuture = sequentialFuture.thenApplyAsync(unused -> withLogger(() -> {
      cancelMonitor.checkCanceled();
      return code.apply(cancelMonitor);
    }, configScopeId), requestsExecutor);
    requestFuture.whenComplete((result, error) -> {
      if (error instanceof CancellationException) {
        cancelMonitor.cancel();
      }
    });
    return requestFuture;
  }

  protected CompletableFuture<Void> runAsync(Consumer<SonarLintCancelMonitor> code) {
    return runAsync(code, null);
  }

  protected CompletableFuture<Void> runAsync(Consumer<SonarLintCancelMonitor> code, @Nullable String configScopeId) {
    var cancelMonitor = new SonarLintCancelMonitor();
    cancelMonitor.watchForShutdown(requestsExecutor);
    // First we schedule the processing of the request on the sequential executor, to maintain ordering of notifications, requests, responses, and cancellations
    // We can maybe cancel early
    var sequentialFuture = CompletableFuture.runAsync(cancelMonitor::checkCanceled, requestAndNotificationsSequentialExecutor);
    // Then requests are processed asynchronously to not block the processing of notifications, responses and cancellations
    var requestFuture = sequentialFuture.<Void>thenApplyAsync(unused -> {
      withLogger(() -> {
        cancelMonitor.checkCanceled();
        code.accept(cancelMonitor);
      }, configScopeId);
      return null;
    }, requestsExecutor);
    requestFuture.whenComplete((result, error) -> {
      if (error instanceof CancellationException) {
        cancelMonitor.cancel();
      }
    });
    return requestFuture;
  }

  /**
   * We don't want to risk a long notification to block the message processor thread and to prevent cancellation of requests,
   * so we are also moving notifications to a separate thread pool. Still we want to preserve ordering of requests and notifications.
   */
  protected void notify(Runnable code) {
    notify(code, null);
  }

  protected void notify(Runnable code, String configScopeId) {
    requestAndNotificationsSequentialExecutor.submit(() -> withLogger(code, configScopeId));
  }

  private void withLogger(Runnable code, @Nullable String configScopeId) {
    SonarLintLogger.setTarget(logOutputSupplier.get());
    SonarLintMDC.putConfigScopeId(configScopeId);
    logOutputSupplier.get().setConfigScopeId(configScopeId);
    try {
      code.run();
    } finally {
      MDC.clear();
      SonarLintLogger.setTarget(null);
      logOutputSupplier.get().setConfigScopeId(null);
    }
  }

  private <G> G withLogger(Supplier<G> code, @Nullable String configScopeId) {
    SonarLintLogger.setTarget(logOutputSupplier.get());
    SonarLintMDC.putConfigScopeId(configScopeId);
    logOutputSupplier.get().setConfigScopeId(configScopeId);
    try {
      return code.get();
    } finally {
      MDC.clear();
      SonarLintLogger.setTarget(null);
      logOutputSupplier.get().setConfigScopeId(null);
    }
  }

}
