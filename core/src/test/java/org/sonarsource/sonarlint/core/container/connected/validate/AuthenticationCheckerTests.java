/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.validate;

import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationCheckerTests {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();
  private AuthenticationChecker underTest;

  @BeforeEach
  public void setUp() {
    underTest = new AuthenticationChecker(mockServer.slClient());
  }

  @Test
  void test_authentication_ok() {
    mockServer.addStringResponse("/api/authentication/validate?format=json", "{\"valid\": true}");

    ValidationResult validationResult = underTest.validateCredentials();

    assertThat(validationResult.success()).isTrue();
    assertThat(validationResult.message()).isEqualTo("Authentication successful");
  }

  @Test
  void test_authentication_ko() {
    mockServer.addStringResponse("/api/authentication/validate?format=json", "{\"valid\": false}");

    ValidationResult validationResult = underTest.validateCredentials();

    assertThat(validationResult.success()).isFalse();
    assertThat(validationResult.message()).isEqualTo("Authentication failed");
  }

  @Test
  void test_connection_issue() {
    mockServer.addResponse("/api/authentication/validate?format=json", new MockResponse().setResponseCode(500).setBody("Foo"));

    ValidationResult validationResult = underTest.validateCredentials();

    assertThat(validationResult.success()).isFalse();
    assertThat(validationResult.message()).isEqualTo("HTTP Connection failed (500): Foo");
  }
}
