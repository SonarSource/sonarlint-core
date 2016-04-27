/*
 * SonarLint Daemon
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarlint.daemon.interceptors.ExceptionInterceptor;
import org.sonarlint.daemon.services.ConnectedSonarLintImpl;
import org.sonarlint.daemon.services.StandaloneSonarLintImpl;
import org.sonarsource.sonarlint.daemon.proto.ConnectedSonarLintGrpc;
import org.sonarsource.sonarlint.daemon.proto.StandaloneSonarLintGrpc;

public class Daemon {
  private static final Logger LOGGER = LoggerFactory.getLogger(Daemon.class);
  private static final int DEFAULT_PORT = 8050;
  private Server server;

  public static void main(String[] args) {
    new Daemon().start();
  }

  public void stop() {
    server.shutdown();
  }

  public void start() {
    try {
      ServerInterceptor interceptor = new ExceptionInterceptor();

      server = ServerBuilder.forPort(DEFAULT_PORT)
        .addService(ServerInterceptors.intercept(ConnectedSonarLintGrpc.bindService(new ConnectedSonarLintImpl()), interceptor))
        .addService(ServerInterceptors.intercept(StandaloneSonarLintGrpc.bindService(new StandaloneSonarLintImpl()), interceptor))
        .build()
        .start();
      LOGGER.info("Server started, listening on " + DEFAULT_PORT);
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          System.err.println("*** shutting down gRPC server since JVM is shutting down");
          Daemon.this.stop();
          System.err.println("*** server shut down");
        }
      });
      server.awaitTermination();
    } catch (Exception e) {
      // grpc threads are daemon, so should not hang process
      LOGGER.error("Error running daemon", e);
    }
  }
}
