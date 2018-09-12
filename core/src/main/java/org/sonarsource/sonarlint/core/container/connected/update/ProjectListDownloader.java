/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import org.sonarqube.ws.WsComponents;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectList;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectList.Project.Builder;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;
import org.sonarsource.sonarlint.core.util.ws.WsResponse;

public class ProjectListDownloader {
  private final SonarLintWsClient wsClient;

  public ProjectListDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public void fetchModulesListTo(Path dest, String serverVersion, ProgressWrapper progress) {
    if (Version.create(serverVersion).compareToIgnoreQualifier(Version.create("6.3")) >= 0) {
      fetchModulesListAfter6dot3(dest, progress);
    } else {
      fetchModulesListBefore6dot3(dest);
    }
  }

  private void fetchModulesListAfter6dot3(Path dest, ProgressWrapper progress) {
    ProjectList.Builder projectListBuilder = ProjectList.newBuilder();
    Builder projectBuilder = ProjectList.Project.newBuilder();

    String baseUrl = "api/components/search.protobuf?qualifiers=TRK";
    if (wsClient.getOrganizationKey() != null) {
      baseUrl += "&organization=" + StringUtils.urlEncode(wsClient.getOrganizationKey());
    }
    SonarLintWsClient.getPaginated(wsClient, baseUrl,
      WsComponents.SearchWsResponse::parseFrom,
      WsComponents.SearchWsResponse::getPaging,
      WsComponents.SearchWsResponse::getComponentsList,
      project -> {
        projectBuilder.clear();
        projectListBuilder.putProjectsByKey(project.getKey(), projectBuilder
          .setProjectKey(project.getProject())
          .setKey(project.getKey())
          .setName(project.getName())
          .build());
      },
      true,
      progress);

    ProtobufUtil.writeToFile(projectListBuilder.build(), dest.resolve(StoragePaths.PROJECT_LIST_PB));
  }

  private void fetchModulesListBefore6dot3(Path dest) {
    try (WsResponse response = wsClient.get("api/projects/index?format=json")) {
      try (Reader contentReader = response.contentReader()) {
        DefaultModule[] results = new Gson().fromJson(contentReader, DefaultModule[].class);

        ProjectList.Builder projectListBuilder = ProjectList.newBuilder();
        Builder projectBuilder = ProjectList.Project.newBuilder();
        for (DefaultModule project : results) {
          projectBuilder.clear();
          projectListBuilder.putProjectsByKey(project.k, projectBuilder
            .setKey(project.k)
            .setName(project.nm)
            .build());
        }
        ProtobufUtil.writeToFile(projectListBuilder.build(), dest.resolve(StoragePaths.PROJECT_LIST_PB));
      } catch (IOException e) {
        throw new IllegalStateException("Failed to load module list", e);
      }
    }
  }

  private static class DefaultModule {
    String k;
    String nm;
    String qu;
  }

}
