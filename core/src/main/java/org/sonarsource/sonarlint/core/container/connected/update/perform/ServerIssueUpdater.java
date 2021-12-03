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

import java.nio.file.Path;
import java.util.List;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class ServerIssueUpdater {
  private final ProjectStoragePaths projectStoragePaths;
  private final IssueDownloader issueDownloader;
  private final IssueStoreFactory issueStoreFactory;
  private final TempFolder tempFolder;

  public ServerIssueUpdater(ProjectStoragePaths projectStoragePaths, IssueDownloader issueDownloader, IssueStoreFactory issueStoreFactory, TempFolder tempFolder) {
    this.projectStoragePaths = projectStoragePaths;
    this.issueDownloader = issueDownloader;
    this.issueStoreFactory = issueStoreFactory;
    this.tempFolder = tempFolder;
  }

  public void update(String projectKey, Sonarlint.ProjectConfiguration projectConfiguration, boolean fetchTaintVulnerabilities, ProgressWrapper progress) {
    Path work = tempFolder.newDir().toPath();
    Path target = projectStoragePaths.getServerIssuesPath(projectKey);
    FileUtils.replaceDir(path -> updateServerIssues(projectKey, projectConfiguration, path, fetchTaintVulnerabilities, progress), target, work);
  }

  public void updateServerIssues(String projectKey, Sonarlint.ProjectConfiguration projectConfiguration, Path path, boolean fetchTaintVulnerabilities, ProgressWrapper progress) {
    List<Sonarlint.ServerIssue> issues = issueDownloader.download(projectKey, projectConfiguration, fetchTaintVulnerabilities, null, progress);
    issueStoreFactory.apply(path).save(issues);
  }

}
