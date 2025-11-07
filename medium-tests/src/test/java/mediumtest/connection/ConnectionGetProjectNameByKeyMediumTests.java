/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetProjectNamesByKeyParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetProjectNamesByKeyResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

class ConnectionGetProjectNameByKeyMediumTests {

  @SonarLintTest
  void it_should_return_null_if_no_projects_in_sonarqube(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend().start();

    var response = getProjectNamesByKey(backend, new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto("token"))),
      List.of("myProject"));

    assertThat(response.getProjectNamesByKey().entrySet()).extracting(Map.Entry::getKey, Map.Entry::getValue)
      .containsExactlyInAnyOrder(tuple("myProject", null));
  }

  @SonarLintTest
  void it_should_return_null_if_no_projects_in_sonarcloud_organization(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarCloudServer().start();
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .start();

    var response = getProjectNamesByKey(backend, new TransientSonarCloudConnectionDto("myOrg", Either.forLeft(new TokenDto("token")), SonarCloudRegion.EU), List.of(
      "myProject"));

    assertThat(response.getProjectNamesByKey().entrySet()).extracting(Map.Entry::getKey, Map.Entry::getValue)
      .containsExactlyInAnyOrder(tuple("myProject", null));
  }

  @SonarLintTest
  void it_should_find_project_name_if_available_on_sonarqube(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withProject("project-foo1", project -> project.withName("My Company Project Foo 1"))
      .withProject("project-foo2", project -> project.withName("My Company Project Foo 2"))
      .withProject("project-foo3", project -> project.withName("My Company Project Foo 3"))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl())
      .start();

    var response = getProjectNamesByKey(backend, new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto("token"))),
      List.of("project-foo2", "project-foo3", "project-foo4"));

    assertThat(response.getProjectNamesByKey().entrySet()).extracting(Map.Entry::getKey, Map.Entry::getValue)
      .containsExactlyInAnyOrder(tuple("project-foo4", null), tuple("project-foo2", "My Company Project Foo 2"), tuple("project-foo3",
        "My Company Project Foo 3"));
  }

  @SonarLintTest
  void it_should_find_project_names_if_available_on_sonarcloud(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("myOrg", organization -> organization
        .withProject("projectKey1", project -> project.withName("MyProject1"))
        .withProject("projectKey2", project -> project.withName("MyProject2"))
        .withProject("projectKey3", project -> project.withName("MyProject3")))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .start();

    var response = getProjectNamesByKey(backend, new TransientSonarCloudConnectionDto("myOrg", Either.forLeft(new TokenDto("token")), SonarCloudRegion.EU),
      List.of("projectKey2", "projectKey3", "projectKey4"));

    assertThat(response.getProjectNamesByKey().entrySet()).extracting(Map.Entry::getKey, Map.Entry::getValue)
      .containsExactlyInAnyOrder(tuple("projectKey4", null), tuple("projectKey2", "MyProject2"), tuple("projectKey3", "MyProject3"));
  }

  @SonarLintTest
  void it_should_support_cancellation(SonarLintTestHarness harness) {
    var myProjectKey = "myProjectKey";
    var server = harness.newFakeSonarQubeServer().start();
    server.getMockServer().stubFor(get("/api/components/show.protobuf?component=" + myProjectKey).willReturn(aResponse()
      .withStatus(200)
      .withFixedDelay(2000)));
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend().start(client);

    var connectionDto = new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto(null)));

    var future = backend.getConnectionService().getProjectNamesByKey(new GetProjectNamesByKeyParams(connectionDto, List.of(myProjectKey)));
    await().untilAsserted(() -> server.getMockServer().verify(getRequestedFor(urlEqualTo("/api/components/show.protobuf?component=" + myProjectKey))));

    future.cancel(true);

    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Request cancelled"));
  }

  private GetProjectNamesByKeyResponse getProjectNamesByKey(SonarLintTestRpcServer backend, TransientSonarQubeConnectionDto connectionDto, List<String> projectKey) {
    return backend.getConnectionService().getProjectNamesByKey(new GetProjectNamesByKeyParams(connectionDto, projectKey)).join();
  }

  private GetProjectNamesByKeyResponse getProjectNamesByKey(SonarLintTestRpcServer backend, TransientSonarCloudConnectionDto connectionDto, List<String> projectKey) {
    return backend.getConnectionService().getProjectNamesByKey(new GetProjectNamesByKeyParams(connectionDto, projectKey)).join();
  }
}
