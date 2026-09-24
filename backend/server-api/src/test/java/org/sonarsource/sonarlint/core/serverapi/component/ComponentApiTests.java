/*
 * SonarLint Core - Server API
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.serverapi.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import mockwebserver3.MockResponse;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.exception.ServerErrorException;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class ComponentApiTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private static final String PROJECT_KEY = "project1";

  private ComponentApi underTest;

  @BeforeEach
  void setUp() {
    underTest = new ComponentApi(mockServer.serverApiHelper());
  }

  @Test
  void should_return_empty_when_no_components_returned() {
    mockServer.addStringResponse("/api/components/search_projects?projectIds=project%3Akey",
      "{\"components\":[]}");

    var result = underTest.searchProjects("project:key", new SonarLintCancelMonitor());

    assertThat(result).isNull();
  }

  @Test
  void should_return_empty_when_response_is_invalid_json() {
    mockServer.addStringResponse("/api/components/search_projects?projectIds=project%3Akey",
      "invalid json");

    var result = underTest.searchProjects("project:key", new SonarLintCancelMonitor());

    assertThat(result).isNull();
  }

  @Test
  void should_get_project_key_by_project_id() {
    var projectId = "project:key";
    var encodedProjectId = "project%3Akey";
    var organization = "my-org";
    underTest = new ComponentApi(mockServer.serverApiHelper(organization));

    mockServer.addStringResponse("/api/components/search_projects?projectIds=" + encodedProjectId + "&organization=" + organization,
      "{\"components\":[{\"key\":\"projectKey\",\"name\":\"projectName\"}]}\n");

    var result = underTest.searchProjects(projectId, new SonarLintCancelMonitor());

    assertThat(result.projectKey()).isEqualTo("projectKey");
    assertThat(result.projectName()).isEqualTo("projectName");
  }

  @Test
  void should_return_empty_if_project_not_found() {
    var result = underTest.searchProjects("project:key", new SonarLintCancelMonitor());

    assertThat(result).isNull();
  }

  @Test
  void should_get_files() {
    mockServer.addResponseFromResource("/api/components/tree.protobuf?qualifiers=FIL,UTS&component=project1&ps=500&p=1", "/update/component_tree.pb");

    var files = underTest.getAllFileKeys(PROJECT_KEY, new SonarLintCancelMonitor());

    assertThat(files).hasSize(187);
    assertThat(files.get(0)).isEqualTo("org.sonarsource.sonarlint.intellij:sonarlint-intellij:src/main/java/org/sonarlint/intellij/ui/AbstractIssuesPanel.java");
  }

  @Test
  void should_get_files_with_organization() {
    underTest = new ComponentApi(mockServer.serverApiHelper("myorg"));
    mockServer.addResponseFromResource("/api/components/tree.protobuf?qualifiers=FIL,UTS&component=project1&organization=myorg&ps=500&p=1", "/update/component_tree.pb");

    var files = underTest.getAllFileKeys(PROJECT_KEY, new SonarLintCancelMonitor());

    assertThat(files).hasSize(187);
    assertThat(files.get(0)).isEqualTo("org.sonarsource.sonarlint.intellij:sonarlint-intellij:src/main/java/org/sonarlint/intellij/ui/AbstractIssuesPanel.java");
  }

  @Test
  void should_get_empty_files_if_tree_is_empty() {
    mockServer.addResponseFromResource("/api/components/tree.protobuf?qualifiers=FIL,UTS&component=project1&ps=500&p=1", "/update/empty_component_tree.pb");

    var files = underTest.getAllFileKeys(PROJECT_KEY, new SonarLintCancelMonitor());

    assertThat(files).isEmpty();
  }

  @Test
  void should_search_projects_by_name_or_key_with_one_bounded_server_request() {
    mockServer.addProtobufResponse("/api/components/search.protobuf?qualifiers=TRK&q=cr%C3%A8me+br%C3%BBl%C3%A9e&p=1&ps=10", Components.SearchWsResponse.newBuilder()
      .addComponents(Components.Component.newBuilder().setKey("second").setName("Second").build())
      .addComponents(Components.Component.newBuilder().setKey("first").setName("First").build())
      .build());

    var projects = underTest.searchProjectsByNameOrKey("crème brûlée", new SonarLintCancelMonitor());

    assertThat(projects).extracting("key", "name")
      .containsExactly(tuple("second", "Second"), tuple("first", "First"));
  }

  @Test
  void should_search_projects_by_name_or_key_in_sonarcloud_organization() {
    underTest = new ComponentApi(mockServer.serverApiHelper("org:key"));
    mockServer.addProtobufResponse("/api/components/search.protobuf?qualifiers=TRK&organization=org%3Akey&q=project&p=1&ps=10",
      Components.SearchWsResponse.newBuilder().build());

    assertThat(underTest.searchProjectsByNameOrKey("project", new SonarLintCancelMonitor())).isEmpty();
  }

  @Test
  void should_propagate_search_failures() {
    mockServer.addResponse("/api/components/search.protobuf?qualifiers=TRK&q=project&p=1&ps=10", new MockResponse.Builder().code(500).build());

    assertThatThrownBy(() -> underTest.searchProjectsByNameOrKey("project", new SonarLintCancelMonitor()))
      .isInstanceOf(ServerErrorException.class);
  }

  @Test
  void should_get_all_projects() {
    mockServer.addProtobufResponse("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1", Components.SearchWsResponse.newBuilder()
      .addComponents(Components.Component.newBuilder().setKey("projectKey").setName("projectName").build()).build());
    mockServer.addProtobufResponse("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=2", Components.SearchWsResponse.newBuilder().build());

    var projects = underTest.getAllProjects(new SonarLintCancelMonitor());

    assertThat(projects)
      .extracting("key", "name")
      .containsOnly(tuple("projectKey", "projectName"));
  }

  @Test
  void should_get_all_projects_with_organization() {
    mockServer.addProtobufResponse("/api/components/search.protobuf?qualifiers=TRK&organization=org%3Akey&ps=500&p=1", Components.SearchWsResponse.newBuilder()
      .addComponents(Components.Component.newBuilder().setKey("projectKey").setName("projectName").build()).build());
    mockServer.addProtobufResponse("/api/components/search.protobuf?qualifiers=TRK&organization=org%3Akey&ps=500&p=2", Components.SearchWsResponse.newBuilder().build());
    var componentApi = new ComponentApi(mockServer.serverApiHelper("org:key"));

    var projects = componentApi.getAllProjects(new SonarLintCancelMonitor());

    assertThat(projects)
      .extracting("key", "name")
      .containsOnly(tuple("projectKey", "projectName"));
  }

  @Test
  void should_get_project_details() {
    mockServer.addProtobufResponse("/api/components/show.protobuf?component=project%3Akey", Components.ShowWsResponse.newBuilder()
      .setComponent(Components.Component.newBuilder().setKey("projectKey").setName("projectName").build()).build());

    var project = underTest.getProject("project:key", new SonarLintCancelMonitor());

    assertThat(project).hasValueSatisfying(p -> {
      assertThat(p.key()).isEqualTo("projectKey");
      assertThat(p.name()).isEqualTo("projectName");
    });
  }

  @Test
  void should_get_project_by_exact_key() {
    mockServer.addStringResponse("/api/components/show?component=project%3Akey",
      "{\"component\":{\"key\":\"project:key\",\"name\":\"Project\",\"qualifier\":\"TRK\"}}");

    assertThat(underTest.getProjectByExactKey("project:key", new SonarLintCancelMonitor()))
      .hasValueSatisfying(project -> assertThat(project.name()).isEqualTo("Project"));
  }

  @Test
  void should_validate_exact_key_project_metadata() {
    mockServer.addStringResponse("/api/components/show?component=expected",
      "{\"component\":{\"key\":\"different\",\"name\":\"Project\",\"qualifier\":\"TRK\"}}");
    mockServer.addStringResponse("/api/components/show?component=file",
      "{\"component\":{\"key\":\"file\",\"name\":\"File\",\"qualifier\":\"FIL\"}}");

    assertThat(underTest.getProjectByExactKey("expected", new SonarLintCancelMonitor())).isEmpty();
    assertThat(underTest.getProjectByExactKey("file", new SonarLintCancelMonitor())).isEmpty();
  }

  @Test
  void should_validate_exact_key_project_organization_on_sonarcloud() {
    underTest = new ComponentApi(mockServer.serverApiHelper("expected-org"));
    mockServer.addStringResponse("/api/components/show?component=project",
      "{\"component\":{\"key\":\"project\",\"name\":\"Project\",\"qualifier\":\"TRK\",\"organization\":\"other-org\"}}");

    assertThat(underTest.getProjectByExactKey("project", new SonarLintCancelMonitor())).isEmpty();
  }

  @Test
  void should_treat_forbidden_and_not_found_exact_key_lookup_as_no_project() {
    mockServer.addResponse("/api/components/show?component=forbidden", new MockResponse.Builder().code(403).build());
    mockServer.addResponse("/api/components/show?component=missing", new MockResponse.Builder().code(404).build());

    assertThat(underTest.getProjectByExactKey("forbidden", new SonarLintCancelMonitor())).isEmpty();
    assertThat(underTest.getProjectByExactKey("missing", new SonarLintCancelMonitor())).isEmpty();
  }

  @Test
  void should_propagate_exact_key_lookup_failures() {
    mockServer.addResponse("/api/components/show?component=project", new MockResponse.Builder().code(500).build());

    assertThatThrownBy(() -> underTest.getProjectByExactKey("project", new SonarLintCancelMonitor()))
      .isInstanceOf(ServerErrorException.class);
  }

  @Test
  void should_get_empty_project_details_if_request_fails() {
    var project = underTest.getProject("project:key", new SonarLintCancelMonitor());

    assertThat(project).isEmpty();
  }

  @Test
  void should_get_ancestor_key() {
    mockServer.addProtobufResponse("/api/components/show.protobuf?component=project%3Akey", Components.ShowWsResponse.newBuilder()
      .addAncestors(Components.Component.newBuilder().setKey("ancestorKey").build()).build());

    var project = underTest.fetchFirstAncestorKey("project:key", new SonarLintCancelMonitor());

    assertThat(project).contains("ancestorKey");
  }
}
