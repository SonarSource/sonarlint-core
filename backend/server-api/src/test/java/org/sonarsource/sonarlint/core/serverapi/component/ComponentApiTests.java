/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class ComponentApiTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private final static String PROJECT_KEY = "project1";

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
