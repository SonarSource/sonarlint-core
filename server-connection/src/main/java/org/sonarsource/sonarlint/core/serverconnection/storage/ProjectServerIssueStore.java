/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2023 SonarSource SA
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

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

public interface ProjectServerIssueStore {

  /**
   * Store issues for a branch by replacing existing ones.
   */
  void replaceAllIssuesOfBranch(String branchName, List<ServerIssue> issues);

  void replaceAllHotspotsOfBranch(String branchName, Collection<ServerHotspot> serverHotspots);

  void replaceAllHotspotsOfFile(String branchName, String serverFilePath, Collection<ServerHotspot> serverHotspots);

  /**
   * Store issues for a single file by replacing existing ones and moving issues if necessary.
   */
  void replaceAllIssuesOfFile(String branchName, String serverFilePath, List<ServerIssue> issues);

  /**
   * Merge provided issues to stored ones for the given project:
   *  - new issues are added
   *  - existing issues are updated
   *  - closed issues are removed from the store
   */
  void mergeIssues(String branchName, List<ServerIssue> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp);

  /**
   * Merge provided taint issues to stored ones for the given project:
   *  - new issues are added
   *  - existing issues are updated
   *  - closed issues are removed from the store
   */
  void mergeTaintIssues(String branchName, List<ServerTaintIssue> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp);

  /**
   * Return the timestamp of the last issue sync for a given branch.
   * @return empty if the issues of the branch have never been pulled
   */
  Optional<Instant> getLastIssueSyncTimestamp(String branchName);

  /**
   * Return the timestamp of the last taint issue sync for a given branch.
   * @return empty if the taint issues of the branch have never been pulled
   */
  Optional<Instant> getLastTaintSyncTimestamp(String branchName);

  /**
   * Load issues stored for specified file.
   *
   *
   * @param projectKey
   * @param sqFilePath the relative path to the base of project, in SonarQube
   * @return issues, possibly empty
   */
  List<ServerIssue> load(String branchName, String sqFilePath);

  /**
   * Store taint issues for a single file.
   * For filesystem-based implementations, watch out for:
   * - Too long paths
   * - Directories with too many files
   * - (Too deep paths?)
   */
  void replaceAllTaintOfFile(String branchName, String serverFilePath, List<ServerTaintIssue> taintIssues);

  /**
   * Load taint issues stored for specified file.
   *
   *
   * @param branchName
   * @param sqFilePath the relative path to the base of project, in SonarQube
   * @return issues, possibly empty
   */
  List<ServerTaintIssue> loadTaint(String branchName, String sqFilePath);

  /**
   * Load hotspots stored for specified file.
   *
   * @param serverFilePath the relative path to the base of project, from the server point of view
   * @return issues, possibly empty
   */
  Collection<ServerHotspot> loadHotspots(String branchName, String serverFilePath);

  /**
   * Load all taint issues stored for a branch.
   *
   *
   * @param branchName
   * @return issues, possibly empty
   */
  List<ServerTaintIssue> loadTaint(String branchName);

  /**
   * @param issueKey
   * @param issueUpdater
   * @return true if the issue with the corresponding key exists in the store and has been updated
   */
  boolean updateIssue(String issueKey, Consumer<ServerIssue> issueUpdater);

  void updateTaintIssue(String issueKey, Consumer<ServerTaintIssue> taintIssueUpdater);

  void insert(String branchName, ServerTaintIssue taintIssue);

  void deleteTaintIssue(String issueKeyToDelete);

  void close();
}
