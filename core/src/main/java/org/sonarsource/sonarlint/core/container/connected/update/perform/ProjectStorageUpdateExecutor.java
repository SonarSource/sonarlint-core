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
package org.sonarsource.sonarlint.core.container.connected.update.perform;

import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectFileListDownloader;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class ProjectStorageUpdateExecutor {
  private final TempFolder tempFolder;
  private final ProjectConfigurationDownloader projectConfigurationDownloader;
  private final ProjectFileListDownloader projectFileListDownloader;
  private final ServerIssueUpdater serverIssueUpdater;
  private final ProjectStoragePaths projectStoragePaths;

  public ProjectStorageUpdateExecutor(ProjectStoragePaths projectStoragePaths, TempFolder tempFolder, ProjectConfigurationDownloader projectConfigurationDownloader,
    ProjectFileListDownloader projectFileListDownloader, ServerIssueUpdater serverIssueUpdater) {
    this.projectStoragePaths = projectStoragePaths;
    this.tempFolder = tempFolder;
    this.projectConfigurationDownloader = projectConfigurationDownloader;
    this.projectFileListDownloader = projectFileListDownloader;
    this.serverIssueUpdater = serverIssueUpdater;
  }

  public void update(String projectKey, boolean fetchTaintVulnerabilities, ProgressMonitor progress) {
    FileUtils.replaceDir(temp -> {
      var projectConfiguration = updateConfiguration(projectKey, temp, progress);
      updateServerIssues(projectKey, temp, projectConfiguration, fetchTaintVulnerabilities, progress);
      updateComponents(projectKey, temp, projectConfiguration, progress);
      updateStatus(temp);
    }, projectStoragePaths.getProjectStorageRoot(projectKey), tempFolder.newDir().toPath());
  }

  private ProjectConfiguration updateConfiguration(String projectKey, Path temp, ProgressMonitor progress) {
    var projectConfiguration = projectConfigurationDownloader.fetch(projectKey, progress);
    ProtobufUtil.writeToFile(projectConfiguration, temp.resolve(ProjectStoragePaths.PROJECT_CONFIGURATION_PB));
    return projectConfiguration;
  }

  void updateComponents(String projectKey, Path temp, ProjectConfiguration projectConfiguration, ProgressMonitor progress) {
    List<String> sqFiles = projectFileListDownloader.get(projectKey, progress);
    var componentsBuilder = Sonarlint.ProjectComponents.newBuilder();

    Map<String, String> modulePathByKey = projectConfiguration.getModulePathByKeyMap();
    for (String fileKey : sqFiles) {
      int idx = StringUtils.lastIndexOf(fileKey, ":");
      var moduleKey = fileKey.substring(0, idx);
      var relativePath = fileKey.substring(idx + 1);
      String prefix = modulePathByKey.getOrDefault(moduleKey, "");
      if (!prefix.isEmpty()) {
        prefix = prefix + "/";
      }
      componentsBuilder.addComponent(prefix + relativePath);
    }
    ProtobufUtil.writeToFile(componentsBuilder.build(), temp.resolve(ProjectStoragePaths.COMPONENT_LIST_PB));
  }

  private void updateServerIssues(String projectKey, Path temp, ProjectConfiguration projectConfiguration, boolean fetchTaintVulnerabilities, ProgressMonitor progress) {
    Path basedir = temp.resolve(ProjectStoragePaths.SERVER_ISSUES_DIR);
    serverIssueUpdater.updateServerIssues(projectKey, projectConfiguration, basedir, fetchTaintVulnerabilities, progress);
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
