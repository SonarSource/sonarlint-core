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

import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloaderImpl;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectListDownloader;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;

public class PartialUpdaterFactory {
  private final StorageReader storageReader;
  private final StoragePaths storagePaths;
  private final IssueStorePaths issueStorePaths;
  private final TempFolder tempFolder;

  public PartialUpdaterFactory(StorageReader storageReader, StoragePaths storagePaths, IssueStorePaths issueStorePaths, TempFolder tempFolder) {
    this.storageReader = storageReader;
    this.storagePaths = storagePaths;
    this.issueStorePaths = issueStorePaths;
    this.tempFolder = tempFolder;
  }

  public PartialUpdater create(ServerConfiguration serverConfig) {
    SonarLintWsClient client = new SonarLintWsClient(serverConfig);
    IssueStoreFactory issueStoreFactory = new IssueStoreFactory();
    IssueDownloader downloader = new IssueDownloaderImpl(client);
    ProjectListDownloader projectListDownloader = new ProjectListDownloader(client);
    return new PartialUpdater(issueStoreFactory, downloader, storageReader, storagePaths, projectListDownloader,
      issueStorePaths, tempFolder);
  }
}
