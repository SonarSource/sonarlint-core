/*
 * SonarLint Core - Test Utils
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.test.utils.server.sse;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.http.AdminRequestHandler;
import com.github.tomakehurst.wiremock.http.HttpServer;
import com.github.tomakehurst.wiremock.http.HttpServerFactory;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.jetty12.Jetty12HttpServer;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;

public class SseCapableJetty12HttpServerFactory implements HttpServerFactory {

  // Initialized before any constructor runs, so safe to access from inner class
  private final SSEServlet sseServlet = new SSEServlet();

  @Override
  public HttpServer buildHttpServer(Options options, AdminRequestHandler adminRequestHandler,
    StubRequestHandler stubRequestHandler) {
    return new SseCapableJetty12HttpServer(options, adminRequestHandler, stubRequestHandler);
  }

  public SSEServlet getSseServlet() {
    return sseServlet;
  }

  private class SseCapableJetty12HttpServer extends Jetty12HttpServer {

    SseCapableJetty12HttpServer(Options options, AdminRequestHandler adminRequestHandler,
      StubRequestHandler stubRequestHandler) {
      // super() calls decorateMockServiceContextAfterConfig() — sseServlet is already
      // initialized on the outer factory instance, so no NPE
      super(options, adminRequestHandler, stubRequestHandler);
    }

    @Override
    protected void decorateMockServiceContextAfterConfig(ServletContextHandler mockServiceContext) {
      var holder = new ServletHolder(sseServlet);
      holder.setAsyncSupported(true);
      mockServiceContext.addServlet(holder, "/api/push/sonarlint_events");
    }
  }
}
