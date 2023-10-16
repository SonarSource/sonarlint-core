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
package org.sonarsource.sonarlint.core;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.embedded.server.AwaitingUserTokenFutureRepository;
import org.sonarsource.sonarlint.core.embedded.server.EmbeddedServer;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.utils.FutureUtils;

import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

@Named
@Singleton
public class TokenGeneratorHelper {
  private static final Version MIN_SQ_VERSION_SUPPORTING_AUTOMATIC_TOKEN_GENERATION = Version.create("9.7");

  private final SonarLintRpcClient client;
  private final EmbeddedServer embeddedServer;
  private final AwaitingUserTokenFutureRepository awaitingUserTokenFutureRepository;

  private final HttpClientProvider httpClientProvider;
  private final String clientName;

  public TokenGeneratorHelper(SonarLintRpcClient client, EmbeddedServer embeddedServer, AwaitingUserTokenFutureRepository awaitingUserTokenFutureRepository,
    InitializeParams params, HttpClientProvider httpClientProvider) {
    this.client = client;
    this.embeddedServer = embeddedServer;
    this.awaitingUserTokenFutureRepository = awaitingUserTokenFutureRepository;
    this.clientName = params.getClientInfo().getName();
    this.httpClientProvider = httpClientProvider;
  }

  public HelpGenerateUserTokenResponse helpGenerateUserToken(HelpGenerateUserTokenParams params, CancelChecker cancelChecker) {
    var serverBaseUrl = params.getServerUrl();

    var automaticTokenGenerationSupported = doesServerSupportAutomaticUserTokenGeneration(serverBaseUrl, params.isSonarCloud(), cancelChecker);
    client.openUrlInBrowser(new OpenUrlInBrowserParams(ServerApiHelper.concat(serverBaseUrl, getUserTokenGenerationRelativeUrlToOpen(automaticTokenGenerationSupported))));
    var shouldWaitIncomingToken = automaticTokenGenerationSupported && embeddedServer.isStarted();
    if (shouldWaitIncomingToken) {
      var future = new CompletableFuture<HelpGenerateUserTokenResponse>();
      awaitingUserTokenFutureRepository.addExpectedResponse(serverBaseUrl, future);
      return FutureUtils.waitForTaskWithResult(cancelChecker, future, "wait for user token", Duration.ofMinutes(1));
    } else {
      return new HelpGenerateUserTokenResponse(null);
    }
  }

  private String getUserTokenGenerationRelativeUrlToOpen(boolean automaticTokenGenerationSupported) {
    if (automaticTokenGenerationSupported) {
      return "/sonarlint/auth?ideName=" + urlEncode(clientName) + (embeddedServer.isStarted() ? ("&port=" + embeddedServer.getPort()) : "");
    }
    return "/account/security";
  }

  private boolean doesServerSupportAutomaticUserTokenGeneration(String serverUrl, boolean isSonarCloud, CancelChecker cancelChecker) {
    if (!isSonarCloud) {
      var endpoint = new EndpointParams(serverUrl, false, null);
      var status = FutureUtils.waitForTaskWithResult(cancelChecker, new ServerApi(endpoint, httpClientProvider.getHttpClient()).system().getStatus(), "check support for token generation", Duration.ofMinutes(1));
      return Version.create(status.getVersion()).satisfiesMinRequirement(MIN_SQ_VERSION_SUPPORTING_AUTOMATIC_TOKEN_GENERATION);
    }
    return false;

  }
}
