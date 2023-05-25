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
package mediumtest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import mediumtest.fixtures.ServerFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.backend.authentication.HelpGenerateUserTokenParams;
import org.sonarsource.sonarlint.core.clientapi.backend.authentication.HelpGenerateUserTokenResponse;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class AuthenticationHelperMediumTests {

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (server != null) {
      server.shutdown();
    }
  }

  @Test
  void it_should_open_the_security_url_for_sonarcloud() {
    var fakeClient = newFakeClient().build();
    backend = newBackend().build(fakeClient);

    var futureResponse = backend.getAuthenticationHelperService().helpGenerateUserToken(new HelpGenerateUserTokenParams("https://sonarcloud.io", true));

    assertThat(futureResponse)
      .succeedsWithin(Duration.ofSeconds(3))
      .extracting(HelpGenerateUserTokenResponse::getToken)
      .isNull();
    assertThat(fakeClient.getUrlsToOpen()).containsExactly("https://sonarcloud.io/account/security");
  }

  @Test
  void it_should_open_the_security_url_for_sonarqube_older_than_9_7() {
    var fakeClient = newFakeClient().build();
    backend = newBackend().build(fakeClient);
    server = newSonarQubeServer("9.6").start();

    var futureResponse = backend.getAuthenticationHelperService().helpGenerateUserToken(new HelpGenerateUserTokenParams(server.baseUrl(), false));

    assertThat(futureResponse)
      .succeedsWithin(Duration.ofSeconds(3))
      .extracting(HelpGenerateUserTokenResponse::getToken)
      .isNull();
    assertThat(fakeClient.getUrlsToOpen()).containsExactly(server.url("account/security"));
  }

  @Test
  void it_should_open_the_sonarlint_auth_url_for_sonarqube_9_7_plus() throws IOException, InterruptedException {
    var fakeClient = newFakeClient().withHostName("ClientName").build();
    server = newSonarQubeServer("9.7").start();
    backend = newBackend().withEmbeddedServer().withSonarQubeConnection("connectionId", server).build(fakeClient);

    var futureResponse = backend.getAuthenticationHelperService().helpGenerateUserToken(new HelpGenerateUserTokenParams(server.baseUrl(), false));

    await().atMost(Duration.ofSeconds(3)).until(() -> !fakeClient.getUrlsToOpen().isEmpty());
    assertThat(fakeClient.getUrlsToOpen())
      .containsExactly(server.url("/sonarlint/auth?ideName=ClientName&port=" + backend.getEmbeddedServerPort()));

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
    var fakeClient = newFakeClient().withHostName("ClientName").build();
    server = newSonarQubeServer("9.7").start();
    backend = newBackend().withEmbeddedServer().withSonarQubeConnection("connectionId", server).build(fakeClient);

    backend.getAuthenticationHelperService().helpGenerateUserToken(new HelpGenerateUserTokenParams(server.baseUrl(), false));

    await().atMost(Duration.ofSeconds(3)).until(() -> !fakeClient.getUrlsToOpen().isEmpty());
    assertThat(fakeClient.getUrlsToOpen())
            .containsExactly(server.url("/sonarlint/auth?ideName=ClientName&port=" + backend.getEmbeddedServerPort()));

    var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/token"))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString("{\"token\": \"value\"}")).build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  void it_should_reject_tokens_from_unexpected_origin() throws IOException, InterruptedException {
    var fakeClient = newFakeClient().withHostName("ClientName").build();
    server = newSonarQubeServer("9.7").start();
    backend = newBackend().withEmbeddedServer().withSonarQubeConnection("connectionId", server).build(fakeClient);

    backend.getAuthenticationHelperService().helpGenerateUserToken(new HelpGenerateUserTokenParams(server.baseUrl(), false));

    await().atMost(Duration.ofSeconds(3)).until(() -> !fakeClient.getUrlsToOpen().isEmpty());
    assertThat(fakeClient.getUrlsToOpen())
            .containsExactly(server.url("/sonarlint/auth?ideName=ClientName&port=" + backend.getEmbeddedServerPort()));

    var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/token"))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Origin", "https://unexpected.sonar")
            .POST(HttpRequest.BodyPublishers.ofString("{\"token\": \"value\"}")).build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  void it_should_open_the_sonarlint_auth_url_without_port_for_sonarqube_9_7_plus_when_server_is_not_started() {
    var fakeClient = newFakeClient().withHostName("ClientName").build();
    backend = newBackend().build(fakeClient);
    server = newSonarQubeServer("9.7").start();

    var futureResponse = backend.getAuthenticationHelperService().helpGenerateUserToken(new HelpGenerateUserTokenParams(server.baseUrl(), false));

    assertThat(futureResponse)
      .succeedsWithin(Duration.ofSeconds(3))
      .extracting(HelpGenerateUserTokenResponse::getToken)
      .isNull();
    assertThat(fakeClient.getUrlsToOpen()).containsExactly(server.url("/sonarlint/auth?ideName=ClientName"));
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

  private SonarLintBackendImpl backend;
  private ServerFixture.Server server;
}
