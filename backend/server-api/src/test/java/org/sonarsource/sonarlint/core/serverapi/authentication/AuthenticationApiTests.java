/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.authentication;

import mockwebserver3.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationApiTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();
  private AuthenticationApi underTest;

  @BeforeEach
  void setUp() {
    underTest = new AuthenticationApi(mockServer.serverApiHelper());
  }

  @Test
  void test_authentication_ok() {
    mockServer.addStringResponse("/api/authentication/validate?format=json", "{\"valid\": true}");

    var validationResult = underTest.validate(new SonarLintCancelMonitor());

    assertThat(validationResult.success()).isTrue();
    assertThat(validationResult.message()).isEqualTo("Authentication successful");
  }

  @Test
  void test_authentication_ko() {
    mockServer.addStringResponse("/api/authentication/validate?format=json", "{\"valid\": false}");

    var validationResult = underTest.validate(new SonarLintCancelMonitor());

    assertThat(validationResult.success()).isFalse();
    assertThat(validationResult.message()).isEqualTo("Authentication failed");
  }

  @Test
  void test_connection_issue() {
    mockServer.addResponse("/api/authentication/validate?format=json", new MockResponse.Builder().code(500).body("Foo").build());

    var validationResult = underTest.validate(new SonarLintCancelMonitor());

    assertThat(validationResult.success()).isFalse();
    assertThat(validationResult.message()).isEqualTo("HTTP Connection failed (500): Foo");
  }
}
