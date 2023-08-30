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
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedServerMediumTests {

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Test
  void it_should_return_the_ide_name_and_empty_description_if_the_origin_is_not_trusted() throws IOException, InterruptedException {
    var fakeClient = newFakeClient().build();
    backend = newBackend().withEmbeddedServer().withClientName("ClientName").build(fakeClient);

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/status"))
      .GET().build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response)
      .extracting(HttpResponse::statusCode, HttpResponse::body)
      .containsExactly(HttpStatus.OK_200, "{\"ideName\":\"ClientName\",\"description\":\"\"}");
  }

  @Test
  void it_should_not_trust_origin_having_known_connection_prefix() throws IOException, InterruptedException {
    var fakeClient = newFakeClient().withClientDescription("WorkspaceTitle").build();
    backend = newBackend().withEmbeddedServer().withClientName("ClientName").withSonarQubeConnection("connectionId", "https://sonar.my").build(fakeClient);

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/status"))
      .header("Origin", "https://sonar")
      .GET().build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response)
      .extracting(HttpResponse::statusCode, HttpResponse::body)
      .containsExactly(HttpStatus.OK_200, "{\"ideName\":\"ClientName\",\"description\":\"\"}");
  }

  @Test
  void it_should_return_the_ide_name_and_full_description_if_the_origin_is_trusted() throws IOException, InterruptedException {
    var fakeClient = newFakeClient().withClientDescription("WorkspaceTitle").build();
    backend = newBackend().withEmbeddedServer().withClientName("ClientName").withSonarQubeConnection("connectionId", "https://sonar.my").build(fakeClient);

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/status"))
      .header("Origin", "https://sonar.my")
      .GET().build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response)
      .extracting(HttpResponse::statusCode, HttpResponse::body)
      .containsExactly(HttpStatus.OK_200, "{\"ideName\":\"ClientName\",\"description\":\"WorkspaceTitle\"}");
  }

  @Test
  void it_should_set_preflight_response_accordingly_when_receiving_preflight_request() throws IOException, InterruptedException {
    var fakeClient = newFakeClient().withClientDescription("WorkspaceTitle").build();
    backend = newBackend().withEmbeddedServer().withClientName("ClientName").withSonarQubeConnection("connectionId", "http://sonar.my").build(fakeClient);

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
  }

  @Test
  void it_should_receive_bad_request_response_if_not_right_method() throws IOException, InterruptedException {
    var fakeClient = newFakeClient().withClientDescription("WorkspaceTitle").build();
    backend = newBackend().withEmbeddedServer().withClientName("ClientName").withSonarQubeConnection("connectionId", "https://sonar.my").build(fakeClient);

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/token"))
      .header("Origin", "https://sonar.my")
      .GET().build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST_400);
  }

  private SonarLintBackendImpl backend;
}
