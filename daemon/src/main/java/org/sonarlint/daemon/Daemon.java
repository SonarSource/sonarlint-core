/*
 * SonarLint Daemon
 * Copyright (C) 2009-2017 SonarSource SA
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
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarlint.daemon.interceptors.ExceptionInterceptor;
import org.sonarlint.daemon.services.ConnectedSonarLintImpl;
import org.sonarlint.daemon.services.StandaloneSonarLintImpl;

public class Daemon {
  private static final Logger LOGGER = LoggerFactory.getLogger(Daemon.class);
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

      if (options.getPort() != null) {
        port = Integer.parseInt(options.getPort());
      } else {
        port = DEFAULT_PORT;
      }
    } catch (Exception e) {
      LOGGER.error("Error parsing arguments", e);
      return;
    }

    new Daemon().start(port);
  }

  private static void setUpNettyLogging() {
    InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
  }

  public void stop() {
    server.shutdown();
  }

  public void start(int port) {
    try {
      LOGGER.info("Starting server on port {}", port);
      ServerInterceptor interceptor = new ExceptionInterceptor();

      server = ServerBuilder.forPort(port)
        .addService(ServerInterceptors.intercept(new ConnectedSonarLintImpl(), interceptor))
        .addService(ServerInterceptors.intercept(new StandaloneSonarLintImpl(Utils.getAnalyzers()), interceptor))
        .build()
        .start();
      LOGGER.info("Server started, listening on {}", port);
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          LOGGER.info("Shutting down gRPC server since JVM is shutting down");
          Daemon.this.stop();
          LOGGER.info("Server shut down");
        }
      });
      server.awaitTermination();
    } catch (Exception e) {
      // grpc threads are daemon, so should not hang process
      LOGGER.error("Error running daemon", e);
    }
  }
}
