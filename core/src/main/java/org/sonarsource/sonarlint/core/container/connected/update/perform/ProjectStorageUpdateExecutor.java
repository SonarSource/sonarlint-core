/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.container.connected.update.perform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleHierarchyDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectFileListDownloader;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class ProjectStorageUpdateExecutor {
  private final ProjectConfigurationDownloader projectConfigurationDownloader;
  private final ProjectFileListDownloader projectFileListDownloader;
  private final ServerIssueUpdater serverIssueUpdater;
  private final ProjectStoragePaths projectStoragePaths;

  public ProjectStorageUpdateExecutor(ProjectStoragePaths projectStoragePaths) {
    this(projectStoragePaths, new ProjectConfigurationDownloader(new ModuleHierarchyDownloader()), new ProjectFileListDownloader(),
      new ServerIssueUpdater(projectStoragePaths, new IssueDownloader(new IssueStorePaths()), new IssueStoreFactory()));
  }

  ProjectStorageUpdateExecutor(ProjectStoragePaths projectStoragePaths, ProjectConfigurationDownloader projectConfigurationDownloader,
    ProjectFileListDownloader projectFileListDownloader, ServerIssueUpdater serverIssueUpdater) {
    this.projectStoragePaths = projectStoragePaths;
    this.projectConfigurationDownloader = projectConfigurationDownloader;
    this.projectFileListDownloader = projectFileListDownloader;
    this.serverIssueUpdater = serverIssueUpdater;
  }

  public void update(ServerApiHelper serverApiHelper, String projectKey, boolean fetchTaintVulnerabilities, ProgressMonitor progress) {
    Path temp;
    try {
      temp = Files.createTempDirectory("sonarlint-global-storage");
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create temp directory", e);
    }
    try {
      FileUtils.replaceDir(dir -> {
        var projectConfiguration = updateConfiguration(serverApiHelper, projectKey, dir, progress);
        updateServerIssues(serverApiHelper, projectKey, dir, projectConfiguration, fetchTaintVulnerabilities, progress);
        updateComponents(serverApiHelper, projectKey, dir, projectConfiguration, progress);
        updateStatus(dir);
      }, projectStoragePaths.getProjectStorageRoot(projectKey), temp);
    } finally {
      org.apache.commons.io.FileUtils.deleteQuietly(temp.toFile());
    }
  }

  private ProjectConfiguration updateConfiguration(ServerApiHelper serverApiHelper, String projectKey, Path temp, ProgressMonitor progress) {
    var projectConfiguration = projectConfigurationDownloader.fetch(serverApiHelper, projectKey, progress);
    ProtobufUtil.writeToFile(projectConfiguration, temp.resolve(ProjectStoragePaths.PROJECT_CONFIGURATION_PB));
    return projectConfiguration;
  }

  void updateComponents(ServerApiHelper serverApiHelper, String projectKey, Path temp, ProjectConfiguration projectConfiguration, ProgressMonitor progress) {
    var sqFiles = projectFileListDownloader.get(serverApiHelper, projectKey, progress);
    var componentsBuilder = Sonarlint.ProjectComponents.newBuilder();

    var modulePathByKey = projectConfiguration.getModulePathByKeyMap();
    for (String fileKey : sqFiles) {
      var idx = StringUtils.lastIndexOf(fileKey, ":");
      var moduleKey = fileKey.substring(0, idx);
      var relativePath = fileKey.substring(idx + 1);
      var prefix = modulePathByKey.getOrDefault(moduleKey, "");
      if (!prefix.isEmpty()) {
        prefix = prefix + "/";
      }
      componentsBuilder.addComponent(prefix + relativePath);
    }
    ProtobufUtil.writeToFile(componentsBuilder.build(), temp.resolve(ProjectStoragePaths.COMPONENT_LIST_PB));
  }

  private void updateServerIssues(ServerApiHelper serverApiHelper, String projectKey, Path temp, ProjectConfiguration projectConfiguration, boolean fetchTaintVulnerabilities,
    ProgressMonitor progress) {
    var basedir = temp.resolve(ProjectStoragePaths.SERVER_ISSUES_DIR);
    serverIssueUpdater.updateServerIssues(serverApiHelper, projectKey, projectConfiguration, basedir, fetchTaintVulnerabilities, progress);
  }

  private static void updateStatus(Path temp) {
    var storageStatus = StorageStatus.newBuilder()
      .setStorageVersion(ProjectStoragePaths.STORAGE_VERSION)
      .setSonarlintCoreVersion(VersionUtils.getLibraryVersion())
      .setUpdateTimestamp(new Date().getTime())
      .build();
    ProtobufUtil.writeToFile(storageStatus, temp.resolve(ProjectStoragePaths.STORAGE_STATUS_PB));
  }

}
