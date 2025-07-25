/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource SA
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

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerFinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

public interface ProjectServerIssueStore {
  boolean wasEverUpdated();

  /**
   * Store issues for a branch by replacing existing ones.
   */
  void replaceAllIssuesOfBranch(String branchName, List<ServerIssue<?>> issues);

  void replaceAllHotspotsOfBranch(String branchName, Collection<ServerHotspot> serverHotspots);

  void replaceAllHotspotsOfFile(String branchName, Path serverFilePath, Collection<ServerHotspot> serverHotspots);

  /**
   * Update the resolution status of a hotspot by its key.
   * @return true if the hotspot with the given key was found, else false
   */
  boolean changeHotspotStatus(String hotspotKey, HotspotReviewStatus newStatus);

  /**
   * Store issues for a single file by replacing existing ones and moving issues if necessary.
   */
  void replaceAllIssuesOfFile(String branchName, Path serverFilePath, List<ServerIssue<?>> issues);

  /**
   * Merge provided issues to stored ones for the given project:
   *  - new issues are added
   *  - existing issues are updated
   *  - closed issues are removed from the store
   */
  void mergeIssues(String branchName, List<ServerIssue<?>> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp, Set<SonarLanguage> enabledLanguages);

  /**
   * Merge provided taint issues to stored ones for the given project:
   *  - new issues are added
   *  - existing issues are updated
   *  - closed issues are removed from the store
   */
  void mergeTaintIssues(String branchName, List<ServerTaintIssue> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp, Set<SonarLanguage> enabledLanguages);

  /**
   * Merge provided hotspots to stored ones for the given project:
   *  - new hotspots are added
   *  - existing hotspots are updated
   *  - closed hotspots are removed from the store
   */
  void mergeHotspots(String branchName, List<ServerHotspot> hotspotsToMerge, Set<String> closedHotspotKeysToDelete, Instant syncTimestamp, Set<SonarLanguage> enabledLanguages);

  /**
   * Return the timestamp of the last issue sync for a given branch.
   * @return empty if the issues of the branch have never been pulled
   */
  Optional<Instant> getLastIssueSyncTimestamp(String branchName);

  /**
   * Return the last enabled languages of the last issue sync for a given branch.
   *
   * @return empty if the issues of the branch have never been pulled
   */
  Set<SonarLanguage> getLastIssueEnabledLanguages(String branchName);

  /**
   * Return the last enabled languages of the last taint sync for a given branch.
   * @return empty if the taints of the branch have never been pulled
   */
  Set<SonarLanguage> getLastTaintEnabledLanguages(String branchName);

  /**
   * Return the last enabled languages of the last hotspot sync for a given branch.
   * @return empty if the hotspots of the branch have never been pulled
   */
  Set<SonarLanguage> getLastHotspotEnabledLanguages(String branchName);

  /**
   * Return the timestamp of the last taint issue sync for a given branch.
   * @return empty if the taint issues of the branch have never been pulled
   */
  Optional<Instant> getLastTaintSyncTimestamp(String branchName);

  /**
   * Return the timestamp of the last hotspot sync for a given branch.
   * @return empty if the hotspots of the branch have never been pulled
   */
  Optional<Instant> getLastHotspotSyncTimestamp(String branchName);

  /**
   * Load issues stored for specified file.
   *
   * @param branchName
   * @param sqFilePath the relative path to the base of project, in SonarQube
   * @return issues, possibly empty
   */
  List<ServerIssue<?>> load(String branchName, Path sqFilePath);

  /**
   * Store taint issues for a branch.
   */
  void replaceAllTaintsOfBranch(String branchName, List<ServerTaintIssue> taintIssues);

  /**
   * Load hotspots stored for specified file.
   *
   * @param serverFilePath the relative path to the base of project, from the server point of view
   * @return issues, possibly empty
   */
  Collection<ServerHotspot> loadHotspots(String branchName, Path serverFilePath);

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
  boolean updateIssue(String issueKey, Consumer<ServerIssue<?>> issueUpdater);

  /**
   * Retrieve an issue from the store
   * @param issueKey
   * @return the server issue if found, null otherwise
   */
  ServerIssue<?> getIssue(String issueKey);

  /**
   * Retrieve a hotspot from the store
   * @param hotspotKey
   * @return the hotspot if found, null otherwise
   */
  ServerHotspot getHotspot(String hotspotKey);

  /**
   * Set the resolution status of an Issue (by its key).
   */
  Optional<ServerFinding> updateIssueResolutionStatus(String issueKey, boolean isTaintIssue, boolean isResolved);

  /**
   * @return the updated issue if found, else empty
   */
  Optional<ServerTaintIssue> updateTaintIssueBySonarServerKey(String sonarServerKey, Consumer<ServerTaintIssue> taintIssueUpdater);

  void insert(String branchName, ServerTaintIssue taintIssue);

  void insert(String branchName, ServerHotspot hotspot);

  /**
   * @return the id of the deleted taint issue if it was found
   */
  Optional<UUID> deleteTaintIssueBySonarServerKey(String sonarServerKeyToDelete);

  void deleteHotspot(String hotspotKey);

  void close();

  void updateHotspot(String hotspotKey, Consumer<ServerHotspot> hotspotUpdater);

  boolean containsIssue(String issueKey);

  /**
   * Store dependency risks for a branch by replacing existing ones.
   */
  void replaceAllDependencyRisksOfBranch(String branchName, List<ServerDependencyRisk> serverDependencyRisks);

  /**
   * Load all dependency risks stored for a branch.
   */
  List<ServerDependencyRisk> loadDependencyRisks(String branchName);

  void updateDependencyRiskStatus(UUID key, ServerDependencyRisk.Status newStatus);
}
