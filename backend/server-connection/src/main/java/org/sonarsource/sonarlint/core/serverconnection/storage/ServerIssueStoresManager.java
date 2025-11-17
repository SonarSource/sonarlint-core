/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.sonarsource.sonarlint.core.commons.monitoring.DogfoodEnvironmentDetectionService;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;

public class ServerIssueStoresManager {

  Map<String, ProjectServerIssueStore> serverIssueStoreByKey = new ConcurrentHashMap<>();
  private final Path projectsStorageBaseDir;
  private final DogfoodEnvironmentDetectionService dogfoodEnvDetectionService;
  private final SonarLintDatabase database;
  private final Path workDir;
  private final String connectionId;

  public ServerIssueStoresManager(Path projectsStorageBaseDir, Path workDir, String connectionId,
    DogfoodEnvironmentDetectionService dogfoodEnvDetectionService, SonarLintDatabase database) {
    this.projectsStorageBaseDir = projectsStorageBaseDir;
    this.workDir = workDir;
    this.dogfoodEnvDetectionService = dogfoodEnvDetectionService;
    this.database = database;
    this.connectionId = connectionId;
  }

  public ProjectServerIssueStore get(String projectKey) {
    var xodusBackupPath = projectsStorageBaseDir.resolve(ProjectStoragePaths.encodeForFs(projectKey)).resolve("issues");
    return serverIssueStoreByKey.computeIfAbsent(projectKey,
      p -> {
        try {
          if (dogfoodEnvDetectionService.isDogfoodEnvironment()) {
            return new ServerFindingRepository(database, connectionId, projectKey);
          } else {
            return new XodusServerIssueStore(xodusBackupPath, workDir);
          }
        } catch (IOException e) {
          throw new IllegalStateException("Unable to create server issue database", e);
        }
      });
  }

  public void close() {
    serverIssueStoreByKey.values().forEach(ProjectServerIssueStore::close);
    serverIssueStoreByKey.clear();
  }
}
