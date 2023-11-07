/*
 * SonarLint Core - Medium Tests
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
package mediumtest.connection;

import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProject;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;

import static mediumtest.fixtures.ServerFixture.newSonarCloudServer;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ConnectionGetAllProjectsMediumTests {
  private SonarLintRpcServer backend;
  private String oldSonarCloudUrl;

  @BeforeEach
  void prepare() {
    oldSonarCloudUrl = System.getProperty("sonarlint.internal.sonarcloud.url");
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
    if (oldSonarCloudUrl == null) {
      System.clearProperty("sonarlint.internal.sonarcloud.url");
    } else {
      System.setProperty("sonarlint.internal.sonarcloud.url", oldSonarCloudUrl);
    }
  }

  @Test
  void it_should_return_an_empty_response_if_no_projects_in_sonarqube() {
    var server = newSonarQubeServer().start();
    backend = newBackend().build();

    var response = getAllProjects(new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto(null))));

    assertThat(response.getSonarProjects()).isEmpty();
  }

  @Test
  void it_should_return_an_empty_response_if_no_projects_in_sonarcloud_organization() {
    var server = newSonarCloudServer("myOrg").start();
    System.setProperty("sonarlint.internal.sonarcloud.url", server.baseUrl());
    backend = newBackend().build();

    var response = getAllProjects(new TransientSonarCloudConnectionDto("myOrg", Either.forLeft(new TokenDto("token"))));

    assertThat(response.getSonarProjects()).isEmpty();
  }

  @Test
  void it_should_return_the_list_of_projects_on_sonarqube() {
    var server = newSonarQubeServer()
      .withProject("projectKey1", project -> project.withName("MyProject1"))
      .withProject("projectKey2", project -> project.withName("MyProject2"))
      .start();
    backend = newBackend().build();

    var response = getAllProjects(new TransientSonarQubeConnectionDto(server.baseUrl(), Either.forLeft(new TokenDto("token"))));

    assertThat(response.getSonarProjects())
      .extracting(SonarProject::getKey, SonarProject::getName)
      .containsOnly(tuple("projectKey1", "MyProject1"), tuple("projectKey2", "MyProject2"));
  }

  @Test
  void it_should_return_the_list_of_projects_on_sonarcloud() {
    var server = newSonarCloudServer("myOrg")
      .withProject("projectKey1", project -> project.withName("MyProject1"))
      .withProject("projectKey2", project -> project.withName("MyProject2"))
      .start();
    System.setProperty("sonarlint.internal.sonarcloud.url", server.baseUrl());
    backend = newBackend().build();

    var response = getAllProjects(new TransientSonarCloudConnectionDto("myOrg", Either.forLeft(new TokenDto("token"))));

    assertThat(response.getSonarProjects())
      .extracting(SonarProject::getKey, SonarProject::getName)
      .containsOnly(tuple("projectKey1", "MyProject1"), tuple("projectKey2", "MyProject2"));
  }

  private GetAllProjectsResponse getAllProjects(TransientSonarQubeConnectionDto connectionDto) {
    try {
      return backend.getConnectionService().getAllProjects(new GetAllProjectsParams(connectionDto)).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private GetAllProjectsResponse getAllProjects(TransientSonarCloudConnectionDto connectionDto) {
    try {
      return backend.getConnectionService().getAllProjects(new GetAllProjectsParams(connectionDto)).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

}
