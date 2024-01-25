/*
 * SonarLint Core - Medium Tests
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
package mediumtest.connection;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarcloud.ws.Organizations;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static testutils.TestUtils.protobufBody;

class ConnectionValidatorMediumTests {
  private static SonarLintRpcServer backend;
  private static String oldSonarCloudUrl;

  @RegisterExtension
  static WireMockExtension serverMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @BeforeAll
  static void createBackend() {
    oldSonarCloudUrl = System.getProperty("sonarlint.internal.sonarcloud.url");
    System.setProperty("sonarlint.internal.sonarcloud.url", serverMock.baseUrl());
    backend = newBackend().build();
  }

  @AfterAll
  static void tearDown() {
    if (backend != null) {
      backend.shutdown().join();
    }
    if (oldSonarCloudUrl == null) {
      System.clearProperty("sonarlint.internal.sonarcloud.url");
    } else {
      System.setProperty("sonarlint.internal.sonarcloud.url", oldSonarCloudUrl);
    }
  }

  @Test
  void testConnection_ok() {
    serverMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withBody("{\"id\": \"20160308094653\",\"version\": \"7.9\",\"status\": \"UP\"}")));
    serverMock.stubFor(get("/api/authentication/validate?format=json")
      .willReturn(aResponse().withBody("{\"valid\": true}")));

    var response = backend.getConnectionService().validateConnection(new ValidateConnectionParams(new TransientSonarQubeConnectionDto(serverMock.baseUrl(), Either.forLeft(new TokenDto(null))))).join();

    assertThat(response.isSuccess()).isTrue();
  }

  @Test
  void testConnectionOrganizationNotFound() {
    serverMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withBody("{\"id\": \"20160308094653\",\"version\": \"7.9\",\"status\": \"UP\"}")));
    serverMock.stubFor(get("/api/authentication/validate?format=json")
      .willReturn(aResponse().withBody("{\"valid\": true}")));
    serverMock.stubFor(get("/api/organizations/search.protobuf?organizations=myOrg&ps=500&p=1")
      .willReturn(aResponse().withResponseBody(protobufBody(Organizations.SearchWsResponse.newBuilder().build()))));

    var response = backend.getConnectionService().validateConnection(new ValidateConnectionParams(new TransientSonarCloudConnectionDto("myOrg", Either.forLeft(new TokenDto(null))))).join();

    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getMessage()).isEqualTo("No organizations found for key: myOrg");
  }

  @Test
  void testConnection_ok_with_org() {
    serverMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withBody("{\"id\": \"20160308094653\",\"version\": \"7.9\",\"status\": \"UP\"}")));
    serverMock.stubFor(get("/api/authentication/validate?format=json")
      .willReturn(aResponse().withBody("{\"valid\": true}")));
    serverMock.stubFor(get("/api/organizations/search.protobuf?organizations=myOrg&ps=500&p=1")
      .willReturn(aResponse().withResponseBody(protobufBody(Organizations.SearchWsResponse.newBuilder()
        .addOrganizations(Organizations.Organization.newBuilder()
          .setKey("myOrg")
          .setName("My Org")
          .build())
        .build()))));
    serverMock.stubFor(get("/api/organizations/search.protobuf?organizations=myOrg&ps=500&p=2")
      .willReturn(aResponse().withResponseBody(protobufBody(Organizations.SearchWsResponse.newBuilder().build()))));

    var response = backend.getConnectionService().validateConnection(new ValidateConnectionParams(new TransientSonarCloudConnectionDto("myOrg", Either.forLeft(new TokenDto(null))))).join();

    assertThat(response.isSuccess()).isTrue();
  }

  @Test
  void testUnsupportedServer() {
    serverMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withBody("{\"id\": \"20160308094653\",\"version\": \"6.7\",\"status\": \"UP\"}")));

    var response = backend.getConnectionService().validateConnection(new ValidateConnectionParams(new TransientSonarQubeConnectionDto(serverMock.baseUrl(), Either.forLeft(new TokenDto(null))))).join();

    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getMessage()).isEqualTo("SonarQube server has version 6.7. Version should be greater or equal to 7.9");
  }

  @Test
  void testClientError() throws ExecutionException, InterruptedException {
    serverMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withStatus(400)));

    var response = backend.getConnectionService().validateConnection(new ValidateConnectionParams(new TransientSonarQubeConnectionDto(serverMock.baseUrl(), Either.forLeft(new TokenDto(null))))).join();

    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getMessage()).isEqualTo("Error 400 on " + serverMock.baseUrl() + "/api/system/status");
  }

  @Test
  void testResponseError() throws ExecutionException, InterruptedException {
    serverMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withBody("{\"id\": }")));

    var response = backend.getConnectionService().validateConnection(new ValidateConnectionParams(new TransientSonarQubeConnectionDto(serverMock.baseUrl(), Either.forLeft(new TokenDto(null))))).join();

    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getMessage()).isEqualTo("Unable to parse server infos from: {\"id\": }");
  }

}
