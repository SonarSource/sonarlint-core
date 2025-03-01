/*
 * SonarLint Core - Test Utils
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
package org.sonarsource.sonarlint.core.test.utils.server.sse;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@WebServlet(asyncSupported = true)
public class SSEServlet implements Servlet {

  private final List<AsyncContext> asyncContexts = new ArrayList<>();
  private final List<String> pendingEvents = new ArrayList<>();

  @Override
  public synchronized void service(ServletRequest request, ServletResponse response) throws IOException {
    var asyncContext = request.startAsync();
    asyncContext.setTimeout(0);
    asyncContexts.add(asyncContext);
    setHeadersForResponse((HttpServletResponse) response);
    sendPendingEventsIfNeeded(asyncContext);
  }

  private static void setHeadersForResponse(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("text/event-stream");
    // By adding this header, and not closing the connection,
    // we disable HTTP chunking, and we can use write()+flush()
    // to send data in the text/event-stream protocol
    response.setHeader("Connection", "close");
    response.flushBuffer();
  }

  private void sendPendingEventsIfNeeded(AsyncContext asyncContext) {
    if (!pendingEvents.isEmpty()) {
      pendingEvents.forEach(event -> sendEventToClient(asyncContext, event));
      pendingEvents.clear();
    }
  }

  public synchronized void sendEventToAllClients(String eventPayload) {
    if (asyncContexts.isEmpty()) {
      pendingEvents.add(eventPayload);
    } else {
      asyncContexts.forEach(asyncContext -> sendEventToClient(asyncContext, eventPayload));
    }
  }

  private static void sendEventToClient(AsyncContext asyncContext, String eventPayload) {
    try {
      var outputStream = asyncContext.getResponse().getOutputStream();
      outputStream.write(eventPayload.getBytes(StandardCharsets.UTF_8));
      outputStream.flush();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot send event to client", e);
    }

  }

  @Override
  public void init(ServletConfig config) {
    // no-op
  }

  @Override
  public ServletConfig getServletConfig() {
    return null;
  }

  @Override
  public String getServletInfo() {
    return "Server Sent Event servlet";
  }

  @Override
  public void destroy() {
    // no-op
  }
}
