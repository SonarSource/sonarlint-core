/*
 * SonarLint Core - RPC Java Client
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.client;

import java.util.concurrent.CancellationException;

/*
 * A class to use in place of {@link org.eclipse.lsp4j.jsonrpc.CancelChecker} to stop depending on lsp4j types in API
 * and services.
 * See SLCORE-663 for details.
 */
public class SonarLintCancelChecker {

  private final org.eclipse.lsp4j.jsonrpc.CancelChecker lsp4JCancelChecker;

  public SonarLintCancelChecker(org.eclipse.lsp4j.jsonrpc.CancelChecker cancelChecker) {
    this.lsp4JCancelChecker = cancelChecker;
  }

  /**
   * Throw a {@link CancellationException} if the currently processed request
   * has been canceled.
   */
  public void checkCanceled() {
    lsp4JCancelChecker.checkCanceled();
  }

  /**
   * Check for cancellation without throwing an exception.
   */
  public boolean isCanceled() {
    return lsp4JCancelChecker.isCanceled();
  }
}
