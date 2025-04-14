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

import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.FuzzySearchProjectsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;
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

class ConnectionGetAllProjectsMediumTests {

  @SonarLintTest
  void it_should_return_an_empty_response_if_no_projects_in_sonarqube(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend().start();

    var response = getAllProjects(backend, new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto(null))));

    assertThat(response.getSonarProjects()).isEmpty();
  }

  @SonarLintTest
  void it_should_return_an_empty_response_if_no_projects_in_sonarcloud_organization(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("myOrg")
      .start();
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .start();

    var response = getAllProjects(backend, new TransientSonarCloudConnectionDto("myOrg", Either.forLeft(new TokenDto("token")), SonarCloudRegion.EU));

    assertThat(response.getSonarProjects()).isEmpty();
  }

  @SonarLintTest
  void it_should_return_the_list_of_projects_on_sonarqube(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey1", project -> project.withName("MyProject1"))
      .withProject("projectKey2", project -> project.withName("MyProject2"))
      .start();
    var backend = harness.newBackend().start();

    var response = getAllProjects(backend, new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto("token"))));

    assertThat(response.getSonarProjects())
      .extracting(SonarProjectDto::getKey, SonarProjectDto::getName)
      .containsOnly(tuple("projectKey1", "MyProject1"), tuple("projectKey2", "MyProject2"));
  }

  @SonarLintTest
  void it_should_fuzzy_search_for_projects_on_sonarqube(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withProject("mycompany:project-foo1", project -> project.withName("My Company Project Foo 1"))
      .withProject("mycompany:project-foo2", project -> project.withName("My Company Project Foo 2"))
      .withProject("mycompany:project-bar", project -> project.withName("My Company Project Bar"))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl())
      .start();

    var emptySearch = backend.getConnectionService().fuzzySearchProjects(new FuzzySearchProjectsParams("connectionId", "")).join();
    assertThat(emptySearch.getTopResults())
      .isEmpty();

    var searchMy = backend.getConnectionService().fuzzySearchProjects(new FuzzySearchProjectsParams("connectionId", "My")).join();
    assertThat(searchMy.getTopResults())
      .extracting(SonarProjectDto::getKey, SonarProjectDto::getName)
      .containsExactly(
        tuple("mycompany:project-bar", "My Company Project Bar"),
        tuple("mycompany:project-foo1", "My Company Project Foo 1"),
        tuple("mycompany:project-foo2", "My Company Project Foo 2"));

    var searchFooByName = backend.getConnectionService().fuzzySearchProjects(new FuzzySearchProjectsParams("connectionId", "Foo")).join();
    assertThat(searchFooByName.getTopResults())
      .extracting(SonarProjectDto::getKey, SonarProjectDto::getName)
      .containsExactly(
        tuple("mycompany:project-foo1", "My Company Project Foo 1"),
        tuple("mycompany:project-foo2", "My Company Project Foo 2"));

    var searchBarByKey = backend.getConnectionService().fuzzySearchProjects(new FuzzySearchProjectsParams("connectionId", "project-bar")).join();
    assertThat(searchBarByKey.getTopResults())
      .extracting(SonarProjectDto::getKey, SonarProjectDto::getName)
      .containsExactly(
        tuple("mycompany:project-bar", "My Company Project Bar"));
  }

  @SonarLintTest
  void it_should_return_the_list_of_projects_on_sonarcloud(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("myOrg", organization -> organization
        .withProject("projectKey1", project -> project.withName("MyProject1"))
        .withProject("projectKey2", project -> project.withName("MyProject2")))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .start();

    var response = getAllProjects(backend, new TransientSonarCloudConnectionDto("myOrg", Either.forLeft(new TokenDto("token")), SonarCloudRegion.EU));

    assertThat(response.getSonarProjects())
      .extracting(SonarProjectDto::getKey, SonarProjectDto::getName)
      .containsOnly(tuple("projectKey1", "MyProject1"), tuple("projectKey2", "MyProject2"));
  }

  @SonarLintTest
  void it_should_support_cancellation(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    server.getMockServer().stubFor(get("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1").willReturn(aResponse()
      .withStatus(200)
      .withFixedDelay(2000)));
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend().start(client);

    var connectionDto = new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto(null)));

    var future = backend.getConnectionService().getAllProjects(new GetAllProjectsParams(connectionDto));
    await().untilAsserted(() -> server.getMockServer().verify(getRequestedFor(urlEqualTo("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1"))));

    future.cancel(true);

    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Request cancelled"));
  }

  private GetAllProjectsResponse getAllProjects(SonarLintTestRpcServer backend, TransientSonarQubeConnectionDto connectionDto) {
    return backend.getConnectionService().getAllProjects(new GetAllProjectsParams(connectionDto)).join();
  }

  private GetAllProjectsResponse getAllProjects(SonarLintTestRpcServer backend, TransientSonarCloudConnectionDto connectionDto) {
    return backend.getConnectionService().getAllProjects(new GetAllProjectsParams(connectionDto)).join();
  }

}
