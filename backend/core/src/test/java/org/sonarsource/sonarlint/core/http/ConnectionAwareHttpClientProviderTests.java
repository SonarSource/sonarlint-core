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
package org.sonarsource.sonarlint.core.http;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.InvalidTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionAwareHttpClientProviderTests {

  private SonarLintRpcClient client;
  private ConnectionAwareHttpClientProvider underTest;

  @BeforeEach
  void setUp() {
    client = mock(SonarLintRpcClient.class);
    HttpClientProvider httpClientProvider = mock(HttpClientProvider.class);
    underTest = new ConnectionAwareHttpClientProvider(client, httpClientProvider);
  }

  @Test
  void should_call_invalidToken_notification_when_token_is_null() {
    String connectionId = "test-connection";
    boolean shouldUseBearer = true;

    Either<TokenDto, UsernamePasswordDto> nullToken = Either.forLeft(new TokenDto(null));
    GetCredentialsResponse response = new GetCredentialsResponse(nullToken);
    when(client.getCredentials(any(GetCredentialsParams.class)))
      .thenReturn(CompletableFuture.completedFuture(response));

    assertThatThrownBy(() -> underTest.getHttpClient(connectionId, shouldUseBearer))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("No token for connection " + connectionId);
    ArgumentCaptor<InvalidTokenParams> paramsCaptor = ArgumentCaptor.forClass(InvalidTokenParams.class);
    verify(client).invalidToken(paramsCaptor.capture());
    InvalidTokenParams capturedParams = paramsCaptor.getValue();
    assertThat(capturedParams.getConnectionId()).isEqualTo(connectionId);
  }
} 
