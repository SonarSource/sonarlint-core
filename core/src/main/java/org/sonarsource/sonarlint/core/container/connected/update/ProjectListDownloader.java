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
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectList;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectList.Project.Builder;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.project.ProjectApi;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class ProjectListDownloader {
  private final ProjectApi projectApi;

  public ProjectListDownloader(ServerApiHelper serverApiHelper) {
    this.projectApi = new ServerApi(serverApiHelper).project();
  }

  public void fetchTo(Path dest, ProgressWrapper progress) {
    ProjectList.Builder projectListBuilder = ProjectList.newBuilder();
    Builder projectBuilder = ProjectList.Project.newBuilder();
    projectApi.getAllProjects(progress).forEach(project -> {
      projectBuilder.clear();
      projectListBuilder.putProjectsByKey(project.getKey(), projectBuilder
        .setKey(project.getKey())
        .setName(project.getName())
        .build());
    });

    ProtobufUtil.writeToFile(projectListBuilder.build(), dest.resolve(StoragePaths.PROJECT_LIST_PB));
  }

}
