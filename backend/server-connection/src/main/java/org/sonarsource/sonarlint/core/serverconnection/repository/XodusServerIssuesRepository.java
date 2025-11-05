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
package org.sonarsource.sonarlint.core.serverconnection.repository;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerFinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectServerIssueStore;
import org.sonarsource.sonarlint.core.serverconnection.storage.XodusServerIssueStore;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

public class XodusServerIssuesRepository implements ServerIssuesRepository {

  private final Path storageRoot;
  private final Path workDir;
  private final Map<String, Map<String, ProjectServerIssueStore>> serverIssueStoreByConnectionAndProject = new ConcurrentHashMap<>();

  public XodusServerIssuesRepository(Path storageRoot, Path workDir) {
    this.storageRoot = storageRoot;
    this.workDir = workDir;
  }

  private ProjectServerIssueStore getStore(String connectionId, String projectKey) {
    var storesByProject = serverIssueStoreByConnectionAndProject.computeIfAbsent(connectionId, k -> new ConcurrentHashMap<>());
    return storesByProject.computeIfAbsent(projectKey, k -> {
      var projectsStorageBaseDir = storageRoot.resolve(encodeForFs(connectionId)).resolve("projects");
      var xodusBackupPath = projectsStorageBaseDir.resolve(encodeForFs(projectKey)).resolve("issues");
      try {
        return new XodusServerIssueStore(xodusBackupPath, workDir);
      } catch (IOException e) {
        throw new IllegalStateException("Unable to create server issue database", e);
      }
    });
  }

  @Override
  public boolean wasEverUpdated(String connectionId, String projectKey) {
    return getStore(connectionId, projectKey).wasEverUpdated();
  }

  @Override
  public void replaceAllIssuesOfBranch(String connectionId, String projectKey, String branchName, List<ServerIssue<?>> issues) {
    getStore(connectionId, projectKey).replaceAllIssuesOfBranch(branchName, issues);
  }

  @Override
  public void replaceAllHotspotsOfBranch(String connectionId, String projectKey, String branchName, Collection<ServerHotspot> serverHotspots) {
    getStore(connectionId, projectKey).replaceAllHotspotsOfBranch(branchName, serverHotspots);
  }

  @Override
  public void replaceAllHotspotsOfFile(String connectionId, String projectKey, String branchName, Path serverFilePath, Collection<ServerHotspot> serverHotspots) {
    getStore(connectionId, projectKey).replaceAllHotspotsOfFile(branchName, serverFilePath, serverHotspots);
  }

  @Override
  public boolean changeHotspotStatus(String connectionId, String projectKey, String hotspotKey, HotspotReviewStatus newStatus) {
    return getStore(connectionId, projectKey).changeHotspotStatus(hotspotKey, newStatus);
  }

  @Override
  public void replaceAllIssuesOfFile(String connectionId, String projectKey, String branchName, Path serverFilePath, List<ServerIssue<?>> issues) {
    getStore(connectionId, projectKey).replaceAllIssuesOfFile(branchName, serverFilePath, issues);
  }

  @Override
  public void mergeIssues(String connectionId, String projectKey, String branchName, List<ServerIssue<?>> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp, Set<SonarLanguage> enabledLanguages) {
    getStore(connectionId, projectKey).mergeIssues(branchName, issuesToMerge, closedIssueKeysToDelete, syncTimestamp, enabledLanguages);
  }

  @Override
  public void mergeTaintIssues(String connectionId, String projectKey, String branchName, List<ServerTaintIssue> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp, Set<SonarLanguage> enabledLanguages) {
    getStore(connectionId, projectKey).mergeTaintIssues(branchName, issuesToMerge, closedIssueKeysToDelete, syncTimestamp, enabledLanguages);
  }

  @Override
  public void mergeHotspots(String connectionId, String projectKey, String branchName, List<ServerHotspot> hotspotsToMerge, Set<String> closedHotspotKeysToDelete, Instant syncTimestamp, Set<SonarLanguage> enabledLanguages) {
    getStore(connectionId, projectKey).mergeHotspots(branchName, hotspotsToMerge, closedHotspotKeysToDelete, syncTimestamp, enabledLanguages);
  }

  @Override
  public Optional<Instant> getLastIssueSyncTimestamp(String connectionId, String projectKey, String branchName) {
    return getStore(connectionId, projectKey).getLastIssueSyncTimestamp(branchName);
  }

  @Override
  public Set<SonarLanguage> getLastIssueEnabledLanguages(String connectionId, String projectKey, String branchName) {
    return getStore(connectionId, projectKey).getLastIssueEnabledLanguages(branchName);
  }

  @Override
  public Set<SonarLanguage> getLastTaintEnabledLanguages(String connectionId, String projectKey, String branchName) {
    return getStore(connectionId, projectKey).getLastTaintEnabledLanguages(branchName);
  }

  @Override
  public Set<SonarLanguage> getLastHotspotEnabledLanguages(String connectionId, String projectKey, String branchName) {
    return getStore(connectionId, projectKey).getLastHotspotEnabledLanguages(branchName);
  }

