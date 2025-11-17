/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.http;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.InvalidTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

public class ConnectionAwareHttpClientProvider {
  private final SonarLintRpcClient client;
  private final HttpClientProvider httpClientProvider;

  public ConnectionAwareHttpClientProvider(SonarLintRpcClient client, HttpClientProvider httpClientProvider) {
    this.client = client;
    this.httpClientProvider = httpClientProvider;
  }

  public HttpClient getHttpClient() {
    return httpClientProvider.getHttpClient();
  }

  public HttpClient getHttpClient(String connectionId, boolean shouldUseBearer) {
    try {
      var credentials = queryClientForConnectionCredentials(connectionId);
      return credentials.map(
        tokenDto -> httpClientProvider.getHttpClientWithPreemptiveAuth(tokenDto.getToken(), shouldUseBearer),
        userPass -> httpClientProvider.getHttpClientWithPreemptiveAuth(userPass.getUsername(), userPass.getPassword()));
    } catch (IllegalStateException e) {
      client.invalidToken(new InvalidTokenParams(connectionId));
      throw e;
    }
  }

  public WebSocketClient getWebSocketClient(String connectionId) {
    var credentials = queryClientForConnectionCredentials(connectionId);
    if (credentials.isRight()) {
      // We are normally only supporting tokens for SonarCloud connections
      throw new IllegalStateException("Expected token for connection " + connectionId);
    }
    return httpClientProvider.getWebSocketClient(credentials.getLeft().getToken());
  }

  private Either<TokenDto, UsernamePasswordDto> queryClientForConnectionCredentials(String connectionId) {
    var response = client.getCredentials(new GetCredentialsParams(connectionId)).join();
    var credentials = response.getCredentials();
    validateCredentials(connectionId, credentials);
    return credentials;
  }

  private static void validateCredentials(String connectionId, @Nullable Either<TokenDto, UsernamePasswordDto> credentials) {
    if (credentials == null) {
      throw new IllegalStateException("No credentials for connection " + connectionId);
    }
    if (credentials.isLeft()) {
      if(isNullOrEmpty(credentials.getLeft().getToken())) {
        throw new IllegalStateException("No token for connection " + connectionId);
      }
      return;
    }
    var right = credentials.getRight();
    if (right == null) {
      throw new IllegalStateException("No username/password for connection " + connectionId);
    }
    if (isNullOrEmpty(right.getUsername())) {
      throw new IllegalStateException("No username for connection " + connectionId);
    }
    if (isNullOrEmpty(right.getPassword())) {
      throw new IllegalStateException("No password for connection " + connectionId);
    }
  }

  private static boolean isNullOrEmpty(@Nullable String s) {
    return s == null || s.trim().isEmpty();
  }
}
