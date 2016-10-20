/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.storage.incremental;

import java.nio.file.Path;
import java.util.Iterator;

import org.sonar.scanner.protocol.input.ScannerInput.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.container.connected.IssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloaderImpl;
import org.sonarsource.sonarlint.core.container.storage.IssueStoreReader;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;

public class PartialUpdater {
  private final IssueStoreFactory issueStoreFactory;
  private final IssueDownloader downloader;
  private final StorageManager storageManager;
  private final IssueStoreReader issueStoreReader;

  public PartialUpdater(IssueStoreFactory issueStoreFactory, IssueDownloader downloader, StorageManager storageManager, IssueStoreReader issueStoreReader) {
    this.issueStoreFactory = issueStoreFactory;
    this.downloader = downloader;
    this.storageManager = storageManager;
    this.issueStoreReader = issueStoreReader;
  }

  public static PartialUpdater create(StorageManager storageManager, ServerConfiguration serverConfig, IssueStoreReader issueStoreReader) {
    SonarLintWsClient client = new SonarLintWsClient(serverConfig);
    IssueStoreFactory issueStoreFactory = new IssueStoreFactory();
    IssueDownloader downloader = new IssueDownloaderImpl(client);

    return new PartialUpdater(issueStoreFactory, downloader, storageManager, issueStoreReader);
  }

  public void updateFileIssues(String moduleKey, String filePath) {
    Path serverIssuesPath = storageManager.getServerIssuesPath(moduleKey);
    IssueStore issueStore = issueStoreFactory.apply(serverIssuesPath);
    String fileKey = issueStoreReader.getFileKey(moduleKey, filePath);
    Iterator<ServerIssue> issues;
    try {
      issues = downloader.apply(fileKey);
    } catch (Exception e) {
      // null as cause so that it doesn't get wrapped
      throw new DownloadException("Failed to update file issues" + e.getMessage(), null);
    }
    issueStore.save(issues);
  }
}
