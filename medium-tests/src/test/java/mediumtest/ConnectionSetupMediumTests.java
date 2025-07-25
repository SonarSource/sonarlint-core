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
package mediumtest;

import com.google.gson.JsonArray;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.sonarsource.sonarlint.core.test.utils.server.ServerFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.EMBEDDED_SERVER;

class ConnectionSetupMediumTests {

  public static final String EXPECTED_MESSAGE = "UTM parameters should match regular expression: [a-z0-9\\-]+";

  @SonarLintTest
  void it_should_open_the_sonarlint_auth_url_for_sonarcloud(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    ServerFixture.Server scServer = harness.newFakeSonarCloudServer()
      .start();

    var backend = harness.newBackend().withBackendCapability(EMBEDDED_SERVER).withClientName("ClientName").withSonarCloudConnection("connectionId").start(fakeClient);

    var futureResponse = backend.getConnectionService().helpGenerateUserToken(new HelpGenerateUserTokenParams(scServer.baseUrl()));

    verify(fakeClient, timeout(3000)).openUrlInBrowser(new URL(scServer.url("/sonarlint/auth?ideName=ClientName&port=" + backend.getEmbeddedServerPort())));

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/token"))
      .header("Content-Type", "application/json; charset=utf-8")
      .header("Origin", scServer.baseUrl())
      .POST(HttpRequest.BodyPublishers.ofString("{\"token\": \"value\"}")).build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);

