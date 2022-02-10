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

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarqube.ws.Common.BranchType;
import org.sonarqube.ws.ProjectBranches;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ProjectBranchesApiTests {

  // https://github.com/SonarSource/sonarqube/blob/87ca68d63f4afd37d74b2f454430dfde9e862c6a/sonar-ws/src/main/protobuf/ws-commons.proto#L129
  private static final int LONG_BRANCH_ENUM_VALUE = 1;
  private static final int SHORT_BRANCH_ENUM_VALUE = 2;

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
    // We need to use a DynamicMessage as the old BranchType.SHORT or LONG are not there anymore in the protobuf enum
    DynamicMessage.Builder branchListResponseBuilder = DynamicMessage.newBuilder(ProjectBranches.ListWsResponse.getDescriptor());
    FieldDescriptor branchesField = ProjectBranches.ListWsResponse.getDescriptor().findFieldByNumber(ProjectBranches.ListWsResponse.BRANCHES_FIELD_NUMBER);
    branchListResponseBuilder.addRepeatedField(branchesField, ProjectBranches.Branch.newBuilder().setName("branch-1.x").setIsMain(false).setType(BranchType.BRANCH).build());
    branchListResponseBuilder.addRepeatedField(branchesField, ProjectBranches.Branch.newBuilder().setName("master").setIsMain(true).setType(BranchType.BRANCH).build());

    FieldDescriptor typeField = ProjectBranches.Branch.getDescriptor().findFieldByNumber(ProjectBranches.Branch.TYPE_FIELD_NUMBER);

    DynamicMessage.Builder oldLongBranchbuilder = DynamicMessage.newBuilder(ProjectBranches.Branch.getDescriptor())
      .mergeFrom(ProjectBranches.Branch.newBuilder().setName("feature/my-long-branch").setIsMain(false).build());
    oldLongBranchbuilder.setField(typeField, BranchType.getDescriptor().findValueByNumberCreatingIfUnknown(LONG_BRANCH_ENUM_VALUE));
    branchListResponseBuilder.addRepeatedField(branchesField, oldLongBranchbuilder.build());

    DynamicMessage.Builder oldShortBranchbuilder = DynamicMessage.newBuilder(ProjectBranches.Branch.getDescriptor())
      .mergeFrom(ProjectBranches.Branch.newBuilder().setName("feature/my-short-branch").setIsMain(false).build());
    oldShortBranchbuilder.setField(typeField, BranchType.getDescriptor().findValueByNumberCreatingIfUnknown(SHORT_BRANCH_ENUM_VALUE));
    branchListResponseBuilder.addRepeatedField(branchesField, oldShortBranchbuilder.build());

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
