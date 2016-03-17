/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected.validate;

import org.junit.Test;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuthenticationCheckerTest {

  @Test
  public void test_authentication_ok() {
    SonarLintWsClient wsClient = mock(SonarLintWsClient.class);
    when(wsClient.getUserAgent()).thenReturn("UT");
    WsResponse wsResponse = mock(WsResponse.class);
    when(wsClient.rawGet("api/authentication/validate?format=json")).thenReturn(wsResponse);
    when(wsResponse.content()).thenReturn("{\"valid\": true}");
    when(wsResponse.code()).thenReturn(200);
    when(wsResponse.isSuccessful()).thenReturn(true);

    AuthenticationChecker checker = new AuthenticationChecker(wsClient);
    ValidationResult validationResult = checker.validateCredentials();
    assertThat(validationResult.status()).isTrue();
    assertThat(validationResult.statusCode()).isEqualTo(200);
    assertThat(validationResult.message()).isEqualTo("Authentication successful");
  }

  @Test
  public void test_authentication_ko() {
    SonarLintWsClient wsClient = mock(SonarLintWsClient.class);
    when(wsClient.getUserAgent()).thenReturn("UT");
    WsResponse wsResponse = mock(WsResponse.class);
    when(wsClient.rawGet("api/authentication/validate?format=json")).thenReturn(wsResponse);
    when(wsResponse.content()).thenReturn("{\"valid\": false}");
    when(wsResponse.code()).thenReturn(200);
    when(wsResponse.isSuccessful()).thenReturn(true);

    AuthenticationChecker checker = new AuthenticationChecker(wsClient);
    ValidationResult validationResult = checker.validateCredentials();
    assertThat(validationResult.status()).isFalse();
    assertThat(validationResult.statusCode()).isEqualTo(200);
    assertThat(validationResult.message()).isEqualTo("Authentication failed");
  }

  @Test
  public void test_connection_issue() {
    SonarLintWsClient wsClient = mock(SonarLintWsClient.class);
    when(wsClient.getUserAgent()).thenReturn("UT");
    WsResponse wsResponse = mock(WsResponse.class);
    when(wsClient.rawGet("api/authentication/validate?format=json")).thenReturn(wsResponse);
    when(wsResponse.content()).thenReturn("Foo");
    when(wsResponse.code()).thenReturn(500);
    when(wsResponse.isSuccessful()).thenReturn(false);

    AuthenticationChecker checker = new AuthenticationChecker(wsClient);
    ValidationResult validationResult = checker.validateCredentials();
    assertThat(validationResult.status()).isFalse();
    assertThat(validationResult.statusCode()).isEqualTo(500);
    assertThat(validationResult.message()).isEqualTo("HTTP Connection failed: Foo");
  }
}
