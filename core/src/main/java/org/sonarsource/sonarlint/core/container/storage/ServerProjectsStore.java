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
package org.sonarsource.sonarlint.core.container.storage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarlint.core.container.model.DefaultRemoteProject;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverapi.project.ServerProject;

public class ServerProjectsStore {
  public static final String PROJECT_LIST_PB = "project_list.pb";

  private final StorageFolder storageFolder;
  private final RWLock rwLock = new RWLock();

  public ServerProjectsStore(StorageFolder storageFolder) {
    this.storageFolder = storageFolder;
  }

  public void store(List<ServerProject> serverProjects) {
    rwLock.write(() -> {
      Sonarlint.ProjectList projectList = adapt(serverProjects);
      storageFolder.writeAction(dest -> ProtobufUtil.writeToFile(projectList, dest.resolve(PROJECT_LIST_PB)));
    });
  }

  public Map<String, ServerProject> getAll() {
    Sonarlint.ProjectList projectList = rwLock.read(() -> storageFolder.readAction(source ->
      ProtobufUtil.readFile(source.resolve(PROJECT_LIST_PB), Sonarlint.ProjectList.parser())
    ));
    return adapt(projectList);
  }

  private static Sonarlint.ProjectList adapt(List<ServerProject> serverProjects) {
    Sonarlint.ProjectList.Builder projectListBuilder = Sonarlint.ProjectList.newBuilder();
    Sonarlint.ProjectList.Project.Builder projectBuilder = Sonarlint.ProjectList.Project.newBuilder();
    serverProjects.forEach(project -> {
      projectBuilder.clear();
      projectListBuilder.putProjectsByKey(project.getKey(), projectBuilder
        .setKey(project.getKey())
        .setName(project.getName())
        .build());
    });
    return projectListBuilder.build();
  }

  private static Map<String, ServerProject> adapt(Sonarlint.ProjectList projectList) {
    Map<String, ServerProject> converted = new HashMap<>();
    Map<String, Sonarlint.ProjectList.Project> projectsByKey = projectList.getProjectsByKeyMap();
    for (Map.Entry<String, Sonarlint.ProjectList.Project> entry : projectsByKey.entrySet()) {
      converted.put(entry.getKey(), new DefaultRemoteProject(entry.getValue()));
    }
    return converted;
  }
}
