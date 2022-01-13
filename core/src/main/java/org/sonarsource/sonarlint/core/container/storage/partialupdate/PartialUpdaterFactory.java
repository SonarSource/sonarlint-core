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

import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverapi.source.SourceApi;

public class PartialUpdaterFactory {
  private final ProjectStoragePaths projectStoragePaths;
  private final IssueStorePaths issueStorePaths;
  private final TempFolder tempFolder;

  public PartialUpdaterFactory(ProjectStoragePaths projectStoragePaths, IssueStorePaths issueStorePaths, TempFolder tempFolder) {
    this.projectStoragePaths = projectStoragePaths;
    this.issueStorePaths = issueStorePaths;
    this.tempFolder = tempFolder;
  }

  public PartialUpdater create(EndpointParams endpoint, HttpClient client) {
    var serverApiHelper = new ServerApiHelper(endpoint, client);
    var issueStoreFactory = new IssueStoreFactory();
    var downloader = new IssueDownloader(new IssueApi(serverApiHelper), new SourceApi(serverApiHelper), issueStorePaths);
    return new PartialUpdater(issueStoreFactory, downloader, projectStoragePaths, issueStorePaths, tempFolder);
  }
}
