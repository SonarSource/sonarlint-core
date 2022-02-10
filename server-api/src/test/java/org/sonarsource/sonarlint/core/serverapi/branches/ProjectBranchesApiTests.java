/*
 * SonarLint Server API
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.branches;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarqube.ws.ProjectBranches;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectBranchesApiTests {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private final static String PROJECT_KEY = "project1";

  private ProjectBranchesApi underTest;

  @BeforeEach
  void setUp() {
    underTest = new ProjectBranchesApi(mockServer.serverApiHelper());
  }

  @Test
  void shouldDownloadBranches() {
    mockServer.addProtobufResponse("/api/project_branches/list.protobuf?project=project1", ProjectBranches.ListWsResponse.newBuilder()
      .addBranches(ProjectBranches.Branch.newBuilder().setName("feature/foo").setIsMain(false))
      .addBranches(ProjectBranches.Branch.newBuilder().setName("master").setIsMain(true)).build());

    var branches = underTest.getAllBranches(PROJECT_KEY);

    assertThat(branches).hasSize(2);
  }

  @Test
  void shouldReturnEmptyListOnMalformedResponse() {
    mockServer.addStringResponse("/api/project_branches/list.protobuf?project=project1",
      "{\n" +
        "  \"branches\": [\n" +
        "    { }" +
        "  ]\n" +
        "}");

    var branches = underTest.getAllBranches(PROJECT_KEY);

    assertThat(branches).isEmpty();
  }

}
