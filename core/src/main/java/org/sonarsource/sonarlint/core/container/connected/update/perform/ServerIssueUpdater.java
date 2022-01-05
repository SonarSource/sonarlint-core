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
import java.util.List;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

public class ServerIssueUpdater {
  private final ProjectStoragePaths projectStoragePaths;
  private final IssueDownloader issueDownloader;
  private final IssueStoreFactory issueStoreFactory;

  public ServerIssueUpdater(ProjectStoragePaths projectStoragePaths, IssueDownloader issueDownloader, IssueStoreFactory issueStoreFactory) {
    this.projectStoragePaths = projectStoragePaths;
    this.issueDownloader = issueDownloader;
    this.issueStoreFactory = issueStoreFactory;
  }

  public void update(ServerApiHelper serverApiHelper, String projectKey, Sonarlint.ProjectConfiguration projectConfiguration, boolean fetchTaintVulnerabilities,
    ProgressMonitor progress) {
    var target = projectStoragePaths.getServerIssuesPath(projectKey);
    var work = createTempDir(target);
    FileUtils.replaceDir(path -> updateServerIssues(serverApiHelper, projectKey, projectConfiguration, path, fetchTaintVulnerabilities, progress), target, work);
  }

  private static Path createTempDir(Path target) {
    try {
      return Files.createTempDirectory(target.getParent(), "sonarlint-issue-updater");
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create temp directory in " + target);
    }
  }

  public void updateServerIssues(ServerApiHelper serverApiHelper, String projectKey, Sonarlint.ProjectConfiguration projectConfiguration, Path path,
    boolean fetchTaintVulnerabilities, ProgressMonitor progress) {
    var issues = issueDownloader.download(serverApiHelper, projectKey, projectConfiguration, fetchTaintVulnerabilities, null, progress);
    issueStoreFactory.apply(path).save(issues);
  }

}
