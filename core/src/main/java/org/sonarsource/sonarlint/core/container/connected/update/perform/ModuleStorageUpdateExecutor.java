/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
import java.util.Set;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectId;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalUpdateRequiredException;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class ModuleStorageUpdateExecutor {

  private final StorageManager storageManager;
  private final SonarLintWsClient wsClient;
  private final IssueDownloader issueDownloader;
  private final IssueStoreFactory issueStoreFactory;
  private final TempFolder tempFolder;
  private final ProjectConfigurationDownloader projectConfigurationDownloader;

  public ModuleStorageUpdateExecutor(StorageManager storageManager, SonarLintWsClient wsClient,
    IssueDownloader issueDownloader, IssueStoreFactory issueStoreFactory, TempFolder tempFolder, ProjectConfigurationDownloader moduleConfigurationDownloader) {
    this.storageManager = storageManager;
    this.wsClient = wsClient;
    this.issueDownloader = issueDownloader;
    this.issueStoreFactory = issueStoreFactory;
    this.tempFolder = tempFolder;
    this.projectConfigurationDownloader = moduleConfigurationDownloader;
  }

  public void update(ProjectId projectId) {
    GlobalProperties globalProps = storageManager.readGlobalPropertiesFromStorage();
    FileUtils.replaceDir(temp -> {
      updateModuleConfiguration(projectId, globalProps, temp);
      updateRemoteIssues(projectId, temp);
      updateStatus(temp);
    }, storageManager.getProjectStorageRoot(projectId), tempFolder.newDir().toPath());
  }

  private void updateModuleConfiguration(ProjectId projectId, GlobalProperties globalProps, Path temp) {
    ModuleConfiguration moduleConfiguration = projectConfigurationDownloader.fetchProjectConfiguration(storageManager.readServerInfosFromStorage().getVersion(), projectId,
      globalProps);
    final Set<String> qProfileKeys = storageManager.readQProfilesFromStorage().getQprofilesByKeyMap().keySet();
    for (String qpKey : moduleConfiguration.getQprofilePerLanguageMap().values()) {
      if (!qProfileKeys.contains(qpKey)) {
        throw new GlobalUpdateRequiredException(
          "Project '" + projectId + "' is associated to quality profile '" + qpKey + "' that is not in storage.");
      }
    }
    ProtobufUtil.writeToFile(moduleConfiguration, temp.resolve(StorageManager.MODULE_CONFIGURATION_PB));
  }

  private void updateRemoteIssues(ProjectId projectId, Path temp) {
    Path basedir = temp.resolve(StorageManager.SERVER_ISSUES_DIR);
    new ServerIssueUpdater(storageManager, issueDownloader, issueStoreFactory, tempFolder).updateServerIssues(projectId, basedir);
  }

  private void updateStatus(Path temp) {
    StorageStatus storageStatus = StorageStatus.newBuilder()
      .setStorageVersion(StorageManager.STORAGE_VERSION)
      .setClientUserAgent(wsClient.getUserAgent())
      .setSonarlintCoreVersion(VersionUtils.getLibraryVersion())
      .setUpdateTimestamp(new Date().getTime())
      .build();
    ProtobufUtil.writeToFile(storageStatus, temp.resolve(StorageManager.STORAGE_STATUS_PB));
  }

}
