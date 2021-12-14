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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.connected.update.PluginListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.PluginReferencesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectListDownloader;
import org.sonarsource.sonarlint.core.container.storage.PluginReferenceStore;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
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
  private final ServerApiHelper serverApiHelper;
  private final ServerVersionAndStatusChecker statusChecker;
  private final PluginListDownloader pluginListDownloader;

  public GlobalStorageUpdateExecutor(ServerStorage serverStorage, ServerApiHelper serverApiHelper, ServerVersionAndStatusChecker statusChecker,
    PluginCache pluginCache, PluginListDownloader pluginListDownloader, ConnectedGlobalConfiguration connectedGlobalConfiguration) {
    this.serverStorage = serverStorage;
    this.serverApiHelper = serverApiHelper;
    this.statusChecker = statusChecker;
    this.pluginCache = pluginCache;
    this.pluginListDownloader = pluginListDownloader;
    this.connectedGlobalConfiguration = connectedGlobalConfiguration;
  }

  public List<SonarAnalyzer> update(ProgressMonitor progress) {
    Path temp;
    try {
      temp = Files.createTempDirectory("sonarlint-global-storage");
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create temp directory", e);
    }
    try {
      StorageFolder storageFolder = new StorageFolder.Default(temp);
      var serverInfoStore = new ServerInfoStore(storageFolder);
      var pluginReferenceStore = new PluginReferenceStore(storageFolder);
      var serverProjectsStore = new ServerProjectsStore(storageFolder);
      var storageStatusStore = new StorageStatusStore(storageFolder);

      progress.setProgressAndCheckCancel("Checking server version and status", 0.1f);
      ServerInfo serverStatus = statusChecker.checkVersionAndStatus();

      progress.setProgressAndCheckCancel("Fetching list of code analyzers", 0.12f);
      List<SonarAnalyzer> analyzers = pluginListDownloader.downloadPluginList();
      serverInfoStore.store(serverStatus);

      progress.setProgressAndCheckCancel("Fetching analyzers", 0.25f);
      var pluginReferenceDownloader = new PluginReferencesDownloader(serverApiHelper, pluginCache, connectedGlobalConfiguration,
        pluginReferenceStore);
      pluginReferenceDownloader.fetchPlugins(analyzers, progress.subProgress(0.25f, 0.8f, "Fetching code analyzers"));

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
    } finally {
      org.apache.commons.io.FileUtils.deleteQuietly(temp.toFile());
    }
  }
}
