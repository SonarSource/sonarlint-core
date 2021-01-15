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
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectFileListDownloader;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class ProjectStorageUpdateExecutor {

  private final StorageReader storageReader;
  private final TempFolder tempFolder;
  private final ProjectConfigurationDownloader projectConfigurationDownloader;
  private final ProjectFileListDownloader projectFileListDownloader;
  private final ServerIssueUpdater serverIssueUpdater;
  private final StoragePaths storagePaths;

  public ProjectStorageUpdateExecutor(StorageReader storageReader, StoragePaths storagePaths, TempFolder tempFolder,
    ProjectConfigurationDownloader projectConfigurationDownloader, ProjectFileListDownloader projectFileListDownloader, ServerIssueUpdater serverIssueUpdater) {
    this.storageReader = storageReader;
    this.storagePaths = storagePaths;
    this.tempFolder = tempFolder;
    this.projectConfigurationDownloader = projectConfigurationDownloader;
    this.projectFileListDownloader = projectFileListDownloader;
    this.serverIssueUpdater = serverIssueUpdater;
  }

  public void update(String projectKey, ProgressWrapper progress) {
    GlobalProperties globalProps = storageReader.readGlobalProperties();

    FileUtils.replaceDir(temp -> {
      ProjectConfiguration projectConfiguration = updateConfiguration(projectKey, globalProps, temp, progress);
      updateServerIssues(projectKey, temp, projectConfiguration, progress);
      updateComponents(projectKey, temp, projectConfiguration, progress);
      updateStatus(temp);
    }, storagePaths.getProjectStorageRoot(projectKey), tempFolder.newDir().toPath());
  }

  private ProjectConfiguration updateConfiguration(String projectKey, GlobalProperties globalProps, Path temp, ProgressWrapper progress) {
    ProjectConfiguration projectConfiguration = projectConfigurationDownloader.fetch(projectKey, globalProps, progress);
    final Set<String> qProfileKeys = storageReader.readQProfiles().getQprofilesByKeyMap().keySet();
    for (String qpKey : projectConfiguration.getQprofilePerLanguageMap().values()) {
      if (!qProfileKeys.contains(qpKey)) {
        throw new IllegalStateException(
          "Project '" + projectKey + "' is associated to quality profile '" + qpKey + "' that is not in the storage. "
            + "The SonarQube server binding is probably outdated,  please update it.");
      }
    }
    ProtobufUtil.writeToFile(projectConfiguration, temp.resolve(StoragePaths.PROJECT_CONFIGURATION_PB));
    return projectConfiguration;
  }

  void updateComponents(String projectKey, Path temp, ProjectConfiguration projectConfiguration, ProgressWrapper progress) {
    List<String> sqFiles = projectFileListDownloader.get(projectKey, progress);
    Sonarlint.ProjectComponents.Builder componentsBuilder = Sonarlint.ProjectComponents.newBuilder();

    Map<String, String> modulePathByKey = projectConfiguration.getModulePathByKeyMap();
    for (String fileKey : sqFiles) {
      int idx = StringUtils.lastIndexOf(fileKey, ":");
      String moduleKey = fileKey.substring(0, idx);
      String relativePath = fileKey.substring(idx + 1);
      String prefix = modulePathByKey.getOrDefault(moduleKey, "");
      if (!prefix.isEmpty()) {
        prefix = prefix + "/";
      }
      componentsBuilder.addComponent(prefix + relativePath);
    }
    ProtobufUtil.writeToFile(componentsBuilder.build(), temp.resolve(StoragePaths.COMPONENT_LIST_PB));
  }

  private void updateServerIssues(String projectKey, Path temp, ProjectConfiguration projectConfiguration, ProgressWrapper progress) {
    Path basedir = temp.resolve(StoragePaths.SERVER_ISSUES_DIR);
    serverIssueUpdater.updateServerIssues(projectKey, projectConfiguration, basedir, progress);
  }

  private void updateStatus(Path temp) {
    StorageStatus storageStatus = StorageStatus.newBuilder()
      .setStorageVersion(StoragePaths.STORAGE_VERSION)
      .setSonarlintCoreVersion(VersionUtils.getLibraryVersion())
      .setUpdateTimestamp(new Date().getTime())
      .build();
    ProtobufUtil.writeToFile(storageStatus, temp.resolve(StoragePaths.STORAGE_STATUS_PB));
  }

}
