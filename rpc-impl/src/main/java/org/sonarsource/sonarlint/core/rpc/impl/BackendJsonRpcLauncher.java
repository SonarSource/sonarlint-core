/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BackendJsonRpcLauncher implements Closeable {

  private final SonarLintBackendImpl server;
  private final ExecutorService messageReaderExecutor;
  private final ExecutorService messageWriterExecutor;

  public BackendJsonRpcLauncher(InputStream in, OutputStream out) {
    messageReaderExecutor = Executors.newCachedThreadPool(r -> {
      var t = new Thread(r);
      t.setName("Server message reader");
      return t;
    });
    messageWriterExecutor = Executors.newCachedThreadPool(r -> {
      var t = new Thread(r);
      t.setName("Server message writer");
      return t;
    });

    server = new SonarLintBackendImpl(in, out, messageReaderExecutor, messageWriterExecutor);
  }

  public SonarLintBackendImpl getJavaImpl() {
    return server;
  }

  @Override
  public void close() throws IOException {
    // Stop the MessageProducer thread
    server.getLauncherFuture().cancel(true);
    messageReaderExecutor.shutdownNow();
    messageWriterExecutor.shutdownNow();
    try {
      if (!messageReaderExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Unable to terminate the server message reader in a timely manner");
      }
      if (!messageWriterExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Unable to terminate the server message writer in a timely manner");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted!", e);
    }
  }
}
