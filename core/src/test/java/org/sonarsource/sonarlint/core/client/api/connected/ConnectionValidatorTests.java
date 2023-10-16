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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.util.concurrent.ExecutionException;
import mockwebserver3.MockResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.exception.UnsupportedServerException;
import org.sonarsource.sonarlint.core.serverconnection.ServerVersionAndStatusChecker;
import testutils.MockWebServerExtensionWithProtobuf;
import testutils.NoopCancelChecker;
import testutils.TakeThreadDumpAfter;
import testutils.ThreadDumpExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({ThreadDumpExtension.class})
class ConnectionValidatorTests {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private final ServerVersionAndStatusChecker serverChecker = mock(ServerVersionAndStatusChecker.class);

  @Test
  @TakeThreadDumpAfter(seconds = 20)
  void testConnection_ok() throws ExecutionException, InterruptedException {
    var underTest = new ConnectionValidator(new ServerApiHelper(mockServer.endpointParams(), HttpClientProvider.forTesting().getHttpClient()));

    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"7.9\",\"status\": \"UP\"}");
    mockServer.addStringResponse("/api/authentication/validate?format=json", "{\"valid\": true}");

    var validation = underTest.validateConnection(new NoopCancelChecker());

    assertThat(validation.success()).isTrue();
    assertThat(mockServer.takeRequest().getPath()).isEqualTo("/api/system/status");
    assertThat(mockServer.takeRequest().getPath()).isEqualTo("/api/authentication/validate?format=json");
  }

  @Test
  @TakeThreadDumpAfter(seconds = 20)
  void testConnectionOrganizationNotFound() throws Exception {
    var underTest = new ConnectionValidator(new ServerApiHelper(mockServer.endpointParams("myOrg"), HttpClientProvider.forTesting().getHttpClient()));

    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"7.9\",\"status\": \"UP\"}");
    mockServer.addStringResponse("/api/authentication/validate?format=json", "{\"valid\": true}");
    mockServer.addResponseFromResource("/api/organizations/search.protobuf?organizations=myOrg&ps=500&p=1", "/orgs/empty.pb");

    var validation = underTest.validateConnection(new NoopCancelChecker());

    assertThat(validation.success()).isFalse();
    assertThat(validation.message()).isEqualTo("No organizations found for key: myOrg");
  }

  @Test
  @TakeThreadDumpAfter(seconds = 20)
  void testConnection_ok_with_org() throws Exception {
    var underTest = new ConnectionValidator(new ServerApiHelper(mockServer.endpointParams("henryju-github"), HttpClientProvider.forTesting().getHttpClient()));

    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"7.9\",\"status\": \"UP\"}");
    mockServer.addStringResponse("/api/authentication/validate?format=json", "{\"valid\": true}");
    mockServer.addResponseFromResource("/api/organizations/search.protobuf?organizations=henryju-github&ps=500&p=1", "/orgs/single.pb");
    mockServer.addResponseFromResource("/api/organizations/search.protobuf?organizations=henryju-github&ps=500&p=2", "/orgs/empty.pb");

    var validation = underTest.validateConnection(new NoopCancelChecker());

    assertThat(validation.success()).isTrue();
  }

  @Test
  @TakeThreadDumpAfter(seconds = 20)
  void testUnsupportedServer() throws ExecutionException, InterruptedException {
    var underTest = new ConnectionValidator(new ServerApiHelper(mockServer.endpointParams(), HttpClientProvider.forTesting().getHttpClient()));

    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"6.7\",\"status\": \"UP\"}");

    when(serverChecker.checkVersionAndStatus()).thenThrow(UnsupportedServerException.class);

    var validation = underTest.validateConnection(new NoopCancelChecker());

    assertThat(validation.success()).isFalse();
    assertThat(validation.message()).isEqualTo("SonarQube server has version 6.7. Version should be greater or equal to 7.9");
  }

  @Test
  @TakeThreadDumpAfter(seconds = 20)
  void testClientError() throws ExecutionException, InterruptedException {
    var underTest = new ConnectionValidator(new ServerApiHelper(mockServer.endpointParams(), HttpClientProvider.forTesting().getHttpClient()));

    var mockResponse = new MockResponse();
    mockResponse.setResponseCode(400);
    mockServer.addResponse("/api/system/status", mockResponse);

    var validation = underTest.validateConnection(new NoopCancelChecker());

    assertThat(validation.success()).isFalse();
    assertThat(validation.message()).isEqualTo("Error 400 on " + mockServer.endpointParams().getBaseUrl() + "api/system/status");
  }

  @Test
  @TakeThreadDumpAfter(seconds = 20)
  void testResponseError() throws ExecutionException, InterruptedException {
    var underTest = new ConnectionValidator(new ServerApiHelper(mockServer.endpointParams(), HttpClientProvider.forTesting().getHttpClient()));

    mockServer.addStringResponse("/api/system/status", "{\"id\": }");

    var validation = underTest.validateConnection(new NoopCancelChecker());

    assertThat(validation.success()).isFalse();
    assertThat(validation.message()).isEqualTo("Unable to parse server infos from: {\"id\": }");
  }

}
