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

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueStore;

public class PartialUpdater {
  private final Function<Path, ServerIssueStore> issueStoreFactory;
  private final IssueDownloader downloader;
  private final ProjectStoragePaths projectStoragePaths;

  public PartialUpdater(Function<Path, ServerIssueStore> issueStoreFactory, IssueDownloader downloader, ProjectStoragePaths projectStoragePaths) {
    this.issueStoreFactory = issueStoreFactory;
    this.downloader = downloader;
    this.projectStoragePaths = projectStoragePaths;
  }

  public void updateFileIssues(ServerApiHelper serverApiHelper, ProjectBinding projectBinding, String ideFilePath, boolean fetchTaintVulnerabilities, @Nullable String branchName,
    ProgressMonitor progress) {
    var serverIssuesPath = projectStoragePaths.getServerIssuesPath(projectBinding.projectKey());
    var issueStore = issueStoreFactory.apply(serverIssuesPath);
    var fileKey = IssueStorePaths.idePathToFileKey(projectBinding, ideFilePath);
    if (fileKey == null) {
      return;
    }
    List<ServerIssue> issues;
    try {
      issues = downloader.download(serverApiHelper, fileKey, fetchTaintVulnerabilities, branchName, progress);
    } catch (Exception e) {
      // null as cause so that it doesn't get wrapped
      throw new DownloadException("Failed to update file issues: " + e.getMessage(), null);
    }
    issueStore.save(issues);
  }

  public void updateFileIssues(ServerApiHelper serverApiHelper, String projectKey, boolean fetchTaintVulnerabilities, @Nullable String branchName, ProgressMonitor progress) {
    new ServerIssueUpdater(projectStoragePaths, downloader, issueStoreFactory).update(serverApiHelper, projectKey, fetchTaintVulnerabilities, branchName, progress);
  }
}
