/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2022 SonarSource SA
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
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueStore;

public class ServerIssueUpdater {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ServerIssueStore serverIssueStore;
  private final IssueDownloader issueDownloader;

  public ServerIssueUpdater(ServerIssueStore serverIssueStore, IssueDownloader issueDownloader) {
    this.serverIssueStore = serverIssueStore;
    this.issueDownloader = issueDownloader;
  }

  public void update(ServerApiHelper serverApiHelper, String projectKey, String branchName, boolean isSonarCloud, Version serverVersion) {
    List<ServerIssue> issues;
    if (!isSonarCloud && serverVersion.compareToIgnoreQualifier(IssueApi.MIN_SQ_VERSION_SUPPORTING_PULL) >= 0) {
      issues = issueDownloader.downloadFromPull(serverApiHelper, projectKey, branchName);
    } else {
      issues = issueDownloader.downloadFromBatch(serverApiHelper, projectKey, branchName);
    }
    serverIssueStore.replaceAllIssuesOfProject(projectKey, branchName, issues);
  }

  public void updateFileIssues(ServerApiHelper serverApiHelper, ProjectBinding projectBinding, String ideFilePath, @Nullable String branchName, boolean isSonarCloud,
    Version serverVersion, ProgressMonitor progress) {
    var fileKey = IssueStorePaths.idePathToFileKey(projectBinding, ideFilePath);
    if (fileKey == null) {
      return;
    }
    String serverFilePath = IssueStorePaths.idePathToSqPath(projectBinding, ideFilePath);
    if (isSonarCloud || serverVersion.compareToIgnoreQualifier(IssueApi.MIN_SQ_VERSION_SUPPORTING_PULL) < 0) {
      List<ServerIssue> issues = new ArrayList<>();
      try {
        issues.addAll(issueDownloader.downloadFromBatch(serverApiHelper, fileKey, branchName));
      } catch (Exception e) {
        // null as cause so that it doesn't get wrapped
        throw new DownloadException("Failed to update file issues: " + e.getMessage(), null);
      }
      serverIssueStore.replaceAllIssuesOfFile(projectBinding.projectKey(), branchName, serverFilePath, issues);
    } else {
      LOG.debug("Skip downloading file issues on SonarQube 9.5+");
    }
    List<ServerTaintIssue> taintIssues = new ArrayList<>();
    try {
      taintIssues.addAll(issueDownloader.downloadTaint(serverApiHelper, fileKey, branchName, progress));
    } catch (Exception e) {
      // null as cause so that it doesn't get wrapped
      throw new DownloadException("Failed to update file taint vulnerabilities: " + e.getMessage(), null);
    }
    serverIssueStore.replaceAllTaintOfFile(projectBinding.projectKey(), branchName, serverFilePath, taintIssues);
  }
}
