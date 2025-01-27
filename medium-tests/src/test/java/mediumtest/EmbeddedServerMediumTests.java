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
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.eclipse.jetty.http.HttpStatus;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

class EmbeddedServerMediumTests {

  @SonarLintTest
  void it_should_return_the_ide_name_and_empty_description_if_the_origin_is_not_trusted(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend().withEmbeddedServer().withClientName("ClientName").start(fakeClient);

    var embeddedServerPort = backend.getEmbeddedServerPort();
    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + embeddedServerPort + "/sonarlint/api/status"))
      .header("Origin", "https://untrusted")
      .GET().build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response)
      .extracting(HttpResponse::statusCode, HttpResponse::body)
      .containsExactly(HttpStatus.OK_200, "{\"ideName\":\"ClientName\",\"description\":\"\",\"needsToken\":true,\"capabilities\":{\"canOpenFixSuggestion\":false}}");
    assertCspResponseHeader(response, embeddedServerPort);
  }

  @SonarLintTest
  void it_should_not_trust_origin_having_known_connection_prefix(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    when(fakeClient.getClientLiveDescription()).thenReturn("WorkspaceTitle");

    var backend = harness.newBackend().withEmbeddedServer().withClientName("ClientName").withSonarQubeConnection("connectionId", "https://sonar.my").start(fakeClient);

    var embeddedServerPort = backend.getEmbeddedServerPort();
    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + embeddedServerPort + "/sonarlint/api/status"))
      .header("Origin", "https://sonar")
      .GET().build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response)
      .extracting(HttpResponse::statusCode, HttpResponse::body)
      .containsExactly(HttpStatus.OK_200, "{\"ideName\":\"ClientName\",\"description\":\"\",\"needsToken\":true,\"capabilities\":{\"canOpenFixSuggestion\":false}}");
    assertCspResponseHeader(response, embeddedServerPort);
  }

  @SonarLintTest
  void it_should_return_the_ide_name_and_full_description_if_the_origin_is_trusted(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    when(fakeClient.getClientLiveDescription()).thenReturn("WorkspaceTitle");

    var backend = harness.newBackend().withEmbeddedServer().withClientName("ClientName").withSonarQubeConnection("connectionId", "https://sonar.my").start(fakeClient);

    var embeddedServerPort = backend.getEmbeddedServerPort();
    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + embeddedServerPort + "/sonarlint/api/status"))
      .header("Origin", "https://sonar.my")
      .GET().build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response)
      .extracting(HttpResponse::statusCode, HttpResponse::body)
      .containsExactly(HttpStatus.OK_200, "{\"ideName\":\"ClientName\",\"description\":\"WorkspaceTitle\",\"needsToken\":false,\"capabilities\":{\"canOpenFixSuggestion\":false}}");

    assertCspResponseHeader(response, embeddedServerPort);
  }

  private void assertCspResponseHeader(HttpResponse<String> response, int embeddedServerPort) {
    assertThat(response.headers().map().get("Content-Security-Policy-Report-Only"))
      .contains("connect-src 'self' http://localhost:" + embeddedServerPort + ";");
  }

  @SonarLintTest
  void it_should_set_preflight_response_accordingly_when_receiving_preflight_request(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    when(fakeClient.getClientLiveDescription()).thenReturn("WorkspaceTitle");

    var backend = harness.newBackend().withEmbeddedServer().withClientName("ClientName").withSonarQubeConnection("connectionId", "http://sonar.my").start(fakeClient);

    var request = HttpRequest.newBuilder()
      .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/token"))
      .header("Origin", "http://sonar.my")
      .build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.headers().map())
      .extracting("access-control-allow-methods", "access-control-allow-origin", "access-control-allow-private-network")
      .containsExactly(List.of("GET, POST, OPTIONS"), List.of("http://sonar.my"), List.of("true"));
    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK_200);
    assertThat(response.headers().map()).doesNotContainKey("Content-Security-Policy-Report-Only");
  }

  @SonarLintTest
  void it_should_receive_bad_request_response_if_not_right_method(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    when(fakeClient.getClientLiveDescription()).thenReturn("WorkspaceTitle");

    var backend = harness.newBackend().withEmbeddedServer().withClientName("ClientName").withSonarQubeConnection("connectionId", "https://sonar.my").start(fakeClient);

    var embeddedServerPort = backend.getEmbeddedServerPort();
    var requestToken = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + embeddedServerPort + "/sonarlint/api/token"))
      .header("Origin", "https://sonar.my")
      .GET().build();
    var requestStatus = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + embeddedServerPort + "/sonarlint/api/status"))
      .header("Origin", "https://sonar.my")
      .DELETE().build();
    var responseToken = java.net.http.HttpClient.newHttpClient().send(requestToken, HttpResponse.BodyHandlers.ofString());
    var responseStatus = java.net.http.HttpClient.newHttpClient().send(requestStatus, HttpResponse.BodyHandlers.ofString());

    assertThat(responseToken.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST_400);
    assertThat(responseStatus.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST_400);
    assertThat(responseToken.headers().map()).doesNotContainKey("Content-Security-Policy-Report-Only");
    assertThat(responseStatus.headers().map()).doesNotContainKey("Content-Security-Policy-Report-Only");
  }

  @SonarLintTest
  void it_should_rate_limit_origin_if_too_many_requests(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend().withEmbeddedServer().withClientName("ClientName").start(fakeClient);

    var embeddedServerPort = backend.getEmbeddedServerPort();
    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + embeddedServerPort + "/sonarlint/api/status"))
      .header("Origin", "https://sonar")
      .GET().build();
    for (int i = 0; i < 10; i++) {
      java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response)
      .extracting(HttpResponse::statusCode, HttpResponse::body)
      .containsExactly(HttpStatus.TOO_MANY_REQUESTS_429, "");
  }

  @SonarLintTest
  void it_should_not_allow_request_if_origin_is_missing(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend().withEmbeddedServer().withClientName("ClientName").start(fakeClient);

    var embeddedServerPort = backend.getEmbeddedServerPort();
    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + embeddedServerPort + "/sonarlint/api/status"))
      .GET().build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response)
      .extracting(HttpResponse::statusCode, HttpResponse::body)
      .containsExactly(HttpStatus.BAD_REQUEST_400, "");
  }

  @SonarLintTest
  void it_should_not_rate_limit_over_time(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend().withEmbeddedServer().withClientName("ClientName").start(fakeClient);

    var embeddedServerPort = backend.getEmbeddedServerPort();
    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + embeddedServerPort + "/sonarlint/api/status"))
      .header("Origin", "https://sonar")
      .GET().build();
    for (int i = 0; i < 10; i++) {
      java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(HttpStatus.OK_200);
    });
  }

}
