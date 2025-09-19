/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.embedded.server;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;

import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.EMBEDDED_SERVER;

public class EmbeddedServer {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final int STARTING_PORT = 64120;
  private static final int ENDING_PORT = 64130;

  private static final int INVALID_PORT = -1;

  private HttpServer server;
  private int port;
  private final boolean enabled;
  private final StatusRequestHandler statusRequestHandler;
  private final GeneratedUserTokenHandler generatedUserTokenHandler;
  private final ShowHotspotRequestHandler showHotspotRequestHandler;
  private final ShowIssueRequestHandler showIssueRequestHandler;
  private final ShowFixSuggestionRequestHandler showFixSuggestionRequestHandler;
  private final AutomaticAnalysisEnablementRequestHandler automaticAnalysisEnablementRequestHandler;

  public EmbeddedServer(InitializeParams params, StatusRequestHandler statusRequestHandler, GeneratedUserTokenHandler generatedUserTokenHandler,
    ShowHotspotRequestHandler showHotspotRequestHandler, ShowIssueRequestHandler showIssueRequestHandler, ShowFixSuggestionRequestHandler showFixSuggestionRequestHandler,
    AutomaticAnalysisEnablementRequestHandler automaticAnalysisEnablementRequestHandler) {
    this.enabled = params.getBackendCapabilities().contains(EMBEDDED_SERVER);
    this.statusRequestHandler = statusRequestHandler;
    this.generatedUserTokenHandler = generatedUserTokenHandler;
    this.showHotspotRequestHandler = showHotspotRequestHandler;
    this.showIssueRequestHandler = showIssueRequestHandler;
    this.showFixSuggestionRequestHandler = showFixSuggestionRequestHandler;
    this.automaticAnalysisEnablementRequestHandler = automaticAnalysisEnablementRequestHandler;
  }

  @PostConstruct
  public void start() {
    if (!enabled) {
      return;
    }
    final var socketConfig = SocketConfig.custom()
      .setSoTimeout(15, TimeUnit.SECONDS)
      // let the port be bindable again immediately
      .setSoReuseAddress(true)
      .setTcpNoDelay(true)
      .build();
    port = INVALID_PORT;
    var triedPort = STARTING_PORT;
    HttpServer startedServer = null;
    var loopbackAddress = InetAddress.getLoopbackAddress();
    while (port < 0 && triedPort <= ENDING_PORT) {
      try {
        startedServer = ServerBootstrap.bootstrap()
          .setLocalAddress(loopbackAddress)
          .setCanonicalHostName(loopbackAddress.getHostName())
          // we will never have long connections
          .setConnectionReuseStrategy(new DontKeepAliveReuseStrategy())
          .setListenerPort(triedPort)
          .setSocketConfig(socketConfig)
          .addFilterFirst("RateLimiter", new RateLimitFilter())
          .addFilterAfter("RateLimiter", "CORS", new CorsFilter())
          .register("/sonarlint/api/status", statusRequestHandler)
          .register("/sonarlint/api/token", generatedUserTokenHandler)
          .register("/sonarlint/api/hotspots/show", showHotspotRequestHandler)
          .register("/sonarlint/api/issues/show", showIssueRequestHandler)
          .register("/sonarlint/api/fix/show", showFixSuggestionRequestHandler)
          .register("/sonarlint/api/analysis/automatic/config", automaticAnalysisEnablementRequestHandler)
          .addFilterLast("CSP", new CspFilter())
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

  @PreDestroy
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
