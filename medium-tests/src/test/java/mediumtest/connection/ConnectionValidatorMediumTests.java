/*
 * SonarLint Core - Medium Tests
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
package mediumtest.connection;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarcloud.ws.Organizations;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.test.utils.ProtobufUtils.protobufBody;

class ConnectionValidatorMediumTests {

  @RegisterExtension
  static WireMockExtension serverMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @SonarLintTest
  void test_connection_ok(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(serverMock.baseUrl())
      .start();
    serverMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withBody("{\"id\": \"20160308094653\",\"version\": \"9.9\",\"status\": \"UP\"}")));
    serverMock.stubFor(get("/api/authentication/validate?format=json")
      .willReturn(aResponse().withBody("{\"valid\": true}")));

    var response = backend.getConnectionService().validateConnection(new ValidateConnectionParams(new TransientSonarQubeConnectionDto(serverMock.baseUrl(), Either.forLeft(new TokenDto(null))))).join();

    assertThat(response.isSuccess()).isTrue();
  }

  @SonarLintTest
  void test_connection_organization_not_found(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(serverMock.baseUrl())
      .start();
    serverMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withBody("{\"id\": \"20160308094653\",\"version\": \"9.9\",\"status\": \"UP\"}")));
    serverMock.stubFor(get("/api/authentication/validate?format=json")
      .willReturn(aResponse().withBody("{\"valid\": true}")));
    serverMock.stubFor(get("/api/organizations/search.protobuf?organizations=myOrg&ps=500&p=1")
      .willReturn(aResponse().withResponseBody(protobufBody(Organizations.SearchWsResponse.newBuilder().build()))));

    var response = backend.getConnectionService().validateConnection(new ValidateConnectionParams(new TransientSonarCloudConnectionDto("myOrg", Either.forLeft(new TokenDto(null)), SonarCloudRegion.EU))).join();

    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getMessage()).isEqualTo("No organizations found for key: myOrg");
  }

  @SonarLintTest
  void test_connection_ok_with_org(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(serverMock.baseUrl())
      .start();
    serverMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withBody("{\"id\": \"20160308094653\",\"version\": \"9.9\",\"status\": \"UP\"}")));
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

    var response = backend.getConnectionService().validateConnection(new ValidateConnectionParams(new TransientSonarCloudConnectionDto("myOrg", Either.forLeft(new TokenDto(null)), SonarCloudRegion.EU))).join();

    assertThat(response.isSuccess()).isTrue();
  }

  @SonarLintTest
  void test_connection_ok_without_org(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(serverMock.baseUrl())
      .start();
    serverMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withBody("{\"id\": \"20160308094653\",\"version\": \"9.9\",\"status\": \"UP\"}")));
    serverMock.stubFor(get("/api/authentication/validate?format=json")
      .willReturn(aResponse().withBody("{\"valid\": true}")));
    var response = backend.getConnectionService().validateConnection(new ValidateConnectionParams(new TransientSonarCloudConnectionDto(null, Either.forLeft(new TokenDto(null)), SonarCloudRegion.EU))).join();

    assertThat(response.isSuccess()).isTrue();
  }

  @SonarLintTest
  void test_unsupported_server(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(serverMock.baseUrl())
      .start();
    serverMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withBody("{\"id\": \"20160308094653\",\"version\": \"6.7\",\"status\": \"UP\"}")));

    var response = backend.getConnectionService().validateConnection(new ValidateConnectionParams(new TransientSonarQubeConnectionDto(serverMock.baseUrl(), Either.forLeft(new TokenDto(null))))).join();

    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getMessage()).isEqualTo("Your SonarQube Server instance has version 6.7. Version should be greater or equal to 9.9");
  }

  @SonarLintTest
  void test_client_error(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(serverMock.baseUrl())
      .start();
    serverMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withStatus(400)));

    var response = backend.getConnectionService().validateConnection(new ValidateConnectionParams(new TransientSonarQubeConnectionDto(serverMock.baseUrl(),
      Either.forRight(new UsernamePasswordDto("foo", "bar"))))).join();

    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getMessage()).isEqualTo("Error 400 on " + serverMock.baseUrl() + "/api/system/status");
  }

  @SonarLintTest
  void test_response_error(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(serverMock.baseUrl())
      .start();
    serverMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withBody("{\"id\": }")));

    var response = backend.getConnectionService().validateConnection(new ValidateConnectionParams(new TransientSonarQubeConnectionDto(serverMock.baseUrl(),
      Either.forRight(new UsernamePasswordDto("foo", "bar"))))).join();

    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getMessage()).isEqualTo("Unable to parse server infos from: {\"id\": }");
  }

  @SonarLintTest
  void should_catch_connection_error_to_server(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(serverMock.baseUrl())
      .start();

    var response = backend.getConnectionService().validateConnection(new ValidateConnectionParams(new TransientSonarQubeConnectionDto("https://foo.bar:1234",
      Either.forLeft(new TokenDto("token"))))).join();

    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getMessage()).startsWith("java.net.UnknownHostException:");
  }

}
