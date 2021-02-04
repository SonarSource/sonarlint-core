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

import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectListDownloader;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

public class PartialUpdaterFactory {
  private final StoragePaths storagePaths;
  private final IssueStorePaths issueStorePaths;
  private final TempFolder tempFolder;

  public PartialUpdaterFactory(StoragePaths storagePaths, IssueStorePaths issueStorePaths, TempFolder tempFolder) {
    this.storagePaths = storagePaths;
    this.issueStorePaths = issueStorePaths;
    this.tempFolder = tempFolder;
  }

  public PartialUpdater create(EndpointParams endpoint, HttpClient client) {
    ServerApiHelper serverApiHelper = new ServerApiHelper(endpoint, client);
    IssueStoreFactory issueStoreFactory = new IssueStoreFactory();
    IssueDownloader downloader = new IssueDownloader(serverApiHelper, issueStorePaths);
    ProjectListDownloader projectListDownloader = new ProjectListDownloader(serverApiHelper);
    return new PartialUpdater(issueStoreFactory, downloader, storagePaths, projectListDownloader, issueStorePaths, tempFolder);
  }
}
