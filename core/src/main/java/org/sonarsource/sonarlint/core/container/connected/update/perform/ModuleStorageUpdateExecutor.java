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
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class ModuleStorageUpdateExecutor {

  private final StorageReader storageReader;
  private final SonarLintWsClient wsClient;
  private final IssueDownloader issueDownloader;
  private final IssueStoreFactory issueStoreFactory;
  private final TempFolder tempFolder;
  private final ModuleConfigurationDownloader moduleConfigurationDownloader;
  private final StoragePaths storagePaths;

  public ModuleStorageUpdateExecutor(StorageReader storageReader, StoragePaths storagePaths, SonarLintWsClient wsClient,
    IssueDownloader issueDownloader, IssueStoreFactory issueStoreFactory, TempFolder tempFolder, ModuleConfigurationDownloader moduleConfigurationDownloader) {
    this.storageReader = storageReader;
    this.storagePaths = storagePaths;
    this.wsClient = wsClient;
    this.issueDownloader = issueDownloader;
    this.issueStoreFactory = issueStoreFactory;
    this.tempFolder = tempFolder;
    this.moduleConfigurationDownloader = moduleConfigurationDownloader;
  }

  public void update(String moduleKey, ProgressWrapper progress) {
    GlobalProperties globalProps = storageReader.readGlobalProperties();
    FileUtils.replaceDir(temp -> {
      updateModuleConfiguration(moduleKey, globalProps, temp, progress);
      updateRemoteIssues(moduleKey, temp);
      updateStatus(temp);
    }, storagePaths.getModuleStorageRoot(moduleKey), tempFolder.newDir().toPath());
  }

  private void updateModuleConfiguration(String moduleKey, GlobalProperties globalProps, Path temp, ProgressWrapper progress) {
    ModuleConfiguration moduleConfiguration = moduleConfigurationDownloader.fetchModuleConfiguration(storageReader.readServerInfos().getVersion(), moduleKey,
      globalProps, progress);
    final Set<String> qProfileKeys = storageReader.readQProfiles().getQprofilesByKeyMap().keySet();
    for (String qpKey : moduleConfiguration.getQprofilePerLanguageMap().values()) {
      if (!qProfileKeys.contains(qpKey)) {
        throw new IllegalStateException(
          "Module '" + moduleKey + "' is associated to quality profile '" + qpKey + "' that is not in the storage. "
            + "The SonarQube server binding is probably outdated,  please update it.");
      }
    }
    ProtobufUtil.writeToFile(moduleConfiguration, temp.resolve(StoragePaths.MODULE_CONFIGURATION_PB));
  }

  private void updateRemoteIssues(String moduleKey, Path temp) {
    Path basedir = temp.resolve(StoragePaths.SERVER_ISSUES_DIR);
    new ServerIssueUpdater(storagePaths, issueDownloader, issueStoreFactory, tempFolder).updateServerIssues(moduleKey, basedir);
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
