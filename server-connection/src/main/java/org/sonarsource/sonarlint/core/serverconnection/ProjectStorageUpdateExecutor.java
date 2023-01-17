/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufUtil;

public class ProjectStorageUpdateExecutor {
  private final ProjectFileListDownloader projectFileListDownloader;
  private final ProjectStoragePaths projectStoragePaths;

  public ProjectStorageUpdateExecutor(ProjectStoragePaths projectStoragePaths) {
    this(projectStoragePaths, new ProjectFileListDownloader());
  }

  ProjectStorageUpdateExecutor(ProjectStoragePaths projectStoragePaths, ProjectFileListDownloader projectFileListDownloader) {
    this.projectStoragePaths = projectStoragePaths;
    this.projectFileListDownloader = projectFileListDownloader;
  }

  public void update(ServerApi serverApi, String projectKey, ProgressMonitor progress) {
    Path temp;
    try {
      temp = Files.createTempDirectory("sonarlint-global-storage");
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create temp directory", e);
    }
    try {
      FileUtils.replaceDir(dir -> {
        updateComponents(serverApi, projectKey, dir, progress);
      }, projectStoragePaths.getProjectStorageRoot(projectKey), temp);
    } finally {
      org.apache.commons.io.FileUtils.deleteQuietly(temp.toFile());
    }
  }

  void updateComponents(ServerApi serverApi, String projectKey, Path temp, ProgressMonitor progress) {
    var sqFiles = projectFileListDownloader.get(serverApi, projectKey, progress);
    var componentsBuilder = Sonarlint.ProjectComponents.newBuilder();

    for (String fileKey : sqFiles) {
      var separatorIdx = StringUtils.lastIndexOf(fileKey, ":");
      var relativePath = fileKey.substring(separatorIdx + 1);
      componentsBuilder.addComponent(relativePath);
    }
    ProtobufUtil.writeToFile(componentsBuilder.build(), temp.resolve(ProjectStoragePaths.COMPONENT_LIST_PB));
  }

}
