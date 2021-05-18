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
package org.sonarsource.sonarlint.core.client.api.connected;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectionValidatorTests {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  public static final String PLUGINS_INSTALLED = "{\"plugins\": [\n" +
    "    {\n" +
    "      \"key\": \"branch\",\n" +
    "      \"filename\": \"sonar-branch-plugin-1.1.0.879.jar\",\n" +
    "      \"sonarLintSupported\": false,\n" +
    "      \"hash\": \"064d334d27aa14aab6e39315428ee3cf\",\n" +
    "      \"version\": \"1.1 (build 879)\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"key\": \"javascript\",\n" +
    "      \"filename\": \"sonar-javascript-plugin-3.4.0.5828.jar\",\n" +
    "      \"sonarLintSupported\": true,\n" +
    "      \"hash\": \"d136fdb31fe38c3d780650f7228a49fa\",\n" +
    "      \"version\": \"3.4 (build 5828)\"\n" +
    "    } ]}";

  private final ServerVersionAndStatusChecker serverChecker = mock(ServerVersionAndStatusChecker.class);

  @Test
  void testConnection_ok() throws Exception {
    ConnectionValidator underTest = new ConnectionValidator(new ServerApiHelper(mockServer.endpointParams(), MockWebServerExtension.httpClient()));

    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"7.9\",\"status\": \"UP\"}");
    mockServer.addStringResponse("/api/authentication/validate?format=json", "{\"valid\": true}");

    ValidationResult validation = underTest.validateConnection();

    assertThat(validation.success()).isTrue();
    assertThat(mockServer.takeRequest().getPath()).isEqualTo("/api/system/status");
    assertThat(mockServer.takeRequest().getPath()).isEqualTo("/api/authentication/validate?format=json");
  }

  @Test
  void testConnectionOrganizationNotFound() throws Exception {
    ConnectionValidator underTest = new ConnectionValidator(new ServerApiHelper(mockServer.endpointParams("myOrg"), MockWebServerExtension.httpClient()));

    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"7.9\",\"status\": \"UP\"}");
    mockServer.addStringResponse("/api/authentication/validate?format=json", "{\"valid\": true}");
    mockServer.addResponseFromResource("/api/organizations/search.protobuf?organizations=myOrg&ps=500&p=1", "/orgs/empty.pb");

    ValidationResult validation = underTest.validateConnection();

    assertThat(validation.success()).isFalse();
    assertThat(validation.message()).isEqualTo("No organizations found for key: myOrg");
  }

  @Test
  void testConnection_ok_with_org() throws Exception {
    ConnectionValidator underTest = new ConnectionValidator(new ServerApiHelper(mockServer.endpointParams("henryju-github"), MockWebServerExtension.httpClient()));

    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"7.9\",\"status\": \"UP\"}");
    mockServer.addStringResponse("/api/authentication/validate?format=json", "{\"valid\": true}");
    mockServer.addResponseFromResource("/api/organizations/search.protobuf?organizations=henryju-github&ps=500&p=1", "/orgs/single.pb");
    mockServer.addResponseFromResource("/api/organizations/search.protobuf?organizations=henryju-github&ps=500&p=2", "/orgs/empty.pb");

    ValidationResult validation = underTest.validateConnection();

    assertThat(validation.success()).isTrue();
  }

  @Test
  void testUnsupportedServer() {
    ConnectionValidator underTest = new ConnectionValidator(new ServerApiHelper(mockServer.endpointParams(), MockWebServerExtension.httpClient()));

    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"6.7\",\"status\": \"UP\"}");

    when(serverChecker.checkVersionAndStatus()).thenThrow(UnsupportedServerException.class);

    ValidationResult validation = underTest.validateConnection();

    assertThat(validation.success()).isFalse();
    assertThat(validation.message()).isEqualTo("SonarQube server has version 6.7. Version should be greater or equal to 7.9");
  }

}
