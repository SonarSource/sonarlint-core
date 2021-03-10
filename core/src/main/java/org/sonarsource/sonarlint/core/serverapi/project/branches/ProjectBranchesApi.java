/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.project.branches;

import java.util.List;
import java.util.stream.Collectors;
import org.sonar.api.utils.DateUtils;
import org.sonarqube.ws.ProjectBranches;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class ProjectBranchesApi {
  private final ServerApiHelper helper;

  public ProjectBranchesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public List<ServerProjectBranch> list(String projectKey) {
    return helper.fetch(
      "api/project_branches/list.protobuf?project=" + StringUtils.urlEncode(projectKey),
      ProjectBranches.ListWsResponse::parseFrom,
      ProjectBranchesApi::adapt
    );
  }

  private static List<ServerProjectBranch> adapt(ProjectBranches.ListWsResponse response) {
    return response.getBranchesList().stream().map(ProjectBranchesApi::adapt).collect(Collectors.toList());
  }

  private static ServerProjectBranch adapt(ProjectBranches.Branch branch) {
    return new ServerProjectBranch(branch.getName(), branch.getIsMain(), DateUtils.parseOffsetDateTime(branch.getAnalysisDate()));
  }
}
