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
package org.sonarsource.sonarlint.core.container.storage.partialupdate;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonar.api.utils.TempFolder;
import org.sonar.scanner.protocol.input.ScannerInput.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.container.connected.IssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloaderImpl;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStoreUtils;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.perform.ServerIssueUpdater;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class PartialUpdater {
  private final IssueStoreFactory issueStoreFactory;
  private final IssueDownloader downloader;
  private final StorageReader storageReader;
  private final ProjectListDownloader projectListDownloader;
  private final Function<String, IssueStoreUtils> issueStoreUtilsFactory;
  private final StoragePaths storagePaths;

  public PartialUpdater(IssueStoreFactory issueStoreFactory, IssueDownloader downloader, StorageReader storageReader,
    StoragePaths storagePaths, ProjectListDownloader projectListDownloader, Function<String, IssueStoreUtils> issueStoreUtilsFactory) {
    this.issueStoreFactory = issueStoreFactory;
    this.downloader = downloader;
    this.storageReader = storageReader;
    this.storagePaths = storagePaths;
    this.projectListDownloader = projectListDownloader;
    this.issueStoreUtilsFactory = issueStoreUtilsFactory;
  }

  public static PartialUpdater create(StorageReader storageReader, StoragePaths storagePaths, ServerConfiguration serverConfig,
    Function<String, IssueStoreUtils> issueStoreUtilsFactory) {
    SonarLintWsClient client = new SonarLintWsClient(serverConfig);
    IssueStoreFactory issueStoreFactory = new IssueStoreFactory();
    IssueDownloader downloader = new IssueDownloaderImpl(client);
    ProjectListDownloader projectListDownloader = new ProjectListDownloader(client);
    return new PartialUpdater(issueStoreFactory, downloader, storageReader, storagePaths, projectListDownloader, issueStoreUtilsFactory);
  }

  public void updateFileIssues(String projectKey, String localFilePath) {
    Path serverIssuesPath = storagePaths.getServerIssuesPath(projectKey);
    IssueStore issueStore = issueStoreFactory.apply(serverIssuesPath);
    IssueStoreUtils issueStoreUtils = issueStoreUtilsFactory.apply(projectKey);
    String fileKey = issueStoreUtils.localPathToFileKey(projectKey, localFilePath);
    List<ServerIssue> issues;
    try {
      issues = downloader.apply(fileKey);
    } catch (Exception e) {
      // null as cause so that it doesn't get wrapped
      throw new DownloadException("Failed to update file issues: " + e.getMessage(), null);
    }
    List<Sonarlint.ServerIssue> storageIssues = issues.stream()
      .map(issueStoreUtils::toStorageIssue)
      .collect(Collectors.toList());
    issueStore.save(storageIssues);
  }

  public void updateFileIssues(String projectKey, TempFolder tempFolder) {
    new ServerIssueUpdater(storagePaths, downloader, issueStoreFactory, tempFolder, issueStoreUtilsFactory.apply(projectKey))
      .update(projectKey);
  }

  public void updateModuleList(ProgressWrapper progress) {
    try {
      projectListDownloader.fetchModulesListTo(storagePaths.getGlobalStorageRoot(), storageReader.readServerInfos().getVersion(), progress);
    } catch (Exception e) {
      // null as cause so that it doesn't get wrapped
      throw new DownloadException("Failed to update module list: " + e.getMessage(), null);
    }
  }
}
