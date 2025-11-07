/*
 * SonarLint Core - RPC Java Client
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;

public class Sloop {
  private final SonarLintRpcServer rpcServer;
  private final Process process;

  public Sloop(SonarLintRpcServer rpcServer, Process process) {
    this.rpcServer = rpcServer;
    this.process = process;
  }

  public CompletableFuture<Void> shutdown() {
    return rpcServer.shutdown();
  }

  public SonarLintRpcServer getRpcServer() {
    return rpcServer;
  }

  /**
   * @return a future that will be completed when the process exits, providing the exit value
   */
  public CompletableFuture<Integer> onExit() {
    return process.onExit().thenApply(Process::exitValue);
  }

  public boolean isAlive() {
    return process.isAlive();
  }
}
