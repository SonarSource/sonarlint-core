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

import java.util.Date;
import java.util.List;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.connected.update.PluginListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.PluginReferencesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.QualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.RulesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.SettingsDownloader;
import org.sonarsource.sonarlint.core.container.storage.ActiveRulesStore;
import org.sonarsource.sonarlint.core.container.storage.GlobalSettingsStore;
import org.sonarsource.sonarlint.core.container.storage.PluginReferenceStore;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.container.storage.QualityProfileStore;
import org.sonarsource.sonarlint.core.container.storage.RulesStore;
import org.sonarsource.sonarlint.core.container.storage.ServerInfoStore;
import org.sonarsource.sonarlint.core.container.storage.ServerProjectsStore;
import org.sonarsource.sonarlint.core.container.storage.ServerStorage;
import org.sonarsource.sonarlint.core.container.storage.StorageFolder;
import org.sonarsource.sonarlint.core.container.storage.StorageStatusStore;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.system.ServerInfo;
import org.sonarsource.sonarlint.core.serverapi.system.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class GlobalStorageUpdateExecutor {

  private final ServerStorage serverStorage;
  private final PluginCache pluginCache;
  private final ConnectedGlobalConfiguration connectedGlobalConfiguration;
  private final TempFolder tempFolder;
  private final ServerApiHelper serverApiHelper;
  private final ServerVersionAndStatusChecker statusChecker;
  private final PluginListDownloader pluginListDownloader;

  public GlobalStorageUpdateExecutor(ServerStorage serverStorage, ServerApiHelper serverApiHelper, ServerVersionAndStatusChecker statusChecker,
    PluginCache pluginCache, PluginListDownloader pluginListDownloader, ConnectedGlobalConfiguration connectedGlobalConfiguration,
    TempFolder tempFolder) {
    this.serverStorage = serverStorage;
    this.serverApiHelper = serverApiHelper;
    this.statusChecker = statusChecker;
    this.pluginCache = pluginCache;
    this.pluginListDownloader = pluginListDownloader;
    this.connectedGlobalConfiguration = connectedGlobalConfiguration;
    this.tempFolder = tempFolder;
  }

  public List<SonarAnalyzer> update(ProgressMonitor progress) {
    var temp = tempFolder.newDir().toPath();
    StorageFolder storageFolder = new StorageFolder.Default(temp);
    var serverInfoStore = new ServerInfoStore(storageFolder);
    var globalSettingsStore = new GlobalSettingsStore(storageFolder);
    var pluginReferenceStore = new PluginReferenceStore(storageFolder);
    var rulesStore = new RulesStore(storageFolder);
    var activeRulesStore = new ActiveRulesStore(storageFolder);
    var qualityProfileStore = new QualityProfileStore(storageFolder);
    var serverProjectsStore = new ServerProjectsStore(storageFolder);
    var storageStatusStore = new StorageStatusStore(storageFolder);

    try {
      progress.setProgressAndCheckCancel("Checking server version and status", 0.1f);
      ServerInfo serverStatus = statusChecker.checkVersionAndStatus();

      progress.setProgressAndCheckCancel("Fetching list of code analyzers", 0.12f);
      List<SonarAnalyzer> analyzers = pluginListDownloader.downloadPluginList();
      serverInfoStore.store(serverStatus);

      progress.setProgressAndCheckCancel("Fetching global properties", 0.15f);
      var globalSettingsDownloader = new SettingsDownloader(serverApiHelper, globalSettingsStore);
      globalSettingsDownloader.fetchGlobalSettings();

      progress.setProgressAndCheckCancel("Fetching analyzers", 0.25f);
      var pluginReferenceDownloader = new PluginReferencesDownloader(serverApiHelper, pluginCache, connectedGlobalConfiguration,
        pluginReferenceStore);
      pluginReferenceDownloader.fetchPlugins(analyzers, progress.subProgress(0.25f, 0.4f, "Fetching code analyzers"));

      progress.setProgressAndCheckCancel("Fetching rules", 0.4f);
      var rulesDownloader = new RulesDownloader(serverApiHelper, connectedGlobalConfiguration, rulesStore, activeRulesStore);
      rulesDownloader.fetchRules(progress.subProgress(0.4f, 0.6f, "Fetching rules"));

      progress.setProgressAndCheckCancel("Fetching quality profiles", 0.6f);
      var qualityProfilesDownloader = new QualityProfilesDownloader(serverApiHelper, qualityProfileStore);
      qualityProfilesDownloader.fetchQualityProfiles();

      progress.setProgressAndCheckCancel("Fetching list of projects", 0.8f);
      var projectListDownloader = new ProjectListDownloader(serverApiHelper, serverProjectsStore);
      projectListDownloader.fetch(progress.subProgress(0.8f, 1.0f, "Fetching list of projects"));

      progress.setProgressAndCheckCancel("Finalizing...", 1.0f);

      progress.executeNonCancelableSection(() -> {
        var storageStatus = StorageStatus.newBuilder()
          .setStorageVersion(ProjectStoragePaths.STORAGE_VERSION)
          .setSonarlintCoreVersion(VersionUtils.getLibraryVersion())
          .setUpdateTimestamp(new Date().getTime())
          .build();
        storageStatusStore.store(storageStatus);

        serverStorage.replaceStorageWith(temp);
      });
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
