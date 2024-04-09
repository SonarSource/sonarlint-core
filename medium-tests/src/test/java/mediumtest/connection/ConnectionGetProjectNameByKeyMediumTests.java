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
package mediumtest.connection;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.ServerFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetProjectNamesByKeyParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetProjectNamesByKeyResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static mediumtest.fixtures.ServerFixture.newSonarCloudServer;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

class ConnectionGetProjectNameByKeyMediumTests {
  private SonarLintRpcServer backend;
  private ServerFixture.Server server;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
    if (server != null) {
      server.shutdown();
    }
  }

  @Test
  void it_should_return_null_if_no_projects_in_sonarqube() {
    server = newSonarQubeServer().start();
    backend = newBackend().build();

    var response = getProjectNamesByKey(new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto("token"))),
      List.of("myProject"));

    assertThat(response.getProjectNamesByKey().entrySet()).extracting(Map.Entry::getKey, Map.Entry::getValue)
      .containsExactlyInAnyOrder(tuple("myProject", null));
  }

  @Test
  void it_should_return_null_if_no_projects_in_sonarcloud_organization() {
    server = newSonarCloudServer("myOrg").start();
    backend = newBackend()
      .withSonarCloudUrl(server.baseUrl())
      .build();

    var response = getProjectNamesByKey(new TransientSonarCloudConnectionDto("myOrg", Either.forLeft(new TokenDto("token"))), List.of(
      "myProject"));

    assertThat(response.getProjectNamesByKey().entrySet()).extracting(Map.Entry::getKey, Map.Entry::getValue)
      .containsExactlyInAnyOrder(tuple("myProject", null));
  }

  @Test
  void it_should_find_project_name_if_available_on_sonarqube() {
    server = newSonarQubeServer()
      .withProject("project-foo1", project -> project.withName("My Company Project Foo 1"))
      .withProject("project-foo2", project -> project.withName("My Company Project Foo 2"))
      .withProject("project-foo3", project -> project.withName("My Company Project Foo 3"))
      .start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl())
      .build();

    var response = getProjectNamesByKey(new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto("token"))),
      List.of("project-foo2", "project-foo3", "project-foo4"));

    assertThat(response.getProjectNamesByKey().entrySet()).extracting(Map.Entry::getKey, Map.Entry::getValue)
      .containsExactlyInAnyOrder(tuple("project-foo4", null), tuple("project-foo2", "My Company Project Foo 2"), tuple("project-foo3",
        "My Company Project Foo 3"));
  }

  @Test
  void it_should_find_project_names_if_available_on_sonarcloud() {
    server = newSonarCloudServer("myOrg")
      .withProject("projectKey1", project -> project.withName("MyProject1"))
      .withProject("projectKey2", project -> project.withName("MyProject2"))
      .withProject("projectKey3", project -> project.withName("MyProject3"))
      .start();
    backend = newBackend()
      .withSonarCloudUrl(server.baseUrl())
      .build();

    var response = getProjectNamesByKey(new TransientSonarCloudConnectionDto("myOrg", Either.forLeft(new TokenDto("token"))),
      List.of("projectKey2", "projectKey3", "projectKey4"));

    assertThat(response.getProjectNamesByKey().entrySet()).extracting(Map.Entry::getKey, Map.Entry::getValue)
      .containsExactlyInAnyOrder(tuple("projectKey4", null), tuple("projectKey2", "MyProject2"), tuple("projectKey3", "MyProject3"));
  }

  @Test
  void it_should_support_cancellation() {
    var myProjectKey = "myProjectKey";
    server = newSonarQubeServer().start();
    server.getMockServer().stubFor(get("/api/components/show.protobuf?component=" + myProjectKey).willReturn(aResponse()
      .withStatus(200)
      .withFixedDelay(2000)));
    var client = newFakeClient().build();
    backend = newBackend().build(client);

    var connectionDto = new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto(null)));

    var future = backend.getConnectionService().getProjectNamesByKey(new GetProjectNamesByKeyParams(connectionDto, List.of(myProjectKey)));
    await().untilAsserted(() -> server.getMockServer().verify(getRequestedFor(urlEqualTo("/api/components/show.protobuf?component=" + myProjectKey))));

    future.cancel(true);

    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Request cancelled"));
  }


  private GetProjectNamesByKeyResponse getProjectNamesByKey(TransientSonarQubeConnectionDto connectionDto, List<String> projectKey) {
    return backend.getConnectionService().getProjectNamesByKey(new GetProjectNamesByKeyParams(connectionDto, projectKey)).join();
  }

  private GetProjectNamesByKeyResponse getProjectNamesByKey(TransientSonarCloudConnectionDto connectionDto, List<String> projectKey) {
    return backend.getConnectionService().getProjectNamesByKey(new GetProjectNamesByKeyParams(connectionDto, projectKey)).join();
  }
}
