/*
 * SonarLint Daemon
 * Copyright (C) 2009-2020 SonarSource SA
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
package org.sonarlint.daemon;

import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyServerBuilder;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.JdkLoggerFactory;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import org.sonarlint.daemon.interceptors.ExceptionInterceptor;
import org.sonarlint.daemon.services.ConnectedSonarLintImpl;
import org.sonarlint.daemon.services.StandaloneSonarLintImpl;

public class Daemon {
  private static final int DEFAULT_PORT = 8050;
  private Server server;

  public static void main(String[] args) {
    setUpNettyLogging();

    int port;
    try {
      Options options = Options.parse(args);

      if (options.isHelp()) {
        Options.printUsage();
        return;
      }

      port = options.getPort() != null ? options.getPort() : DEFAULT_PORT;
    } catch (Exception e) {
      System.err.println("Error parsing arguments");
      e.printStackTrace(System.err);
      return;
    }

    Path sonarlintHome = Utils.getSonarLintInstallationHome();
    new Daemon().start(port, sonarlintHome);
  }

  private static void setUpNettyLogging() {
    InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE);
  }

  public void stop() {
    System.out.println("Asking gRPC server to shutdown...");
    server.shutdown();
  }

  public void start(int port, Path sonarlintHome) {
    try {
      System.out.println("Starting server on port " + port);
      ServerInterceptor interceptor = new ExceptionInterceptor();

      server = NettyServerBuilder.forAddress(new InetSocketAddress("localhost", port))
        .addService(ServerInterceptors.intercept(new ConnectedSonarLintImpl(this), interceptor))
        .addService(ServerInterceptors.intercept(new StandaloneSonarLintImpl(this, Utils.getAnalyzers(sonarlintHome)), interceptor))
        .build()
        .start();
      System.out.println("Server started, listening on " + port);
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          System.out.println("JVM is shutting down");
          if (!server.isShutdown()) {
            Daemon.this.stop();
          }
        }
      });
      server.awaitTermination();
    } catch (Exception e) {
      // grpc threads are daemon, so should not hang process
      System.err.println("Error running daemon");
      e.printStackTrace(System.err);
    }
  }
}
