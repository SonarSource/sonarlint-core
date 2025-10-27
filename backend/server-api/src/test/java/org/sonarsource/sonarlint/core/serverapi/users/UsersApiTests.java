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
package org.sonarsource.sonarlint.core.serverapi.users;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

import static org.assertj.core.api.Assertions.assertThat;

class UsersApiTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private UsersApi underTest;

  @BeforeEach
  void setUp() {
    // default with SonarCloud organization to trigger api base URL and isSonarCloud = true
    ServerApiHelper helper = mockServer.serverApiHelper("orgKey");
    underTest = new UsersApi(helper);
  }

  @Test
  void should_return_user_id_on_sonarcloud() {
    mockServer.addStringResponse("/users/current", """
      {
        "isLoggedIn": true,
        "id": "16c9b3b3-3f7e-4d61-91fe-31d731456c08",
        "login": "obiwan.kenobi"
      }""");

    var id = underTest.getCurrentUserId(new SonarLintCancelMonitor());

    assertThat(id).isEqualTo("16c9b3b3-3f7e-4d61-91fe-31d731456c08");
  }

  @Test
  void should_return_null_on_sonarqube_server() {
    var helperSqs = mockServer.serverApiHelper(null); // isSonarCloud = false
    var api = new UsersApi(helperSqs);

    var id = api.getCurrentUserId(new SonarLintCancelMonitor());

    assertThat(id).isNull();
  }

  @Test
  void should_return_null_on_malformed_response() {
    mockServer.addStringResponse("/users/current", "{}");

    var id = underTest.getCurrentUserId(new SonarLintCancelMonitor());

    assertThat(id).isNull();
  }

}


