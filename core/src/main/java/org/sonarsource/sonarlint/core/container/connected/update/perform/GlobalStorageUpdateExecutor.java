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
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectListDownloader;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.container.storage.ServerInfoStore;
import org.sonarsource.sonarlint.core.container.storage.ServerProjectsStore;
import org.sonarsource.sonarlint.core.container.storage.ServerStorage;
import org.sonarsource.sonarlint.core.container.storage.StorageFolder;
import org.sonarsource.sonarlint.core.container.storage.StorageStatusStore;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.system.ServerInfo;
import org.sonarsource.sonarlint.core.serverapi.system.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class GlobalStorageUpdateExecutor {

  private final ServerStorage serverStorage;
  private final ServerApiHelper serverApiHelper;
  private final ServerVersionAndStatusChecker statusChecker;

  public GlobalStorageUpdateExecutor(ServerStorage serverStorage, ServerApiHelper serverApiHelper, ServerVersionAndStatusChecker statusChecker) {
    this.serverStorage = serverStorage;
    this.serverApiHelper = serverApiHelper;
    this.statusChecker = statusChecker;
  }

  public void update(ProgressMonitor progress) {
    Path temp;
    try {
      temp = Files.createTempDirectory("sonarlint-global-storage");
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create temp directory", e);
    }

    try {
      StorageFolder storageFolder = new StorageFolder.Default(temp);
      var serverInfoStore = new ServerInfoStore(storageFolder);
      var serverProjectsStore = new ServerProjectsStore(storageFolder);
      var storageStatusStore = new StorageStatusStore(storageFolder);

      progress.setProgressAndCheckCancel("Checking server version and status", 0.1f);
      ServerInfo serverStatus = statusChecker.checkVersionAndStatus();
      serverInfoStore.store(serverStatus);

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
    } finally {
      org.apache.commons.io.FileUtils.deleteQuietly(temp.toFile());
    }
  }
}
