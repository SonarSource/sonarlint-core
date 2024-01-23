/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.progress;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

public class SonarLintCancelChecker {

  private final CompletableFuture<?> future;

  public SonarLintCancelChecker(CompletableFuture<?> future) {
    this.future = future;
  }

  public void checkCanceled() {
    if (future.isCancelled()) {
      throw new CancellationException();
    }
  }

  public void propagateCancelTo(CompletableFuture<?> downstreamFuture, boolean mayInterruptIfRunning) {
    future.whenComplete((value, error) -> {
      if (error instanceof CancellationException || future.isCancelled()) {
        downstreamFuture.cancel(mayInterruptIfRunning);
      }
    });
  }

  public boolean isCanceled() {
    return future.isCancelled();
  }

}
