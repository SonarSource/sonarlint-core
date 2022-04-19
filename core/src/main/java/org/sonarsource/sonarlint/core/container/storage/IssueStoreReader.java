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
package org.sonarsource.sonarlint.core.container.storage;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;

public class IssueStoreReader {
  private final IssueStoreFactory issueStoreFactory;
  private final ProjectStoragePaths projectStoragePaths;
  private final IssueStorePaths issueStorePaths;

  public IssueStoreReader(IssueStoreFactory issueStoreFactory, IssueStorePaths issueStorePaths, ProjectStoragePaths projectStoragePaths) {
    this.issueStoreFactory = issueStoreFactory;
    this.issueStorePaths = issueStorePaths;
    this.projectStoragePaths = projectStoragePaths;
  }

  public List<ServerIssue> getServerIssues(ProjectBinding projectBinding, String ideFilePath) {
    var sqPath = issueStorePaths.idePathToSqPath(projectBinding, ideFilePath);
    if (sqPath == null) {
      return Collections.emptyList();
    }
    var serverIssuesPath = projectStoragePaths.getServerIssuesPath(projectBinding.projectKey());
    var issueStore = issueStoreFactory.apply(serverIssuesPath);

    var loadedIssues = issueStore.load(sqPath);

    return loadedIssues.stream()
      .map(pbIssue -> IssueStorePaths.toApiIssue(pbIssue, ideFilePath))
      .collect(Collectors.toList());
  }
}
