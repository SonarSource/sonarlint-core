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
package testutils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectServerIssueStore;

import static java.util.Optional.ofNullable;

public class InMemoryIssueStore implements ProjectServerIssueStore {
  private final Map<String, Map<String, List<ServerIssue>>> issuesByFileByBranch = new HashMap<>();
  private final Map<String, Map<String, Collection<ServerHotspot>>> hotspotsByFileByBranch = new HashMap<>();
  private final Map<String, Instant> lastIssueSyncByBranch = new HashMap<>();
  private final Map<String, ServerIssue> issuesByKey = new HashMap<>();
  private final Map<String, Map<String, List<ServerTaintIssue>>> taintIssuesByFileByBranch = new HashMap<>();
  private final Map<String, Instant> lastTaintSyncByBranch = new HashMap<>();
  private final Map<String, ServerTaintIssue> taintIssuesByKey = new HashMap<>();

  @Override
  public void replaceAllIssuesOfFile(String branchName, String serverFilePath, List<ServerIssue> issues) {
    issuesByFileByBranch
      .computeIfAbsent(branchName, __ -> new HashMap<>())
      .put(serverFilePath, issues);
    issues.forEach(issue -> issuesByKey.put(issue.getKey(), issue));
  }

  @Override
  public void mergeIssues(String branchName, List<ServerIssue> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp) {
    var issuesToMergeByFilePath = issuesToMerge.stream().collect(Collectors.groupingBy(ServerIssue::getFilePath));
    // does not handle issue moving file (e.g. file renaming)
    issuesByFileByBranch
      .computeIfAbsent(branchName, __ -> new HashMap<>())
      .putAll(issuesToMergeByFilePath);
    issuesToMerge.forEach(issue -> issuesByKey.put(issue.getKey(), issue));
    closedIssueKeysToDelete.forEach(issuesByKey::remove);
    lastIssueSyncByBranch.put(branchName, syncTimestamp);
  }

  @Override
  public void mergeTaintIssues(String branchName, List<ServerTaintIssue> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp) {
    var issuesToMergeByFilePath = issuesToMerge.stream().collect(Collectors.groupingBy(ServerTaintIssue::getFilePath));
    // does not handle issue moving file (e.g. file renaming)
    taintIssuesByFileByBranch
      .computeIfAbsent(branchName, __ -> new HashMap<>())
      .putAll(issuesToMergeByFilePath);
    issuesToMerge.forEach(issue -> taintIssuesByKey.put(issue.getKey(), issue));
    closedIssueKeysToDelete.forEach(taintIssuesByKey::remove);
    lastTaintSyncByBranch.put(branchName, syncTimestamp);
  }

  @Override
  public Optional<Instant> getLastIssueSyncTimestamp(String branchName) {
    return ofNullable(lastIssueSyncByBranch.get(branchName));
  }

  @Override
  public Optional<Instant> getLastTaintSyncTimestamp(String branchName) {
    return ofNullable(lastTaintSyncByBranch.get(branchName));
  }

  @Override
  public void replaceAllIssuesOfBranch(String branchName, List<ServerIssue> issues) {
    issuesByFileByBranch.put(branchName, issues.stream().collect(Collectors.groupingBy(ServerIssue::getFilePath)));
    issues.forEach(issue -> issuesByKey.put(issue.getKey(), issue));
  }

  @Override
  public void replaceAllHotspotsOfBranch(String branchName, Collection<ServerHotspot> serverHotspots) {
    hotspotsByFileByBranch.put(branchName, serverHotspots.stream().collect(Collectors.groupingBy(ServerHotspot::getFilePath, Collectors.toCollection(ArrayList::new))));
  }

  @Override
  public void replaceAllHotspotsOfFile(String branchName, String serverFilePath, Collection<ServerHotspot> serverHotspots) {
    hotspotsByFileByBranch
      .computeIfAbsent(branchName, __ -> new HashMap<>())
      .put(serverFilePath, serverHotspots);
  }

  @Override
  public List<ServerIssue> load(String branchName, String sqFilePath) {
    return issuesByFileByBranch
      .getOrDefault(branchName, Map.of())
      .getOrDefault(sqFilePath, List.of());
  }

  @Override
  public void replaceAllTaintOfFile(String branchName, String filePath, List<ServerTaintIssue> issues) {
    taintIssuesByFileByBranch
      .computeIfAbsent(branchName, __ -> new HashMap<>())
      .put(filePath, issues);
  }

  @Override
  public List<ServerTaintIssue> loadTaint(String branchName, String sqFilePath) {
    return taintIssuesByFileByBranch
      .getOrDefault(branchName, Map.of())
      .getOrDefault(sqFilePath, List.of());
  }

  @Override
  public Collection<ServerHotspot> loadHotspots(String branchName, String serverFilePath) {
    return hotspotsByFileByBranch
      .getOrDefault(branchName, Map.of())
      .getOrDefault(serverFilePath, List.of());
  }

  public List<ServerTaintIssue> loadTaint(String branchName) {
    return taintIssuesByFileByBranch
      .getOrDefault(branchName, Map.of())
      .values()
      .stream()
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  @Override
  public boolean updateIssue(String issueKey, Consumer<ServerIssue> issueUpdater) {
    if (issuesByKey.containsKey(issueKey)) {
      issueUpdater.accept(issuesByKey.get(issueKey));
      return true;
    }
    return false;
  }

  @Override
  public void updateTaintIssue(String issueKey, Consumer<ServerTaintIssue> taintIssueUpdater) {
    taintIssuesByFileByBranch.forEach(
      (branchName, taintIssuesByFile) -> taintIssuesByFile.forEach((file, taintIssues) -> taintIssues.forEach(taintIssue -> {
        if (taintIssue.getKey().equals(issueKey)) {
          taintIssueUpdater.accept(taintIssue);
        }
      })));
  }

  @Override
  public void insert(String branchName, ServerTaintIssue taintIssue) {
    taintIssuesByFileByBranch
      .computeIfAbsent(branchName, __ -> new HashMap<>())
      .computeIfAbsent(taintIssue.getFilePath(), __ -> new ArrayList<>())
      .add(taintIssue);
  }

  @Override
  public void deleteTaintIssue(String issueKeyToDelete) {
    taintIssuesByFileByBranch.forEach(
      (branchName, taintIssuesByFile) -> taintIssuesByFile.forEach((file, taintIssues) -> taintIssues.removeIf(taintIssue -> issueKeyToDelete.equals(taintIssue.getKey()))));
  }

  @Override
  public void close() {
    // nothing to do
  }
}
