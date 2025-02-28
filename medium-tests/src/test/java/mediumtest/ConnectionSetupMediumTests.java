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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.check.CheckSmartNotificationsSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.sonarsource.sonarlint.core.test.utils.server.ServerFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class ConnectionSetupMediumTests {

  @SonarLintTest
  void it_should_open_the_sonarlint_auth_url_for_sonarcloud(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    ServerFixture.Server scServer = harness.newFakeSonarCloudServer()
      .start();

    var backend = harness.newBackend().withEmbeddedServer().withClientName("ClientName").withSonarCloudConnection("connectionId").start(fakeClient);

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
  void it_should_open_the_sonarlint_auth_url_for_sonarqube_9_7_plus(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    var server = harness.newFakeSonarQubeServer("9.9").start();
    var backend = harness.newBackend().withEmbeddedServer().withClientName("ClientName").withSonarQubeConnection("connectionId", server).start(fakeClient);

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
    var backend = harness.newBackend().withEmbeddedServer().withClientName("ClientName").withSonarQubeConnection("connectionId", server).start(fakeClient);

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
    var backend = harness.newBackend().withEmbeddedServer().withClientName("ClientName").withSonarQubeConnection("connectionId", server).start(fakeClient);

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
    var backend = harness.newBackend().withEmbeddedServer().start(fakeClient);

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/token"))
      .GET().build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(400);
  }

  @SonarLintTest
  void it_should_reject_incoming_user_token_with_wrong_body(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend().withEmbeddedServer().start(fakeClient);

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

  @SonarLintTest
  void it_should_almways_support_notifications(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend().start(fakeClient);

    var connectionResponse = backend.getConnectionService()
      .checkSmartNotificationsSupported(new CheckSmartNotificationsSupportedParams(
        new TransientSonarCloudConnectionDto("https://sonarcloud.io", Either.forLeft(new TokenDto("foo")), SonarCloudRegion.EU)))
      .get();

    assertThat(connectionResponse.isSuccess()).isTrue();
  }
}
