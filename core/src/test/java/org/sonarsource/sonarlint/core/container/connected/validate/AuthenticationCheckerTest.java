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
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticationCheckerTest {

  @Test
  public void test_authentication_ok() {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithResponse("api/authentication/validate?format=json", "{\"valid\": true}");

    AuthenticationChecker checker = new AuthenticationChecker(wsClient);
    ValidationResult validationResult = checker.validateCredentials();
    assertThat(validationResult.success()).isTrue();
    assertThat(validationResult.message()).isEqualTo("Authentication successful");
  }

  @Test
  public void test_authentication_ko() {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithResponse("api/authentication/validate?format=json", "{\"valid\": false}");

    AuthenticationChecker checker = new AuthenticationChecker(wsClient);
    ValidationResult validationResult = checker.validateCredentials();
    assertThat(validationResult.success()).isFalse();
    assertThat(validationResult.message()).isEqualTo("Authentication failed");
  }

  @Test
  public void test_connection_issue() {
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    WsClientTestUtils.addFailedResponse(wsClient, "api/authentication/validate?format=json", 500, "Foo");

    AuthenticationChecker checker = new AuthenticationChecker(wsClient);
    ValidationResult validationResult = checker.validateCredentials();
    assertThat(validationResult.success()).isFalse();
    assertThat(validationResult.message()).isEqualTo("HTTP Connection failed (500): Foo");
  }
}
