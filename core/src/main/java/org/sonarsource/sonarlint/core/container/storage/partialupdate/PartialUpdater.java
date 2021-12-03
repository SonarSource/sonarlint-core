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
package org.sonarsource.sonarlint.core.container.storage.partialupdate;

import java.nio.file.Path;
import java.util.List;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.container.connected.IssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.container.connected.update.perform.ServerIssueUpdater;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class PartialUpdater {
  private final IssueStoreFactory issueStoreFactory;
  private final IssueDownloader downloader;
  private final IssueStorePaths issueStorePaths;
  private final TempFolder tempFolder;
  private final ProjectStoragePaths projectStoragePaths;

  public PartialUpdater(IssueStoreFactory issueStoreFactory, IssueDownloader downloader,
    ProjectStoragePaths projectStoragePaths, IssueStorePaths issueStorePaths, TempFolder tempFolder) {
    this.issueStoreFactory = issueStoreFactory;
    this.downloader = downloader;
    this.projectStoragePaths = projectStoragePaths;
    this.issueStorePaths = issueStorePaths;
    this.tempFolder = tempFolder;
  }

  public void updateFileIssues(ProjectBinding projectBinding, Sonarlint.ProjectConfiguration projectConfiguration, String ideFilePath, boolean fetchTaintVulnerabilities,
    ProgressWrapper progress) {
    Path serverIssuesPath = projectStoragePaths.getServerIssuesPath(projectBinding.projectKey());
    IssueStore issueStore = issueStoreFactory.apply(serverIssuesPath);
    String fileKey = issueStorePaths.idePathToFileKey(projectConfiguration, projectBinding, ideFilePath);
    if (fileKey == null) {
      return;
    }
    List<ServerIssue> issues;
    try {
      issues = downloader.download(fileKey, projectConfiguration, fetchTaintVulnerabilities, null, progress);
    } catch (Exception e) {
      // null as cause so that it doesn't get wrapped
      throw new DownloadException("Failed to update file issues: " + e.getMessage(), null);
    }
    issueStore.save(issues);
  }

  public void updateFileIssues(String projectKey, Sonarlint.ProjectConfiguration projectConfiguration, boolean fetchTaintVulnerabilities, ProgressWrapper progress) {
    new ServerIssueUpdater(projectStoragePaths, downloader, issueStoreFactory, tempFolder).update(projectKey, projectConfiguration, fetchTaintVulnerabilities, progress);
  }
}
