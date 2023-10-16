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
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.springframework.beans.factory.BeanFactory;

abstract class AbstractRpcServiceDelegate {

  private final Supplier<BeanFactory> beanFactorySupplier;
  private final ExecutorService requestsExecutor;
  private final ExecutorService requestAndNotificationsSequentialExecutor;

  protected AbstractRpcServiceDelegate(Supplier<BeanFactory> beanFactorySupplier, ExecutorService requestsExecutor, ExecutorService requestAndNotificationsSequentialExecutor) {
    this.beanFactorySupplier = beanFactorySupplier;
    this.requestsExecutor = requestsExecutor;
    this.requestAndNotificationsSequentialExecutor = requestAndNotificationsSequentialExecutor;
  }

  protected <T> T getBean(Class<T> clazz) {
    return beanFactorySupplier.get().getBean(clazz);
  }

  protected <R> CompletableFuture<R> requestAsync(Function<CancelChecker, R> code) {
    return CompletableFutures.computeAsync(requestAndNotificationsSequentialExecutor, cancelChecker -> {
      var wrapper = new CancelCheckerWrapper(cancelChecker);
      wrapper.checkCanceled();
      return wrapper;
    }).thenApplyAsync(cancelChecker -> {
      cancelChecker.checkCanceled();
      return code.apply(cancelChecker);
    }, requestsExecutor);
  }

  protected CompletableFuture<Void> runAsync(Consumer<CancelChecker> code) {
    return CompletableFutures.computeAsync(requestAndNotificationsSequentialExecutor, cancelChecker -> {
      var wrapper = new CancelCheckerWrapper(cancelChecker);
      wrapper.checkCanceled();
      return wrapper;
    }).thenApplyAsync(cancelChecker -> {
      cancelChecker.checkCanceled();
      code.accept(cancelChecker);
      return null;
    }, requestsExecutor);
  }

  /**
   * We don't want to risk a long notification to block the message processor thread and to prevent cancellation of requests,
   * so we are also moving notifications to a separate thread pool. Still we want to preserve ordering of requests and notifications.
   */
  protected void notify(Runnable code) {
    requestAndNotificationsSequentialExecutor.submit(code);
  }

  private class CancelCheckerWrapper implements CancelChecker {

    private final CancelChecker wrapped;

    private CancelCheckerWrapper(CancelChecker wrapped) {
      this.wrapped = wrapped;
    }

    @Override
    public void checkCanceled() {
      wrapped.checkCanceled();
      if (requestsExecutor.isShutdown()) {
        throw new CancellationException("Server is shutting down");
      }
    }
  }


}
