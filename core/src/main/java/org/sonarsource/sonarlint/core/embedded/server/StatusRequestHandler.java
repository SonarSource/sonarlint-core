/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.ClientInfoDto;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;

public class StatusRequestHandler implements HttpRequestHandler {

  private final SonarLintClient client;
  private final ConnectionConfigurationRepository connectionRepository;
  private final ClientInfoDto clientInfo;

  public StatusRequestHandler(SonarLintClient client, ConnectionConfigurationRepository connectionRepository, ClientInfoDto clientInfo) {
    this.client = client;
    this.connectionRepository = connectionRepository;
    this.clientInfo = clientInfo;
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
    boolean trustedServer = Optional.ofNullable(request.getHeader("Origin"))
      .map(Header::getValue)
      .map(this::isTrustedServer)
      .orElse(false);
    getDescription(trustedServer)
      .thenAccept(description -> response.setEntity(new StringEntity(new Gson().toJson(new StatusResponse(clientInfo.getName(), description)), ContentType.APPLICATION_JSON)));

  }

  private CompletableFuture<String> getDescription(boolean trustedServer) {
    if (trustedServer) {
      var edition = clientInfo.getEdition();
      var editionString = edition == null ? "" : (" (" + edition + ")");
      return client.getWorkspaceInfo().thenApply(workspaceInfo -> clientInfo.getVersion() + " - " + workspaceInfo.getTitle() + editionString);
    }
    return CompletableFuture.completedFuture("");
  }

  private boolean isTrustedServer(String serverOrigin) {
    return connectionRepository.hasConnectionWithOrigin(serverOrigin);
  }

  private static class StatusResponse {
    @Expose
    private final String ideName;
    @Expose
    private final String description;

    public StatusResponse(String ideName, String description) {
      this.ideName = ideName;
      this.description = description;
    }
  }
}
