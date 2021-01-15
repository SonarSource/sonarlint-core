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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.nio.file.Path;
import org.sonarqube.ws.Components;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectList;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectList.Project.Builder;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class ProjectListDownloader {

  private static final String PROJECT_SEARCH_URL = "api/components/search.protobuf?qualifiers=TRK";
  private final SonarLintWsClient wsClient;

  public ProjectListDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public void fetchTo(Path dest, ProgressWrapper progress) {
    ProjectList.Builder projectListBuilder = ProjectList.newBuilder();
    Builder projectBuilder = ProjectList.Project.newBuilder();

    StringBuilder searchUrl = new StringBuilder();
    searchUrl.append(PROJECT_SEARCH_URL);
    wsClient.getOrganizationKey()
      .ifPresent(org -> searchUrl.append("&organization=").append(StringUtils.urlEncode(org)));
    wsClient.getPaginated(searchUrl.toString(),
      Components.SearchWsResponse::parseFrom,
      Components.SearchWsResponse::getPaging,
      Components.SearchWsResponse::getComponentsList,
      project -> {
        projectBuilder.clear();
        projectListBuilder.putProjectsByKey(project.getKey(), projectBuilder
          .setKey(project.getKey())
          .setName(project.getName())
          .build());
      },
      true,
      progress);

    ProtobufUtil.writeToFile(projectListBuilder.build(), dest.resolve(StoragePaths.PROJECT_LIST_PB));
  }

}
