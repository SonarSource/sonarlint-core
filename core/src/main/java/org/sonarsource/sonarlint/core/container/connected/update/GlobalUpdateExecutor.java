/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.nio.file.Path;
import java.util.Date;
import java.util.Set;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.proto.Sonarlint.UpdateStatus;
import org.sonarsource.sonarlint.core.util.FileUtils;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class GlobalUpdateExecutor {

  private final StorageManager storageManager;
  private final PluginReferencesDownloader pluginReferenceDownloader;
  private final GlobalPropertiesDownloader globalPropertiesDownloader;
  private final RulesDownloader rulesDownloader;
  private final TempFolder tempFolder;
  private final ModuleListDownloader moduleListDownloader;
  private final ServerVersionAndStatusChecker statusChecker;
  private final SonarLintWsClient wsClient;
  private final PluginVersionChecker pluginsChecker;
  private final QualityProfilesDownloader qualityProfilesDownloader;

  public GlobalUpdateExecutor(StorageManager storageManager, SonarLintWsClient wsClient, PluginVersionChecker pluginsChecker, ServerVersionAndStatusChecker statusChecker,
    PluginReferencesDownloader pluginReferenceDownloader, GlobalPropertiesDownloader globalPropertiesDownloader, RulesDownloader rulesDownloader,
    ModuleListDownloader moduleListDownloader, QualityProfilesDownloader qualityProfilesDownloader, TempFolder tempFolder) {
    this.storageManager = storageManager;
    this.wsClient = wsClient;
    this.pluginsChecker = pluginsChecker;
    this.statusChecker = statusChecker;
    this.pluginReferenceDownloader = pluginReferenceDownloader;
    this.globalPropertiesDownloader = globalPropertiesDownloader;
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
      pluginsChecker.checkPlugins(serverStatus.getVersion());

      ProtobufUtil.writeToFile(serverStatus, temp.resolve(StorageManager.SERVER_INFO_PB));

      progress.setProgressAndCheckCancel("Fetching global properties", 0.2f);
      Set<String> allowedPlugins = globalPropertiesDownloader.fetchGlobalPropertiesTo(temp);

      progress.setProgressAndCheckCancel("Fetching plugins", 0.3f);
      pluginReferenceDownloader.fetchPluginsTo(temp, allowedPlugins);

      progress.setProgressAndCheckCancel("Fetching rules", 0.4f);
      rulesDownloader.fetchRulesTo(temp);

      progress.setProgressAndCheckCancel("Fetching quality profiles", 0.4f);
      qualityProfilesDownloader.fetchQualityProfiles(temp);

      progress.setProgressAndCheckCancel("Fetching list of modules", 0.8f);
      moduleListDownloader.fetchModulesList(temp);

      progress.startNonCancelableSection();
      progress.setProgressAndCheckCancel("Finalizing...", 1.0f);

      UpdateStatus updateStatus = UpdateStatus.newBuilder()
        .setClientUserAgent(wsClient.getUserAgent())
        .setSonarlintCoreVersion(VersionUtils.getLibraryVersion())
        .setUpdateTimestamp(new Date().getTime())
        .build();
      ProtobufUtil.writeToFile(updateStatus, temp.resolve(StorageManager.UPDATE_STATUS_PB));

      Path dest = storageManager.getGlobalStorageRoot();
      FileUtils.deleteDirectory(dest);
      FileUtils.forceMkDirs(dest.getParent());
      FileUtils.moveDir(temp, dest);
    } catch (RuntimeException e) {
      try {
        FileUtils.deleteDirectory(temp);
      } catch (RuntimeException ignore) {
        // ignore because we want to throw original exception
      }
      throw e;
    }
  }
}
