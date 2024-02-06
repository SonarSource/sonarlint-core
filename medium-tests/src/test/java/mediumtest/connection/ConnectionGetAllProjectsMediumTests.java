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

import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.ServerFixture;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.FuzzySearchProjectsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;
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

class ConnectionGetAllProjectsMediumTests {
  private SonarLintRpcServer backend;
  private String oldSonarCloudUrl;
  private ServerFixture.Server server;

  @BeforeEach
  void prepare() {
    oldSonarCloudUrl = System.getProperty("sonarlint.internal.sonarcloud.url");
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
    if (server != null) {
      server.shutdown();
    }
    if (oldSonarCloudUrl == null) {
      System.clearProperty("sonarlint.internal.sonarcloud.url");
    } else {
      System.setProperty("sonarlint.internal.sonarcloud.url", oldSonarCloudUrl);
    }
  }

  @Test
  void it_should_return_an_empty_response_if_no_projects_in_sonarqube() {
    server = newSonarQubeServer().start();
    backend = newBackend().build();

    var response = getAllProjects(new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto(null))));

    assertThat(response.getSonarProjects()).isEmpty();
  }

  @Test
  void it_should_return_an_empty_response_if_no_projects_in_sonarcloud_organization() {
    server = newSonarCloudServer("myOrg").start();
    System.setProperty("sonarlint.internal.sonarcloud.url", server.baseUrl());
    backend = newBackend().build();

    var response = getAllProjects(new TransientSonarCloudConnectionDto("myOrg", Either.forLeft(new TokenDto("token"))));

    assertThat(response.getSonarProjects()).isEmpty();
  }

  @Test
  void it_should_return_the_list_of_projects_on_sonarqube() {
    server = newSonarQubeServer()
      .withProject("projectKey1", project -> project.withName("MyProject1"))
      .withProject("projectKey2", project -> project.withName("MyProject2"))
      .start();
    backend = newBackend().build();

    var response = getAllProjects(new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto("token"))));

    assertThat(response.getSonarProjects())
      .extracting(SonarProjectDto::getKey, SonarProjectDto::getName)
      .containsOnly(tuple("projectKey1", "MyProject1"), tuple("projectKey2", "MyProject2"));
  }

  @Test
  void it_should_fuzzy_search_for_projects_on_sonarqube() {
    server = newSonarQubeServer()
      .withProject("mycompany:project-foo1", project -> project.withName("My Company Project Foo 1"))
      .withProject("mycompany:project-foo2", project -> project.withName("My Company Project Foo 2"))
      .withProject("mycompany:project-bar", project -> project.withName("My Company Project Bar"))
      .start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl())
      .build();

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

  @Test
  void it_should_return_the_list_of_projects_on_sonarcloud() {
    server = newSonarCloudServer("myOrg")
      .withProject("projectKey1", project -> project.withName("MyProject1"))
      .withProject("projectKey2", project -> project.withName("MyProject2"))
      .start();
    System.setProperty("sonarlint.internal.sonarcloud.url", server.baseUrl());
    backend = newBackend().build();

    var response = getAllProjects(new TransientSonarCloudConnectionDto("myOrg", Either.forLeft(new TokenDto("token"))));

    assertThat(response.getSonarProjects())
      .extracting(SonarProjectDto::getKey, SonarProjectDto::getName)
      .containsOnly(tuple("projectKey1", "MyProject1"), tuple("projectKey2", "MyProject2"));
  }

  @Test
  void it_should_support_cancellation() throws InterruptedException {
    server = newSonarQubeServer().start();
    server.getMockServer().stubFor(get("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1").willReturn(aResponse()
      .withStatus(200)
      .withFixedDelay(2000)));
    var client = newFakeClient().build();
    backend = newBackend().build(client);

    var connectionDto = new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto(null)));

    var future = backend.getConnectionService().getAllProjects(new GetAllProjectsParams(connectionDto));
    await().untilAsserted(() -> server.getMockServer().verify(getRequestedFor(urlEqualTo("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1"))));

    future.cancel(true);

    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Request cancelled"));
  }

  private GetAllProjectsResponse getAllProjects(TransientSonarQubeConnectionDto connectionDto) {
    return backend.getConnectionService().getAllProjects(new GetAllProjectsParams(connectionDto)).join();
  }

  private GetAllProjectsResponse getAllProjects(TransientSonarCloudConnectionDto connectionDto) {
    return backend.getConnectionService().getAllProjects(new GetAllProjectsParams(connectionDto)).join();
  }

}
