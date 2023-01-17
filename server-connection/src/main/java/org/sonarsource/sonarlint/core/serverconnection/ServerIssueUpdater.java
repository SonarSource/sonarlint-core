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

import java.util.ArrayList;
import java.util.List;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueStoresManager;

public class ServerIssueUpdater {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ServerIssueStoresManager serverIssueStoresManager;
  private final IssueDownloader issueDownloader;
  private final TaintIssueDownloader taintIssueDownloader;

  public ServerIssueUpdater(ServerIssueStoresManager serverIssueStoresManager, IssueDownloader issueDownloader, TaintIssueDownloader taintIssueDownloader) {
    this.serverIssueStoresManager = serverIssueStoresManager;
    this.issueDownloader = issueDownloader;
    this.taintIssueDownloader = taintIssueDownloader;
  }

  public void update(ServerApi serverApi, String projectKey, String branchName, boolean isSonarCloud, Version serverVersion) {
    if (IssueApi.supportIssuePull(isSonarCloud, serverVersion)) {
      sync(serverApi, projectKey, branchName);
    } else {
      List<ServerIssue> issues = issueDownloader.downloadFromBatch(serverApi, projectKey, branchName);
      serverIssueStoresManager.get(projectKey).replaceAllIssuesOfBranch(branchName, issues);
    }
  }

  public void sync(ServerApi serverApi, String projectKey, String branchName) {
    var lastSync = serverIssueStoresManager.get(projectKey).getLastIssueSyncTimestamp(branchName);
    var result = issueDownloader.downloadFromPull(serverApi, projectKey, branchName, lastSync);
    serverIssueStoresManager.get(projectKey).mergeIssues(branchName, result.getChangedIssues(), result.getClosedIssueKeys(), result.getQueryTimestamp());
  }

  public void syncTaints(ServerApi serverApi, String projectKey, String branchName) {
    var serverIssueStore = serverIssueStoresManager.get(projectKey);
    var lastSync = serverIssueStore.getLastTaintSyncTimestamp(branchName);
    var result = taintIssueDownloader.downloadTaintFromPull(serverApi, projectKey, branchName, lastSync);
    serverIssueStore.mergeTaintIssues(branchName, result.getChangedTaintIssues(), result.getClosedIssueKeys(), result.getQueryTimestamp());
  }

  public void updateFileIssues(ServerApi serverApi, ProjectBinding projectBinding, String ideFilePath, String branchName, boolean isSonarCloud,
    Version serverVersion) {
    String serverFilePath = IssueStorePaths.idePathToServerPath(projectBinding, ideFilePath);
    if (serverFilePath == null) {
      return;
    }
    var fileKey = IssueStorePaths.componentKey(projectBinding, serverFilePath);
    if (!IssueApi.supportIssuePull(isSonarCloud, serverVersion)) {
      List<ServerIssue> issues = new ArrayList<>();
      try {
        issues.addAll(issueDownloader.downloadFromBatch(serverApi, fileKey, branchName));
      } catch (Exception e) {
        // null as cause so that it doesn't get wrapped
        throw new DownloadException("Failed to update file issues: " + e.getMessage(), null);
      }
      serverIssueStoresManager.get(projectBinding.projectKey()).replaceAllIssuesOfFile(branchName, serverFilePath, issues);
    } else {
      LOG.debug("Skip downloading file issues on SonarQube " + IssueApi.MIN_SQ_VERSION_SUPPORTING_PULL + "+");
    }
  }

  public void updateFileTaints(ServerApi serverApi, ProjectBinding projectBinding, String ideFilePath, String branchName, boolean isSonarCloud,
    Version serverVersion, ProgressMonitor progress) {
    String serverFilePath = IssueStorePaths.idePathToServerPath(projectBinding, ideFilePath);
    if (serverFilePath == null) {
      return;
    }
    var fileKey = IssueStorePaths.componentKey(projectBinding, serverFilePath);
    if (!IssueApi.supportIssuePull(isSonarCloud, serverVersion)) {
      List<ServerTaintIssue> taintIssues = new ArrayList<>();
      try {
        taintIssues.addAll(taintIssueDownloader.downloadTaintFromIssueSearch(serverApi, fileKey, branchName, progress));
      } catch (Exception e) {
        // null as cause so that it doesn't get wrapped
        throw new DownloadException("Failed to update file taint vulnerabilities: " + e.getMessage(), null);
      }
      serverIssueStoresManager.get(projectBinding.projectKey()).replaceAllTaintOfFile(branchName, serverFilePath, taintIssues);
    } else {
      LOG.debug("Skip downloading file taint issues on SonarQube " + IssueApi.MIN_SQ_VERSION_SUPPORTING_PULL + "+");
    }

  }
}
