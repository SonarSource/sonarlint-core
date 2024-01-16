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
package org.sonarsource.sonarlint.core.http;

import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static org.sonarsource.sonarlint.core.commons.ThreadFactories.threadWithNamePrefix;

@Named
@Singleton
public class ConnectionAwareHttpClientProvider {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final SonarLintRpcClient client;
  private final HttpClientProvider httpClientProvider;
  private final ExecutorService webSocketThreadPool;

  public ConnectionAwareHttpClientProvider(SonarLintRpcClient client, HttpClientProvider httpClientProvider) {
    this.client = client;
    this.httpClientProvider = httpClientProvider;
    this.webSocketThreadPool = Executors.newCachedThreadPool(threadWithNamePrefix("sonarcloud-websocket-"));
  }

  public HttpClient getHttpClient() {
    return httpClientProvider.getHttpClient();
  }

  public HttpClient getHttpClient(String connectionId) {
    var credentials = queryClientForConnectionCredentials(connectionId);
    if (credentials.isEmpty()) {
      // Fallback on client with no authentication
      return httpClientProvider.getHttpClient();
    }
    return credentials.get().map(
      tokenDto -> httpClientProvider.getHttpClientWithPreemptiveAuth(tokenDto.getToken(), null),
      userPass -> httpClientProvider.getHttpClientWithPreemptiveAuth(userPass.getUsername(), userPass.getPassword()));
  }

  public WebSocketClient getWebSocketClient(String connectionId) {
    var credentials = queryClientForConnectionCredentials(connectionId);
    if (credentials.isEmpty()) {
      throw new IllegalStateException("No credentials for connection " + connectionId);
    }
    if (credentials.get().isRight()) {
      // We are normally only supporting tokens for SonarCloud connections
      throw new IllegalStateException("Expected token for connection " + connectionId);
    }
    return new WebSocketClient(credentials.get().getLeft().getToken(), webSocketThreadPool);
  }

  private Optional<Either<TokenDto, UsernamePasswordDto>> queryClientForConnectionCredentials(String connectionId) {
    var response = client.getCredentials(new GetCredentialsParams(connectionId)).join();
    var credentials = response.getCredentials();
    if (credentials == null) {
      LOG.debug("No credentials for connection '{}'", connectionId);
      return Optional.empty();
    } else {
      return Optional.of(credentials);
    }
  }

  @PreDestroy
  public void close() {
    if (!MoreExecutors.shutdownAndAwaitTermination(webSocketThreadPool, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop web socket executor service in a timely manner");
    }
  }
}
