/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2024 SonarSource SA
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
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.check.CheckSmartNotificationsSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static mediumtest.fixtures.ServerFixture.newSonarCloudServer;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class ConnectionSetupMediumTests {

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (server != null) {
      server.shutdown();
    }
  }

  @Test
  void it_should_open_the_sonarlint_auth_url_for_sonarcloud() throws IOException, InterruptedException {
    var fakeClient = newFakeClient().build();
    ServerFixture.Server scServer = newSonarCloudServer()
      .start();

    backend = newBackend().withEmbeddedServer().withClientName("ClientName").withSonarCloudConnection("connectionId").build(fakeClient);

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

  @Test
  void it_should_open_the_sonarlint_auth_url_for_sonarqube_9_7_plus() throws IOException, InterruptedException {
    var fakeClient = newFakeClient().build();
    server = newSonarQubeServer("9.9").start();
    backend = newBackend().withEmbeddedServer().withClientName("ClientName").withSonarQubeConnection("connectionId", server).build(fakeClient);

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

  @Test
  void it_should_reject_tokens_from_missing_origin() throws IOException, InterruptedException {
    var fakeClient = newFakeClient().build();
    server = newSonarQubeServer("9.9").start();
    backend = newBackend().withEmbeddedServer().withClientName("ClientName").withSonarQubeConnection("connectionId", server).build(fakeClient);

    backend.getConnectionService().helpGenerateUserToken(new HelpGenerateUserTokenParams(server.baseUrl()));

    verify(fakeClient, timeout(3000)).openUrlInBrowser(new URL(server.url("/sonarlint/auth?ideName=ClientName&port=" + backend.getEmbeddedServerPort())));

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/token"))
      .header("Content-Type", "application/json; charset=utf-8")
      .POST(HttpRequest.BodyPublishers.ofString("{\"token\": \"value\"}")).build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  void it_should_reject_tokens_from_unexpected_origin() throws IOException, InterruptedException {
    var fakeClient = newFakeClient().build();
    server = newSonarQubeServer("9.9").start();
    backend = newBackend().withEmbeddedServer().withClientName("ClientName").withSonarQubeConnection("connectionId", server).build(fakeClient);

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

  @Test
  void it_should_open_the_sonarlint_auth_url_without_port_for_sonarqube_9_7_plus_when_server_is_not_started() throws MalformedURLException {
    var fakeClient = newFakeClient().build();
    backend = newBackend().withClientName("ClientName").build(fakeClient);
    server = newSonarQubeServer("9.9").start();

    var futureResponse = backend.getConnectionService().helpGenerateUserToken(new HelpGenerateUserTokenParams(server.baseUrl()));

    assertThat(futureResponse)
      .succeedsWithin(Duration.ofSeconds(3))
      .extracting(HelpGenerateUserTokenResponse::getToken)
      .isNull();
    verify(fakeClient, timeout(3000)).openUrlInBrowser(new URL(server.url("/sonarlint/auth?ideName=ClientName")));
  }

  @Test
  void it_should_reject_incoming_user_token_with_wrong_http_method() throws IOException, InterruptedException {
    var fakeClient = newFakeClient().build();
    backend = newBackend().withEmbeddedServer().build(fakeClient);

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/token"))
      .GET().build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  void it_should_reject_incoming_user_token_with_wrong_body() throws IOException, InterruptedException {
    var fakeClient = newFakeClient().build();
    backend = newBackend().withEmbeddedServer().build(fakeClient);

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/token"))
      .header("Content-Type", "application/json; charset=utf-8")
      .POST(HttpRequest.BodyPublishers.ofString("{\"token\":")).build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  void it_should_fail_to_validate_connection_if_host_not_found() throws InterruptedException, ExecutionException {
    var fakeClient = newFakeClient().build();
    backend = newBackend().build(fakeClient);

    var connectionResponse = backend.getConnectionService()
      .validateConnection(new ValidateConnectionParams(new TransientSonarQubeConnectionDto("http://notexists", Either.forRight(new UsernamePasswordDto("foo", "bar"))))).get();

    assertThat(connectionResponse.isSuccess()).isFalse();
    assertThat(connectionResponse.getMessage()).contains("notexists");
  }

  @Test
  void it_should_support_notifications_if_sonarcloud() throws ExecutionException, InterruptedException {
    var fakeClient = newFakeClient().build();
    backend = newBackend().build(fakeClient);

    var connectionResponse = backend.getConnectionService()
      .checkSmartNotificationsSupported(new CheckSmartNotificationsSupportedParams(
        new TransientSonarCloudConnectionDto("https://sonarcloud.io", Either.forLeft(new TokenDto("foo")))))
      .get();

    assertThat(connectionResponse.isSuccess()).isTrue();
  }

  @Test
  void it_should_support_notifications_if_sonarqube_supports() throws ExecutionException, InterruptedException {
    var fakeClient = newFakeClient().build();
    server = newSonarQubeServer().withSmartNotificationsSupported(true).start();
    backend = newBackend().withEmbeddedServer().withSonarQubeConnection("connectionId", server).build(fakeClient);

    var connectionResponse = backend.getConnectionService()
      .checkSmartNotificationsSupported(new CheckSmartNotificationsSupportedParams(
        new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto("foo")))))
      .get();

    assertThat(connectionResponse.isSuccess()).isTrue();
  }

  @Test
  void it_should_not_support_notifications_if_sonarqube_does_not_support() throws ExecutionException, InterruptedException {
    var fakeClient = newFakeClient().build();
    server = newSonarQubeServer().withSmartNotificationsSupported(false).start();
    backend = newBackend().withEmbeddedServer().withSonarQubeConnection("connectionId", server).build(fakeClient);

    var connectionResponse = backend.getConnectionService()
      .checkSmartNotificationsSupported(new CheckSmartNotificationsSupportedParams(
        new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto("foo")))))
      .get();

    assertThat(connectionResponse.isSuccess()).isFalse();
  }

  private SonarLintTestRpcServer backend;
  private ServerFixture.Server server;
}