    assertThat(futureResponse)
      .succeedsWithin(Duration.ofSeconds(3))
      .extracting(HelpGenerateUserTokenResponse::getToken)
      .isEqualTo("value");
  }

  @SonarLintTest
  void it_should_open_token_generation_url_for_sonarcloud_with_tracking(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    ServerFixture.Server scServer = harness.newFakeSonarCloudServer()
      .start();

    var backend = harness.newBackend().withBackendCapability(EMBEDDED_SERVER).withClientName("ClientName").withSonarCloudConnection("connectionId").start(fakeClient);

    var futureResponse = backend.getConnectionService().helpGenerateUserToken(
      new HelpGenerateUserTokenParams(scServer.baseUrl(),
        new HelpGenerateUserTokenParams.Utm("referral", "sq-ide-product-name", "create-new-sqc-connection", "generate-token-2")));

    verify(fakeClient, timeout(3000)).openUrlInBrowser(
      new URL(scServer.url("/sonarlint/auth?ideName=ClientName&port=" + backend.getEmbeddedServerPort() +
        "&utm_medium=referral&utm_source=sq-ide-product-name&utm_content=create-new-sqc-connection&utm_term=generate-token-2")));

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/token"))
      .header("Content-Type", "application/json; charset=utf-8")
      .header("Origin", scServer.baseUrl())
      .POST(HttpRequest.BodyPublishers.ofString("{\"token\": \"value\"}")).build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);

    assertThat(futureResponse)
      .succeedsWithin(Duration.ofSeconds(3))
      .extracting(HelpGenerateUserTokenResponse::getToken)
      .isEqualTo("value");
  }

  @SonarLintTest
  void it_should_throw_invalid_parameters_for_invalid_utm_params(SonarLintTestHarness harness) {
    var backend = harness.newBackend().withBackendCapability(EMBEDDED_SERVER).withClientName("ClientName").withSonarCloudConnection("connectionId").start();

    var futureResponse = backend.getConnectionService().helpGenerateUserToken(
      new HelpGenerateUserTokenParams("irrelevant",
        new HelpGenerateUserTokenParams.Utm("referral", "sq-ide-product-name", "create-new-sqc-connection", "INVALID")));

    assertThat(futureResponse)
      .failsWithin(Duration.ofSeconds(3))
      .withThrowableOfType(ExecutionException.class)
      .withCauseInstanceOf(ResponseErrorException.class)
      .havingCause()
      .withMessage(EXPECTED_MESSAGE)
      .extracting("responseError.message", "responseError.code", "responseError.data")
      .containsOnly(EXPECTED_MESSAGE, ResponseErrorCode.InvalidParams.getValue(), utmArray());
  }

  @NotNull
  private static JsonArray utmArray() {
    JsonArray arrayOfInvalidParameters = new JsonArray();
    arrayOfInvalidParameters.add("utm_term");
    return arrayOfInvalidParameters;
  }

  @SonarLintTest
  void it_should_open_the_sonarlint_auth_url_for_sonarqube_9_7_plus(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    var server = harness.newFakeSonarQubeServer("9.9").start();
    var backend = harness.newBackend().withBackendCapability(EMBEDDED_SERVER).withClientName("ClientName").withSonarQubeConnection("connectionId", server).start(fakeClient);

    var futureResponse = backend.getConnectionService().helpGenerateUserToken(new HelpGenerateUserTokenParams(server.baseUrl()));

    verify(fakeClient, timeout(3000)).openUrlInBrowser(new URL(server.url("/sonarlint/auth?ideName=ClientName&port=" + backend.getEmbeddedServerPort())));

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/token"))
      .header("Content-Type", "application/json; charset=utf-8")
      .header("Origin", server.baseUrl())
      .POST(HttpRequest.BodyPublishers.ofString("{\"token\": \"value\"}")).build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);

    assertThat(futureResponse)
      .succeedsWithin(Duration.ofSeconds(3))
      .extracting(HelpGenerateUserTokenResponse::getToken)
      .isEqualTo("value");
  }

  @SonarLintTest
  void it_should_reject_tokens_from_missing_origin(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    var server = harness.newFakeSonarQubeServer("9.9").start();
    var backend = harness.newBackend().withBackendCapability(EMBEDDED_SERVER).withClientName("ClientName").withSonarQubeConnection("connectionId", server).start(fakeClient);

    backend.getConnectionService().helpGenerateUserToken(new HelpGenerateUserTokenParams(server.baseUrl()));

    verify(fakeClient, timeout(3000)).openUrlInBrowser(new URL(server.url("/sonarlint/auth?ideName=ClientName&port=" + backend.getEmbeddedServerPort())));

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/token"))
      .header("Content-Type", "application/json; charset=utf-8")
      .POST(HttpRequest.BodyPublishers.ofString("{\"token\": \"value\"}")).build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(400);
  }

  @SonarLintTest
  void it_should_reject_tokens_from_unexpected_origin(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    var server = harness.newFakeSonarQubeServer("9.9").start();
    var backend = harness.newBackend().withBackendCapability(EMBEDDED_SERVER).withClientName("ClientName").withSonarQubeConnection("connectionId", server).start(fakeClient);

    backend.getConnectionService().helpGenerateUserToken(new HelpGenerateUserTokenParams(server.baseUrl()));

    verify(fakeClient, timeout(3000)).openUrlInBrowser(new URL(server.url("/sonarlint/auth?ideName=ClientName&port=" + backend.getEmbeddedServerPort())));

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/token"))
      .header("Content-Type", "application/json; charset=utf-8")
      .header("Origin", "https://unexpected.sonar")
      .POST(HttpRequest.BodyPublishers.ofString("{\"token\": \"value\"}")).build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @SonarLintTest
  void it_should_open_the_sonarlint_auth_url_without_port_for_sonarqube_9_7_plus_when_server_is_not_started(SonarLintTestHarness harness) throws MalformedURLException {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend().withClientName("ClientName").start(fakeClient);
    var server = harness.newFakeSonarQubeServer("9.9").start();

    var futureResponse = backend.getConnectionService().helpGenerateUserToken(new HelpGenerateUserTokenParams(server.baseUrl()));

    assertThat(futureResponse)
      .succeedsWithin(Duration.ofSeconds(3))
      .extracting(HelpGenerateUserTokenResponse::getToken)
      .isNull();
    verify(fakeClient, timeout(3000)).openUrlInBrowser(new URL(server.url("/sonarlint/auth?ideName=ClientName")));
  }

  @SonarLintTest
  void it_should_reject_incoming_user_token_with_wrong_http_method(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend().withBackendCapability(EMBEDDED_SERVER).start(fakeClient);

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/token"))
      .GET().build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(400);
  }

  @SonarLintTest
  void it_should_reject_incoming_user_token_with_wrong_body(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend().withBackendCapability(EMBEDDED_SERVER).start(fakeClient);

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/token"))
      .header("Content-Type", "application/json; charset=utf-8")
      .POST(HttpRequest.BodyPublishers.ofString("{\"token\":")).build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(400);
  }

  @SonarLintTest
  void it_should_fail_to_validate_connection_if_host_not_found(SonarLintTestHarness harness) throws InterruptedException, ExecutionException {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend().start(fakeClient);

    var connectionResponse = backend.getConnectionService()
      .validateConnection(new ValidateConnectionParams(new TransientSonarQubeConnectionDto("http://notexists", Either.forRight(new UsernamePasswordDto("foo", "bar"))))).get();

    assertThat(connectionResponse.isSuccess()).isFalse();
    assertThat(connectionResponse.getMessage()).contains("notexists");
  }
}
