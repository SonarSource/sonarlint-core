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
package org.sonarsource.sonarlint.core.container.connected.update.perform;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStoreUtils;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectFileListDownloader;
import org.sonarsource.sonarlint.core.container.storage.FileMatcher;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectPathPrefixes;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.ReversePathTree;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class ProjectStorageUpdateExecutor {

  private final StorageReader storageReader;
  private final SonarLintWsClient wsClient;
  private final IssueDownloader issueDownloader;
  private final IssueStoreFactory issueStoreFactory;
  private final TempFolder tempFolder;
  private final ProjectConfigurationDownloader projectConfigurationDownloader;
  private final ProjectFileListDownloader projectFileListDownloader;
  private final StoragePaths storagePaths;

  public ProjectStorageUpdateExecutor(StorageReader storageReader, StoragePaths storagePaths, SonarLintWsClient wsClient,
    IssueDownloader issueDownloader, IssueStoreFactory issueStoreFactory, TempFolder tempFolder,
    ProjectConfigurationDownloader projectConfigurationDownloader, ProjectFileListDownloader projectFileListDownloader) {
    this.storageReader = storageReader;
    this.storagePaths = storagePaths;
    this.wsClient = wsClient;
    this.issueDownloader = issueDownloader;
    this.issueStoreFactory = issueStoreFactory;
    this.tempFolder = tempFolder;
    this.projectConfigurationDownloader = projectConfigurationDownloader;
    this.projectFileListDownloader = projectFileListDownloader;
  }

  public void update(String projectKey, Collection<String> localFilePaths, ProgressWrapper progress) {
    GlobalProperties globalProps = storageReader.readGlobalProperties();
    FileUtils.replaceDir(temp -> {
      ProjectConfiguration projectConfiguration = updateConfiguration(projectKey, globalProps, temp, progress);
      ProjectPathPrefixes pathPrefixes = updatePathPrefixes(projectKey, localFilePaths, progress);
      updateServerIssues(projectKey, temp, projectConfiguration, pathPrefixes);
      updateStatus(temp);
    }, storagePaths.getProjectStorageRoot(projectKey), tempFolder.newDir().toPath());
  }

  private ProjectConfiguration updateConfiguration(String projectKey, GlobalProperties globalProps, Path temp, ProgressWrapper progress) {
    Version serverVersion = Version.create(storageReader.readServerInfos().getVersion());
    ProjectConfiguration projectConfiguration = projectConfigurationDownloader.fetchModuleConfiguration(serverVersion, projectKey,
      globalProps, progress);
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

  private ProjectPathPrefixes updatePathPrefixes(String projectKey, Collection<String> localPaths, ProgressWrapper progress) {
    List<ProjectFileListDownloader.File> sqFiles = projectFileListDownloader.get(projectKey, progress);
    List<Path> sqPaths = sqFiles.stream()
      .map(f -> Paths.get(f.path()))
      .collect(Collectors.toList());

    List<Path> localPathList = localPaths.stream()
      .map(Paths::get)
      .collect(Collectors.toList());

    FileMatcher fileMatcher = new FileMatcher(new ReversePathTree());
    FileMatcher.Result match = fileMatcher.match(sqPaths, localPathList);
    return ProjectPathPrefixes.newBuilder()
      .setLocalPathPrefix(FilenameUtils.separatorsToUnix(match.mostCommonLocalPrefix().toString()))
      .setSqPathPrefix(FilenameUtils.separatorsToUnix(match.mostCommonSqPrefix().toString()))
      .build();
  }

  private void updateServerIssues(String moduleKey, Path temp, ProjectConfiguration projectConfiguration, ProjectPathPrefixes pathPrefixes) {
    Path basedir = temp.resolve(StoragePaths.SERVER_ISSUES_DIR);
    IssueStoreUtils issueStoreUtils = new IssueStoreUtils(projectConfiguration, pathPrefixes);
    new ServerIssueUpdater(storagePaths, issueDownloader, issueStoreFactory, tempFolder, issueStoreUtils)
      .updateServerIssues(moduleKey, basedir);
  }

  private void updateStatus(Path temp) {
    StorageStatus storageStatus = StorageStatus.newBuilder()
      .setStorageVersion(StoragePaths.STORAGE_VERSION)
      .setClientUserAgent(wsClient.getUserAgent())
      .setSonarlintCoreVersion(VersionUtils.getLibraryVersion())
      .setUpdateTimestamp(new Date().getTime())
      .build();
    ProtobufUtil.writeToFile(storageStatus, temp.resolve(StoragePaths.STORAGE_STATUS_PB));
  }

}
