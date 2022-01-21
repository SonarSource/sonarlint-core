/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.container.storage.partialupdate;

import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.container.connected.update.perform.ServerIssueUpdater;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

public class PartialUpdater {
  private final IssueStoreFactory issueStoreFactory;
  private final IssueDownloader downloader;
  private final IssueStorePaths issueStorePaths;
  private final ProjectStoragePaths projectStoragePaths;

  public PartialUpdater(IssueStoreFactory issueStoreFactory, IssueDownloader downloader,
    ProjectStoragePaths projectStoragePaths, IssueStorePaths issueStorePaths) {
    this.issueStoreFactory = issueStoreFactory;
    this.downloader = downloader;
    this.projectStoragePaths = projectStoragePaths;
    this.issueStorePaths = issueStorePaths;
  }

  public void updateFileIssues(ServerApiHelper serverApiHelper, ProjectBinding projectBinding, Sonarlint.ProjectConfiguration projectConfiguration, String ideFilePath,
    boolean fetchTaintVulnerabilities, @Nullable String branchName, ProgressMonitor progress) {
    var serverIssuesPath = projectStoragePaths.getServerIssuesPath(projectBinding.projectKey());
    var issueStore = issueStoreFactory.apply(serverIssuesPath);
    var fileKey = issueStorePaths.idePathToFileKey(projectConfiguration, projectBinding, ideFilePath);
    if (fileKey == null) {
      return;
    }
    List<ServerIssue> issues;
    try {
      issues = downloader.download(serverApiHelper, fileKey, projectConfiguration, fetchTaintVulnerabilities, branchName, progress);
    } catch (Exception e) {
      // null as cause so that it doesn't get wrapped
      throw new DownloadException("Failed to update file issues: " + e.getMessage(), null);
    }
    issueStore.save(issues);
  }

  public void updateFileIssues(ServerApiHelper serverApiHelper, String projectKey, Sonarlint.ProjectConfiguration projectConfiguration,
    boolean fetchTaintVulnerabilities, @Nullable String branchName, ProgressMonitor progress) {
    new ServerIssueUpdater(projectStoragePaths, downloader, issueStoreFactory).update(serverApiHelper, projectKey, projectConfiguration,
      fetchTaintVulnerabilities, branchName, progress);
  }
}
