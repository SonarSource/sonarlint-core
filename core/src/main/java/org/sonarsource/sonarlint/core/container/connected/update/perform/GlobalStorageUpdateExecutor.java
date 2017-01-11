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
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.PluginReferencesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.QualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.RulesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.SettingsDownloader;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class GlobalStorageUpdateExecutor {

  private final StorageManager storageManager;
  private final PluginReferencesDownloader pluginReferenceDownloader;
  private final SettingsDownloader globalSettingsDownloader;
  private final RulesDownloader rulesDownloader;
  private final TempFolder tempFolder;
  private final ModuleListDownloader moduleListDownloader;
  private final ServerVersionAndStatusChecker statusChecker;
  private final SonarLintWsClient wsClient;
  private final PluginVersionChecker pluginsChecker;
  private final QualityProfilesDownloader qualityProfilesDownloader;

  public GlobalStorageUpdateExecutor(StorageManager storageManager, SonarLintWsClient wsClient, PluginVersionChecker pluginsChecker, ServerVersionAndStatusChecker statusChecker,
    PluginReferencesDownloader pluginReferenceDownloader, SettingsDownloader globalPropertiesDownloader, RulesDownloader rulesDownloader,
    ModuleListDownloader moduleListDownloader, QualityProfilesDownloader qualityProfilesDownloader, TempFolder tempFolder) {
    this.storageManager = storageManager;
    this.wsClient = wsClient;
    this.pluginsChecker = pluginsChecker;
    this.statusChecker = statusChecker;
    this.pluginReferenceDownloader = pluginReferenceDownloader;
    this.globalSettingsDownloader = globalPropertiesDownloader;
    this.rulesDownloader = rulesDownloader;
    this.moduleListDownloader = moduleListDownloader;
    this.qualityProfilesDownloader = qualityProfilesDownloader;
    this.tempFolder = tempFolder;
  }

  public void update(ProgressWrapper progress) {
    Path temp = tempFolder.newDir().toPath();

    try {
      progress.setProgressAndCheckCancel("Checking server version and status", 0.1f);
      ServerInfos serverStatus = statusChecker.checkVersionAndStatus();
      progress.setProgressAndCheckCancel("Checking plugins versions", 0.15f);
      pluginsChecker.checkPlugins();

      ProtobufUtil.writeToFile(serverStatus, temp.resolve(StorageManager.SERVER_INFO_PB));

      progress.setProgressAndCheckCancel("Fetching global properties", 0.2f);
      globalSettingsDownloader.fetchGlobalSettingsTo(serverStatus.getVersion(), temp);

      progress.setProgressAndCheckCancel("Fetching plugins", 0.3f);
      pluginReferenceDownloader.fetchPluginsTo(temp, serverStatus.getVersion());

      progress.setProgressAndCheckCancel("Fetching rules", 0.4f);
      rulesDownloader.fetchRulesTo(temp);

      progress.setProgressAndCheckCancel("Fetching quality profiles", 0.4f);
      qualityProfilesDownloader.fetchQualityProfilesTo(temp);

      progress.setProgressAndCheckCancel("Fetching list of modules", 0.8f);
      moduleListDownloader.fetchModulesList(temp);

      progress.startNonCancelableSection();
      progress.setProgressAndCheckCancel("Finalizing...", 1.0f);

      StorageStatus storageStatus = StorageStatus.newBuilder()
        .setStorageVersion(StorageManager.STORAGE_VERSION)
        .setClientUserAgent(wsClient.getUserAgent())
        .setSonarlintCoreVersion(VersionUtils.getLibraryVersion())
        .setUpdateTimestamp(new Date().getTime())
        .build();
      ProtobufUtil.writeToFile(storageStatus, temp.resolve(StorageManager.STORAGE_STATUS_PB));

      Path dest = storageManager.getGlobalStorageRoot();
      FileUtils.deleteRecursively(dest);
      FileUtils.mkdirs(dest.getParent());
      FileUtils.moveDir(temp, dest);
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
