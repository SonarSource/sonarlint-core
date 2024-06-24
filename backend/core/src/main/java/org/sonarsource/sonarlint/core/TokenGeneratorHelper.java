/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core;

import java.util.concurrent.CompletableFuture;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.embedded.server.AwaitingUserTokenFutureRepository;
import org.sonarsource.sonarlint.core.embedded.server.EmbeddedServer;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

@Named
@Singleton
public class TokenGeneratorHelper {

  private final SonarLintRpcClient client;
  private final EmbeddedServer embeddedServer;
  private final AwaitingUserTokenFutureRepository awaitingUserTokenFutureRepository;

  private final String clientName;

  public TokenGeneratorHelper(SonarLintRpcClient client, EmbeddedServer embeddedServer, AwaitingUserTokenFutureRepository awaitingUserTokenFutureRepository,
    InitializeParams params) {
    this.client = client;
    this.embeddedServer = embeddedServer;
    this.awaitingUserTokenFutureRepository = awaitingUserTokenFutureRepository;
    this.clientName = params.getClientConstantInfo().getName();
  }

  public HelpGenerateUserTokenResponse helpGenerateUserToken(String serverBaseUrl, SonarLintCancelMonitor cancelMonitor) {
    client.openUrlInBrowser(new OpenUrlInBrowserParams(ServerApiHelper.concat(serverBaseUrl, getUserTokenGenerationRelativeUrlToOpen())));
    var shouldWaitIncomingToken = embeddedServer.isStarted();
    if (shouldWaitIncomingToken) {
      var future = new CompletableFuture<HelpGenerateUserTokenResponse>();
      awaitingUserTokenFutureRepository.addExpectedResponse(serverBaseUrl, future);
      cancelMonitor.onCancel(() -> future.cancel(false));
      return future.join();
    } else {
      return new HelpGenerateUserTokenResponse(null);
    }
  }

  private String getUserTokenGenerationRelativeUrlToOpen() {
    return "/sonarlint/auth?ideName=" + urlEncode(clientName) + (embeddedServer.isStarted() ? ("&port=" + embeddedServer.getPort()) : "");
  }

}
