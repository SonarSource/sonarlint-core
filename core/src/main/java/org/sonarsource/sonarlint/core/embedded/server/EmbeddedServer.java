/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.embedded.server;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.sonarsource.sonarlint.core.BindingSuggestionProvider;
import org.sonarsource.sonarlint.core.ConfigurationServiceImpl;
import org.sonarsource.sonarlint.core.ConnectionServiceImpl;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.HostInfoDto;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;

public class EmbeddedServer {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final int STARTING_PORT = 64120;
  private static final int ENDING_PORT = 64130;

  private static final int INVALID_PORT = -1;

  private HttpServer server;
  private int port;

  private final SonarLintClient client;
  private final ConnectionServiceImpl connectionService;
  private final AwaitingUserTokenFutureRepository awaitingUserTokenFutureRepository;
  private final ConfigurationServiceImpl configurationService;
  private final BindingSuggestionProvider bindingSuggestionProvider;
  private final ServerApiProvider serverApiProvider;
  private final TelemetryServiceImpl telemetryService;

  public EmbeddedServer(SonarLintClient client, ConnectionServiceImpl connectionService, AwaitingUserTokenFutureRepository awaitingUserTokenFutureRepository,
    ConfigurationServiceImpl configurationService, BindingSuggestionProvider bindingSuggestionProvider, ServerApiProvider serverApiProvider,
    TelemetryServiceImpl telemetryService) {
    this.client = client;
    this.connectionService = connectionService;
    this.awaitingUserTokenFutureRepository = awaitingUserTokenFutureRepository;
    this.configurationService = configurationService;
    this.bindingSuggestionProvider = bindingSuggestionProvider;
    this.serverApiProvider = serverApiProvider;
    this.telemetryService = telemetryService;
  }

  public void initialize(HostInfoDto clientInfo) {
    final var socketConfig = SocketConfig.custom()
      .setSoTimeout(15, TimeUnit.SECONDS)
      // let the port be bindable again immediately
      .setSoReuseAddress(true)
      .setTcpNoDelay(true)
      .build();
    port = INVALID_PORT;
    var triedPort = STARTING_PORT;
    HttpServer startedServer = null;
    while (port < 0 && triedPort <= ENDING_PORT) {
      try {
        startedServer = ServerBootstrap.bootstrap()
          .setLocalAddress(InetAddress.getLoopbackAddress())
          // we will never have long connections
          .setConnectionReuseStrategy(new DontKeepAliveReuseStrategy())
          .setListenerPort(triedPort)
          .setSocketConfig(socketConfig)
          .addFilterFirst("CORS", new CorsFilter())
          .register("/sonarlint/api/status", new StatusRequestHandler(client, connectionService, clientInfo))
          .register("/sonarlint/api/token", new GeneratedUserTokenHandler(awaitingUserTokenFutureRepository))
          .register("/sonarlint/api/hotspots/show",
            new ShowHotspotRequestHandler(client, connectionService, configurationService, bindingSuggestionProvider, serverApiProvider, telemetryService))
          .create();
        startedServer.start();
        port = triedPort;
      } catch (Exception t) {
        LOG.debug("Error while starting port: " + triedPort + ", " + t.getMessage());
        triedPort++;
        if (startedServer != null) {
          startedServer.close();
        }
      }
    }
    if (port > 0) {
      LOG.info("Started embedded server on port " + port);
      server = startedServer;
    } else {
      LOG.error("Unable to start request handler");
      server = null;
    }
  }

  public int getPort() {
    return port;
  }

  public boolean isStarted() {
    return server != null;
  }

  public void shutdown() {
    if (isStarted()) {
      server.close(CloseMode.GRACEFUL);
      server = null;
      port = INVALID_PORT;
    }
  }

  private static class DontKeepAliveReuseStrategy implements ConnectionReuseStrategy {
    @Override
    public boolean keepAlive(HttpRequest request, HttpResponse response, HttpContext context) {
      return false;
    }
  }
}
