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
package org.sonarsource.sonarlint.core.container.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteProject;
import org.sonarsource.sonarlint.core.container.model.DefaultRemoteProject;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectList;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectList.Project;

public class AllProjectReader implements Supplier<Map<String, RemoteProject>> {
  private final StorageReader storageReader;

  public AllProjectReader(StorageReader storageReader) {
    this.storageReader = storageReader;
  }

  @Override
  public Map<String, RemoteProject> get() {
    Map<String, RemoteProject> results = new HashMap<>();
    ProjectList projectList = storageReader.readProjectList();
    Map<String, Project> projectsByKey = projectList.getProjectsByKeyMap();
    for (Map.Entry<String, Project> entry : projectsByKey.entrySet()) {
      results.put(entry.getKey(), new DefaultRemoteProject(entry.getValue()));
    }
    return results;
  }
}
