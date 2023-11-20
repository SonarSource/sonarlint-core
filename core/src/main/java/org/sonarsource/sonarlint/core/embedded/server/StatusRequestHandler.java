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

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;

@Named
@Singleton
public class StatusRequestHandler implements HttpRequestHandler {

  private final SonarLintRpcClient client;
  private final ConnectionConfigurationRepository repository;
  private final ClientInfoDto clientInfo;

  public StatusRequestHandler(SonarLintRpcClient client, ConnectionConfigurationRepository repository, InitializeParams params) {
    this.client = client;
    this.repository = repository;
    this.clientInfo = params.getClientInfo();
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
    if (!Method.GET.isSame(request.getMethod())) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }

    boolean trustedServer = Optional.ofNullable(request.getHeader("Origin"))
      .map(Header::getValue)
      .map(this::isTrustedServer)
      .orElse(false);
    var description = getDescription(trustedServer);
    response.setEntity(new StringEntity(new Gson().toJson(new StatusResponse(clientInfo.getName(), description)), ContentType.APPLICATION_JSON));

  }

  private String getDescription(boolean trustedServer) {
    if (trustedServer) {
      try {
        var getClientInfoResponse = client.getClientDescription().get(1, TimeUnit.SECONDS);
        return getClientInfoResponse.getDescription();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      } catch (TimeoutException e) {
        throw new RuntimeException(e);
      }
    }
    return "";
  }

  private boolean isTrustedServer(String serverOrigin) {
    return repository.hasConnectionWithOrigin(serverOrigin);
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
