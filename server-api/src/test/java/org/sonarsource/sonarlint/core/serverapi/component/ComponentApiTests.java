/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

class ComponentApiTests {
  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private final static String PROJECT_KEY = "project1";

  private final ProgressMonitor progress = mock(ProgressMonitor.class);

  private ComponentApi underTest;

  @BeforeEach
  void setUp() {
    underTest = new ComponentApi(mockServer.serverApiHelper());
  }

  @Test
  void should_get_files() {
    mockServer.addResponseFromResource("/api/components/tree.protobuf?qualifiers=FIL,UTS&component=project1&ps=500&p=1", "/update/component_tree.pb");

    var files = underTest.getAllFileKeys(PROJECT_KEY, progress);

    assertThat(files).hasSize(187);
    assertThat(files.get(0)).isEqualTo("org.sonarsource.sonarlint.intellij:sonarlint-intellij:src/main/java/org/sonarlint/intellij/ui/AbstractIssuesPanel.java");
  }

  @Test
  void should_get_files_with_organization() {
    underTest = new ComponentApi(mockServer.serverApiHelper("myorg"));
    mockServer.addResponseFromResource("/api/components/tree.protobuf?qualifiers=FIL,UTS&component=project1&organization=myorg&ps=500&p=1", "/update/component_tree.pb");

    var files = underTest.getAllFileKeys(PROJECT_KEY, progress);

    assertThat(files).hasSize(187);
    assertThat(files.get(0)).isEqualTo("org.sonarsource.sonarlint.intellij:sonarlint-intellij:src/main/java/org/sonarlint/intellij/ui/AbstractIssuesPanel.java");
  }

  @Test
  void should_get_empty_files_if_tree_is_empty() {
    mockServer.addResponseFromResource("/api/components/tree.protobuf?qualifiers=FIL,UTS&component=project1&ps=500&p=1", "/update/empty_component_tree.pb");

    var files = underTest.getAllFileKeys(PROJECT_KEY, progress);

    assertThat(files).isEmpty();
  }

  @Test
  void should_get_all_projects() {
    mockServer.addProtobufResponse("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1", Components.SearchWsResponse.newBuilder()
      .addComponents(Components.Component.newBuilder().setKey("projectKey").setName("projectName").build()).build());
    mockServer.addProtobufResponse("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=2", Components.SearchWsResponse.newBuilder().build());

    var projects = underTest.getAllProjects(progress);

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

    var projects = componentApi.getAllProjects(progress);

    assertThat(projects)
      .extracting("key", "name")
      .containsOnly(tuple("projectKey", "projectName"));
  }

  @Test
  void should_get_project_details() {
    mockServer.addProtobufResponse("/api/components/show.protobuf?component=project%3Akey", Components.ShowWsResponse.newBuilder()
      .setComponent(Components.Component.newBuilder().setKey("projectKey").setName("projectName").build()).build());

    var project = underTest.getProject("project:key");

    assertThat(project).hasValueSatisfying(p -> {
      assertThat(p.getKey()).isEqualTo("projectKey");
      assertThat(p.getName()).isEqualTo("projectName");
    });
  }

  @Test
  void should_get_empty_project_details_if_request_fails() {
    var project = underTest.getProject("project:key");

    assertThat(project).isEmpty();
  }

  @Test
  void should_get_ancestor_key() {
    mockServer.addProtobufResponse("/api/components/show.protobuf?component=project%3Akey", Components.ShowWsResponse.newBuilder()
      .addAncestors(Components.Component.newBuilder().setKey("ancestorKey").build()).build());

    var project = underTest.fetchFirstAncestorKey("project:key");

    assertThat(project).contains("ancestorKey");
  }
}
