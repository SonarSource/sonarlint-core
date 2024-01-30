/*
 * SonarLint Core - RPC Java Client
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
package org.sonarsource.sonarlint.core.rpc.client;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.core.rpc.protocol.SingleThreadedMessageConsumer;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintLauncherBuilder;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

public class ClientJsonRpcLauncher implements Closeable {

  private final SonarLintRpcServer serverProxy;
  private final Future<Void> future;
  private final ExecutorService messageReaderExecutor;
  private final ExecutorService messageWriterExecutor;
  private final ExecutorService requestAndNotificationsSequentialExecutor;
  private final ExecutorService requestsExecutor;

  public ClientJsonRpcLauncher(InputStream in, OutputStream out, SonarLintRpcClientDelegate clientDelegate) {
    messageReaderExecutor = Executors.newCachedThreadPool(r -> {
      var t = new Thread(r);
      t.setName("Client message reader");
      return t;
    });
    messageWriterExecutor = Executors.newCachedThreadPool(r -> {
      var t = new Thread(r);
      t.setName("Client message writer");
      return t;
    });
    this.requestAndNotificationsSequentialExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "SonarLint Client RPC sequential executor"));
    this.requestsExecutor = Executors.newCachedThreadPool(r -> new Thread(r, "SonarLint Client RPC request executor"));
    var client = new SonarLintRpcClientImpl(clientDelegate, requestsExecutor, requestAndNotificationsSequentialExecutor);
    var clientLauncher = new SonarLintLauncherBuilder<SonarLintRpcServer>()
      .setLocalService(client)
      .setRemoteInterface(SonarLintRpcServer.class)
      .setInput(in)
      .setOutput(out)
      .setExecutorService(messageReaderExecutor)
      .wrapMessages(m -> new SingleThreadedMessageConsumer(m, messageWriterExecutor,
        ex -> clientDelegate.log(new LogParams(LogLevel.ERROR, null, null, stackTraceToString(ex)))))
      .create();

    this.serverProxy = clientLauncher.getRemoteProxy();
    this.future = clientLauncher.startListening();
  }


  private static String stackTraceToString(Throwable t) {
    var stringWriter = new StringWriter();
    var printWriter = new PrintWriter(stringWriter);
    t.printStackTrace(printWriter);
    return stringWriter.toString();
  }

  public SonarLintRpcServer getServerProxy() {
    return serverProxy;
  }

  @Override
  public void close() {
    requestsExecutor.shutdown();
    requestAndNotificationsSequentialExecutor.shutdown();
    // Stop the MessageProducer thread
    future.cancel(true);
    messageReaderExecutor.shutdownNow();
    messageWriterExecutor.shutdownNow();
    try {
      if (!messageReaderExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Unable to terminate the client message reader thread in a timely manner");
      }
      if (!messageWriterExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Unable to terminate the client message writer thread in a timely manner");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted!", e);
    }
  }
}