  @Override
  public Optional<Instant> getLastTaintSyncTimestamp(String connectionId, String projectKey, String branchName) {
    return getStore(connectionId, projectKey).getLastTaintSyncTimestamp(branchName);
  }

  @Override
  public Optional<Instant> getLastHotspotSyncTimestamp(String connectionId, String projectKey, String branchName) {
    return getStore(connectionId, projectKey).getLastHotspotSyncTimestamp(branchName);
  }

  @Override
  public List<ServerIssue<?>> load(String connectionId, String projectKey, String branchName, Path sqFilePath) {
    return getStore(connectionId, projectKey).load(branchName, sqFilePath);
  }

  @Override
  public void replaceAllTaintsOfBranch(String connectionId, String projectKey, String branchName, List<ServerTaintIssue> taintIssues) {
    getStore(connectionId, projectKey).replaceAllTaintsOfBranch(branchName, taintIssues);
  }

  @Override
  public Collection<ServerHotspot> loadHotspots(String connectionId, String projectKey, String branchName, Path serverFilePath) {
    return getStore(connectionId, projectKey).loadHotspots(branchName, serverFilePath);
  }

  @Override
  public List<ServerTaintIssue> loadTaint(String connectionId, String projectKey, String branchName) {
    return getStore(connectionId, projectKey).loadTaint(branchName);
  }

  @Override
  public boolean updateIssue(String connectionId, String projectKey, String issueKey, Consumer<ServerIssue<?>> issueUpdater) {
    return getStore(connectionId, projectKey).updateIssue(issueKey, issueUpdater);
  }

  @Override
  public ServerIssue<?> getIssue(String connectionId, String projectKey, String issueKey) {
    return getStore(connectionId, projectKey).getIssue(issueKey);
  }

  @Override
  public ServerHotspot getHotspot(String connectionId, String projectKey, String hotspotKey) {
    return getStore(connectionId, projectKey).getHotspot(hotspotKey);
  }

  @Override
  public Optional<ServerFinding> updateIssueResolutionStatus(String connectionId, String projectKey, String issueKey, boolean isTaintIssue, boolean isResolved) {
    return getStore(connectionId, projectKey).updateIssueResolutionStatus(issueKey, isTaintIssue, isResolved);
  }

  @Override
  public Optional<ServerTaintIssue> updateTaintIssueBySonarServerKey(String connectionId, String projectKey, String sonarServerKey, Consumer<ServerTaintIssue> taintIssueUpdater) {
    return getStore(connectionId, projectKey).updateTaintIssueBySonarServerKey(sonarServerKey, taintIssueUpdater);
  }

  @Override
  public void insert(String connectionId, String projectKey, String branchName, ServerTaintIssue taintIssue) {
    getStore(connectionId, projectKey).insert(branchName, taintIssue);
  }

  @Override
  public void insert(String connectionId, String projectKey, String branchName, ServerHotspot hotspot) {
    getStore(connectionId, projectKey).insert(branchName, hotspot);
  }

  @Override
  public Optional<UUID> deleteTaintIssueBySonarServerKey(String connectionId, String projectKey, String sonarServerKeyToDelete) {
    return getStore(connectionId, projectKey).deleteTaintIssueBySonarServerKey(sonarServerKeyToDelete);
  }

  @Override
  public void deleteHotspot(String connectionId, String projectKey, String hotspotKey) {
    getStore(connectionId, projectKey).deleteHotspot(hotspotKey);
  }

  @Override
  public void updateHotspot(String connectionId, String projectKey, String hotspotKey, Consumer<ServerHotspot> hotspotUpdater) {
    getStore(connectionId, projectKey).updateHotspot(hotspotKey, hotspotUpdater);
  }

  @Override
  public boolean containsIssue(String connectionId, String projectKey, String issueKey) {
    return getStore(connectionId, projectKey).containsIssue(issueKey);
  }

  @Override
  public void replaceAllDependencyRisksOfBranch(String connectionId, String projectKey, String branchName, List<ServerDependencyRisk> serverDependencyRisks) {
    getStore(connectionId, projectKey).replaceAllDependencyRisksOfBranch(branchName, serverDependencyRisks);
  }

  @Override
  public List<ServerDependencyRisk> loadDependencyRisks(String connectionId, String projectKey, String branchName) {
    return getStore(connectionId, projectKey).loadDependencyRisks(branchName);
  }

  @Override
  public void updateDependencyRiskStatus(String connectionId, String projectKey, UUID key, ServerDependencyRisk.Status newStatus, List<ServerDependencyRisk.Transition> transitions) {
    getStore(connectionId, projectKey).updateDependencyRiskStatus(key, newStatus, transitions);
  }

  @Override
  public void close(String connectionId) {
    var storesByProject = serverIssueStoreByConnectionAndProject.remove(connectionId);
    if (storesByProject != null) {
      storesByProject.values().forEach(ProjectServerIssueStore::close);
      storesByProject.clear();
    }
  }
}

