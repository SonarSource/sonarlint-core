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

import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.authentication.AuthenticationHelperService;
import org.sonarsource.sonarlint.core.clientapi.backend.authentication.HelpGenerateUserTokenParams;
import org.sonarsource.sonarlint.core.clientapi.backend.authentication.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.embedded.server.AwaitingUserTokenFutureRepository;
import org.sonarsource.sonarlint.core.embedded.server.EmbeddedServer;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

import java.util.concurrent.CompletableFuture;

import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

public class AuthenticationHelperServiceImpl implements AuthenticationHelperService {
  private static final Version MIN_SQ_VERSION_SUPPORTING_AUTOMATIC_TOKEN_GENERATION = Version.create("9.7");

  private final SonarLintClient client;
  private final EmbeddedServer embeddedServer;
  private final AwaitingUserTokenFutureRepository awaitingUserTokenFutureRepository;
  private String clientName;

  public AuthenticationHelperServiceImpl(SonarLintClient client, EmbeddedServer embeddedServer, AwaitingUserTokenFutureRepository awaitingUserTokenFutureRepository) {
    this.client = client;
    this.embeddedServer = embeddedServer;
    this.awaitingUserTokenFutureRepository = awaitingUserTokenFutureRepository;
  }

  public void initialize(String clientName) {
    this.clientName = clientName;
  }

  @Override
  public CompletableFuture<HelpGenerateUserTokenResponse> helpGenerateUserToken(HelpGenerateUserTokenParams params) {
    var futureTokenResponse = new CompletableFuture<HelpGenerateUserTokenResponse>();

    // go async to release the current thread
    CompletableFuture.runAsync(() -> {
      var serverBaseUrl = params.getServerUrl();

      doesServerSupportAutomaticUserTokenGeneration(serverBaseUrl, params.isSonarCloud())
        .handle(
          (automaticTokenGenerationSupported, error) -> {
            if (error != null) {
              futureTokenResponse.completeExceptionally(error);
              return null;
            }
            client.openUrlInBrowser(new OpenUrlInBrowserParams(ServerApiHelper.concat(serverBaseUrl, getUserTokenGenerationRelativeUrlToOpen(automaticTokenGenerationSupported))));
            var shouldWaitIncomingToken = Boolean.TRUE.equals(automaticTokenGenerationSupported) && embeddedServer.isStarted();
            if (shouldWaitIncomingToken) {
              awaitingUserTokenFutureRepository.setFutureResponse(futureTokenResponse);
            } else {
              futureTokenResponse.complete(new HelpGenerateUserTokenResponse(null));
            }
            return null;
          });
    });
    return futureTokenResponse;
  }

  private String getUserTokenGenerationRelativeUrlToOpen(boolean automaticTokenGenerationSupported) {
    if (automaticTokenGenerationSupported) {
      return "/sonarlint/auth?ideName=" + urlEncode(clientName) + (embeddedServer.isStarted() ? ("&port=" + embeddedServer.getPort()) : "");
    }
    return "/account/security";
  }

  private CompletableFuture<Boolean> doesServerSupportAutomaticUserTokenGeneration(String serverUrl, boolean isSonarCloud) {
    if (!isSonarCloud) {
      var endpoint = new EndpointParams(serverUrl, false, null);
      var httpClient = client.getHttpClientNoAuth(serverUrl);
      if (httpClient != null) {
        return new ServerApi(endpoint, httpClient).system().getStatus()
          .thenApply(status -> Version.create(status.getVersion()).satisfiesMinRequirement(MIN_SQ_VERSION_SUPPORTING_AUTOMATIC_TOKEN_GENERATION));
      }
    }
    return CompletableFuture.completedFuture(false);

  }
}
