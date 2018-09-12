/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
import java.util.stream.Collectors;
import org.sonar.api.utils.TempFolder;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStoreUtils;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

public class ServerIssueUpdater {

  private final StoragePaths storagePaths;
  private final IssueDownloader issueDownloader;
  private final IssueStoreFactory issueStoreFactory;
  private final TempFolder tempFolder;
  private final IssueStoreUtils issueStoreUtils;

  public ServerIssueUpdater(StoragePaths storagePaths, IssueDownloader issueDownloader, IssueStoreFactory issueStoreFactory, TempFolder tempFolder,
    IssueStoreUtils issueStoreUtils) {
    this.storagePaths = storagePaths;
    this.issueDownloader = issueDownloader;
    this.issueStoreFactory = issueStoreFactory;
    this.tempFolder = tempFolder;
    this.issueStoreUtils = issueStoreUtils;
  }

  public void update(String projectKey) {
    Path work = tempFolder.newDir().toPath();
    Path target = storagePaths.getServerIssuesPath(projectKey);
    FileUtils.replaceDir(path -> updateServerIssues(projectKey, path), target, work);
  }

  public void updateServerIssues(String projectKey, Path path) {
    List<ScannerInput.ServerIssue> issues = issueDownloader.apply(projectKey);
    List<Sonarlint.ServerIssue> storageIssues = issues.stream()
      .map(issueStoreUtils::toStorageIssue)
      .collect(Collectors.toList());

    issueStoreFactory.apply(path).save(storageIssues);
  }

}
