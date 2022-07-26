/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerIssueStoresManager {

  Map<String, ProjectServerIssueStore> serverIssueStoreByKey = new ConcurrentHashMap<>();
  private final Path projectsStorageBaseDir;

  public ServerIssueStoresManager(Path projectsStorageBaseDir) {
    this.projectsStorageBaseDir = projectsStorageBaseDir;

  }

  public ProjectServerIssueStore get(String projectKey) {
    return serverIssueStoreByKey.computeIfAbsent(projectKey,
      p -> new XodusServerIssueStore(projectsStorageBaseDir.resolve(ProjectStoragePaths.encodeForFs(projectKey)).resolve("issues")));
  }

  public void close() {
    serverIssueStoreByKey.values().forEach(ProjectServerIssueStore::close);
    serverIssueStoreByKey.clear();
  }
}
