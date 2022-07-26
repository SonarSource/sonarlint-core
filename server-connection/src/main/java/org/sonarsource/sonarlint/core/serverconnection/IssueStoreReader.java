/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueStoresManager;

import static java.util.function.Predicate.not;

public class IssueStoreReader {
  private final ServerIssueStoresManager serverIssueStoresManager;

  public IssueStoreReader(ServerIssueStoresManager serverIssueStoresManager) {
    this.serverIssueStoresManager = serverIssueStoresManager;
  }

  public List<ServerIssue> getServerIssues(ProjectBinding projectBinding, String branchName, String ideFilePath) {
    var sqPath = IssueStorePaths.idePathToSqPath(projectBinding, ideFilePath);
    if (sqPath == null) {
      return Collections.emptyList();
    }
    var loadedIssues = serverIssueStoresManager.get(projectBinding.projectKey()).load(branchName, sqPath);
    loadedIssues.forEach(issue -> issue.setFilePath(ideFilePath));
    return loadedIssues;
  }

  public List<ServerTaintIssue> getServerTaintIssues(ProjectBinding projectBinding, String branchName, String ideFilePath) {
    var sqPath = IssueStorePaths.idePathToSqPath(projectBinding, ideFilePath);
    if (sqPath == null) {
      return Collections.emptyList();
    }
    var loadedIssues = serverIssueStoresManager.get(projectBinding.projectKey()).loadTaint(branchName, sqPath);
    loadedIssues = loadedIssues.stream().filter(not(ServerTaintIssue::isResolved)).collect(Collectors.toList());
    loadedIssues.forEach(issue -> issue.setFilePath(ideFilePath));
    return loadedIssues;
  }
}
