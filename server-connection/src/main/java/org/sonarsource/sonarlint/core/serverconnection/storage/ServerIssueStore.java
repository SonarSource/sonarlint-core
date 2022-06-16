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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.util.List;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.serverconnection.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

public interface ServerIssueStore {

  /**
   * Store issues per file.
   * For filesystem-based implementations, watch out for:
   * - Too long paths
   * - Directories with too many files
   * - (Too deep paths?)
   */
  void replaceAllIssuesOfProject(String projectKey, String branchName, List<ServerIssue> issues);

  /**
   * Store issues for a single file.
   * For filesystem-based implementations, watch out for:
   * - Too long paths
   * - Directories with too many files
   * - (Too deep paths?)
   */
  void replaceAllIssuesOfFile(String projectKey, String branchName, String serverFilePath, List<ServerIssue> issues);

  /**
   * Load issues stored for specified file.
   *
   *
   * @param projectKey
   * @param sqFilePath the relative path to the base of project, in SonarQube
   * @return issues, possibly empty
   */
  List<ServerIssue> load(String projectKey, String branchName, String sqFilePath);

  /**
   * Store taint issues for a single file.
   * For filesystem-based implementations, watch out for:
   * - Too long paths
   * - Directories with too many files
   * - (Too deep paths?)
   */
  void replaceAllTaintOfFile(String projectKey, String branchName, String serverFilePath, List<ServerTaintIssue> taintIssues);

  /**
   * Load taint issues stored for specified file.
   *
   *
   * @param projectKey
   * @param branchName
   * @param sqFilePath the relative path to the base of project, in SonarQube
   * @return issues, possibly empty
   */
  List<ServerTaintIssue> loadTaint(String projectKey, String branchName, String sqFilePath);

  void updateIssue(String issueKey, Consumer<ServerIssue> issueConsumer);

  void close();
}
