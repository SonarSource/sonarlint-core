/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.util.function.Supplier;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;

public class ServerHotspotUpdater {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConnectionStorage storage;
  private final HotspotDownloader hotspotDownloader;

  public ServerHotspotUpdater(ConnectionStorage storage, HotspotDownloader hotspotDownloader) {
    this.storage = storage;
    this.hotspotDownloader = hotspotDownloader;
  }

  public void updateAll(HotspotApi hotspotApi, String projectKey, String branchName, Supplier<Version> serverVersionSupplier, ProgressMonitor progress) {
    if (hotspotApi.permitsTracking(serverVersionSupplier)) {
      var projectHotspots = hotspotApi.getAll(projectKey, branchName, progress);
      storage.project(projectKey).findings().replaceAllHotspotsOfBranch(branchName, projectHotspots);
    } else {
      LOG.info("Skip downloading hotspots from server, not supported");
    }
  }

  public void updateForFile(HotspotApi hotspotApi, ProjectBinding projectBinding, String ideFilePath, String branchName, Supplier<Version> serverVersionSupplier) {
    String serverFilePath = IssueStorePaths.idePathToServerPath(projectBinding, ideFilePath);
    if (serverFilePath == null) {
      return;
    }
    if (hotspotApi.permitsTracking(serverVersionSupplier)) {
      var projectKey = projectBinding.projectKey();
      var projectHotspots = hotspotApi.getFromFile(projectKey, serverFilePath, branchName);
      storage.project(projectKey).findings().replaceAllHotspotsOfFile(branchName, serverFilePath, projectHotspots);
    } else {
      LOG.info("Skip downloading hotspots for file, not supported");
    }
  }

  public void sync(HotspotApi hotspotApi, String projectKey, String branchName) {
    var lastSync = storage.project(projectKey).findings().getLastHotspotSyncTimestamp(branchName);
    var result = hotspotDownloader.downloadFromPull(hotspotApi, projectKey, branchName, lastSync);
    storage.project(projectKey).findings().mergeHotspots(branchName, result.getChangedHotspots(), result.getClosedHotspotKeys(), result.getQueryTimestamp());
  }
}
