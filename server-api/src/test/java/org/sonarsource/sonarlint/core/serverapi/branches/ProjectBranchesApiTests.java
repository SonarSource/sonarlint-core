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
package org.sonarsource.sonarlint.core.serverapi.branches;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.BranchType;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.ProjectBranches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
    mockServer.addProtobufResponse("/api/project_branches/list.protobuf?project=" + PROJECT_KEY, ProjectBranches.ListWsResponse.newBuilder()
      .addBranches(ProjectBranches.Branch.newBuilder().setName("feature/foo").setIsMain(false).setType(BranchType.BRANCH))
      .addBranches(ProjectBranches.Branch.newBuilder().setName("master").setIsMain(true).setType(BranchType.BRANCH)).build());

    var branches = underTest.getAllBranches(PROJECT_KEY);

    assertThat(branches).extracting(ServerBranch::getName, ServerBranch::isMain).containsExactlyInAnyOrder(tuple("master", true), tuple("feature/foo", false));
  }

  @Test
  void shouldSkipShortLivingBranches() {
    var branchListResponseBuilder = ProjectBranches.ListWsResponse.newBuilder();
    branchListResponseBuilder.addBranches(ProjectBranches.Branch.newBuilder().setName("branch-1.x").setIsMain(false).setType(BranchType.BRANCH));
    branchListResponseBuilder.addBranches(ProjectBranches.Branch.newBuilder().setName("master").setIsMain(true).setType(BranchType.BRANCH));
    branchListResponseBuilder.addBranches(ProjectBranches.Branch.newBuilder().setName("feature/my-long-branch").setIsMain(false).setType(BranchType.LONG));
    branchListResponseBuilder.addBranches(ProjectBranches.Branch.newBuilder().setName("feature/my-short-branch").setIsMain(false).setType(BranchType.SHORT));

    mockServer.addProtobufResponse("/api/project_branches/list.protobuf?project=" + PROJECT_KEY, branchListResponseBuilder.build());

    var branches = underTest.getAllBranches(PROJECT_KEY);

    assertThat(branches).extracting(ServerBranch::getName, ServerBranch::isMain)
      .containsExactlyInAnyOrder(tuple("master", true), tuple("branch-1.x", false),
        tuple("feature/my-long-branch", false));
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
