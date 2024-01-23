/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.utils;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;

/**
 * A {@link CompletableFutures.FutureCancelChecker} that also checks if the executor is shutdown.
 */
public class FutureAndShutdownCancelChecker extends CompletableFutures.FutureCancelChecker {

  private final ExecutorService executor;

  public FutureAndShutdownCancelChecker(ExecutorService executor, CompletableFuture<?> future) {
    super(future);
    this.executor = executor;
  }

  @Override
  public void checkCanceled() {
    super.checkCanceled();
    if (executor.isShutdown()) {
      throw new CancellationException("Server is shutting down");
    }
  }
}
