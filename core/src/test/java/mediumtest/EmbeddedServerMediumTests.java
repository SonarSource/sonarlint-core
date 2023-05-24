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
import java.util.concurrent.ExecutionException;
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
    var fakeClient = newFakeClient().withHostName("ClientName").build();
    backend = newBackend().withEmbeddedServer().build(fakeClient);

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/status"))
      .GET().build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response)
      .extracting(HttpResponse::statusCode, HttpResponse::body)
      .containsExactly(200, "{\"ideName\":\"ClientName\",\"description\":\"\"}");
  }

  @Test
  void it_should_not_trust_origin_having_known_connection_prefix() throws IOException, InterruptedException {
    var fakeClient = newFakeClient().withHostName("ClientName").withHostDescription("WorkspaceTitle").build();
    backend = newBackend().withEmbeddedServer().withSonarQubeConnection("connectionId", "https://sonar.my").build(fakeClient);

    var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/status"))
            .header("Origin", "https://sonar")
            .GET().build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response)
            .extracting(HttpResponse::statusCode, HttpResponse::body)
            .containsExactly(200, "{\"ideName\":\"ClientName\",\"description\":\"\"}");
  }

  @Test
  void it_should_return_the_ide_name_and_full_description_if_the_origin_is_trusted() throws IOException, InterruptedException {
    var fakeClient = newFakeClient().withHostName("ClientName").withHostDescription("WorkspaceTitle").build();
    backend = newBackend().withEmbeddedServer().withSonarQubeConnection("connectionId", "https://sonar.my").build(fakeClient);

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/status"))
      .header("Origin", "https://sonar.my")
      .GET().build();
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response)
      .extracting(HttpResponse::statusCode, HttpResponse::body)
      .containsExactly(200, "{\"ideName\":\"ClientName\",\"description\":\"WorkspaceTitle\"}");
  }

  private SonarLintBackendImpl backend;
}
