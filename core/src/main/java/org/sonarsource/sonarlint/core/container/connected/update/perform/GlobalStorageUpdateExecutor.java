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
import java.util.Date;
import java.util.List;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.PluginListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.PluginReferencesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.QualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.RulesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.SettingsDownloader;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class GlobalStorageUpdateExecutor {

  private final StoragePaths storageManager;
  private final PluginReferencesDownloader pluginReferenceDownloader;
  private final SettingsDownloader globalSettingsDownloader;
  private final RulesDownloader rulesDownloader;
  private final TempFolder tempFolder;
  private final ProjectListDownloader projectListDownloader;
  private final ServerVersionAndStatusChecker statusChecker;
  private final SonarLintWsClient wsClient;
  private final QualityProfilesDownloader qualityProfilesDownloader;
  private final PluginListDownloader pluginListDownloader;

  public GlobalStorageUpdateExecutor(StoragePaths storageManager, SonarLintWsClient wsClient, ServerVersionAndStatusChecker statusChecker,
    PluginReferencesDownloader pluginReferenceDownloader, SettingsDownloader globalPropertiesDownloader, RulesDownloader rulesDownloader,
    ProjectListDownloader projectListDownloader, QualityProfilesDownloader qualityProfilesDownloader, PluginListDownloader pluginListDownloader, TempFolder tempFolder) {
    this.storageManager = storageManager;
    this.wsClient = wsClient;
    this.statusChecker = statusChecker;
    this.pluginReferenceDownloader = pluginReferenceDownloader;
    this.globalSettingsDownloader = globalPropertiesDownloader;
    this.rulesDownloader = rulesDownloader;
    this.projectListDownloader = projectListDownloader;
    this.qualityProfilesDownloader = qualityProfilesDownloader;
    this.pluginListDownloader = pluginListDownloader;
    this.tempFolder = tempFolder;
  }

  public List<SonarAnalyzer> update(ProgressWrapper progress) {
    Path temp = tempFolder.newDir().toPath();

    try {
      progress.setProgressAndCheckCancel("Checking server version and status", 0.1f);
      ServerInfos serverStatus = statusChecker.checkVersionAndStatus();
      Version serverVersion = Version.create(serverStatus.getVersion());
      
      progress.setProgressAndCheckCancel("Fetching list of code analyzers", 0.12f);
      List<SonarAnalyzer> analyzers = pluginListDownloader.downloadPluginList(serverVersion);
      ProtobufUtil.writeToFile(serverStatus, temp.resolve(StoragePaths.SERVER_INFO_PB));

      progress.setProgressAndCheckCancel("Fetching global properties", 0.15f);
      globalSettingsDownloader.fetchGlobalSettingsTo(serverVersion, temp);

      progress.setProgressAndCheckCancel("Fetching analyzers", 0.25f);
      pluginReferenceDownloader.fetchPluginsTo(serverVersion, temp, analyzers, progress.subProgress(0.25f, 0.4f, "Fetching code analyzers"));

      progress.setProgressAndCheckCancel("Fetching rules", 0.4f);
      rulesDownloader.fetchRulesTo(temp, progress.subProgress(0.4f, 0.6f, "Fetching rules"));

      progress.setProgressAndCheckCancel("Fetching quality profiles", 0.6f);
      qualityProfilesDownloader.fetchQualityProfilesTo(temp);

      progress.setProgressAndCheckCancel("Fetching list of projects", 0.8f);
      projectListDownloader.fetchModulesListTo(temp, serverStatus.getVersion(), progress.subProgress(0.8f, 1.0f, "Fetching list of projects"));

      progress.startNonCancelableSection();
      progress.setProgressAndCheckCancel("Finalizing...", 1.0f);

      StorageStatus storageStatus = StorageStatus.newBuilder()
        .setStorageVersion(StoragePaths.STORAGE_VERSION)
        .setClientUserAgent(wsClient.getUserAgent())
        .setSonarlintCoreVersion(VersionUtils.getLibraryVersion())
        .setUpdateTimestamp(new Date().getTime())
        .build();
      ProtobufUtil.writeToFile(storageStatus, temp.resolve(StoragePaths.STORAGE_STATUS_PB));

      Path dest = storageManager.getGlobalStorageRoot();
      FileUtils.deleteRecursively(dest);
      FileUtils.mkdirs(dest.getParent());
      FileUtils.moveDir(temp, dest);
      return analyzers;
    } catch (RuntimeException e) {
      try {
        FileUtils.deleteRecursively(temp);
      } catch (RuntimeException ignore) {
        // ignore because we want to throw original exception
      }
      throw e;
    }
  }
}
