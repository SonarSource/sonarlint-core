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
package testutils;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.serverconnection.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueStore;

import static java.util.Optional.ofNullable;

public class InMemoryIssueStore implements ServerIssueStore {
  private final Map<String, Map<String, Map<String, List<ServerIssue>>>> issuesByFileByBranchByProject = new HashMap<>();
  private final Map<String, Map<String, Instant>> lastSyncByBranchByProject = new HashMap<>();
  private final Map<String, ServerIssue> issuesByKey = new HashMap<>();
  private final Map<String, Map<String, Map<String, List<ServerTaintIssue>>>> taintIssuesByFileByBranchByProject = new HashMap<>();

  @Override
  public void replaceAllIssuesOfFile(String projectKey, String branchName, String serverFilePath, List<ServerIssue> issues) {
    issuesByFileByBranchByProject.computeIfAbsent(projectKey, __ -> new HashMap<>())
      .computeIfAbsent(branchName, __ -> new HashMap<>())
      .put(serverFilePath, issues);
    issues.forEach(issue -> issuesByKey.put(issue.getKey(), issue));
  }

  @Override
  public void mergeIssues(String projectKey, String branchName, List<ServerIssue> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp) {
    var issuesToMergeByFilePath = issuesToMerge.stream().collect(Collectors.groupingBy(ServerIssue::getFilePath));
    // does not handle issue moving file (e.g. file renaming)
    issuesByFileByBranchByProject.computeIfAbsent(projectKey, __ -> new HashMap<>())
      .computeIfAbsent(branchName, __ -> new HashMap<>())
      .putAll(issuesToMergeByFilePath);
    issuesToMerge.forEach(issue -> issuesByKey.put(issue.getKey(), issue));
    closedIssueKeysToDelete.forEach(issuesByKey::remove);
    lastSyncByBranchByProject.computeIfAbsent(projectKey, __ -> new HashMap<>()).put(branchName, syncTimestamp);
  }

  @Override
  public Optional<Instant> getLastSyncTimestamp(String projectKey, String branchName) {
    return ofNullable(lastSyncByBranchByProject.getOrDefault(projectKey, Map.of()).get(branchName));
  }

  @Override
  public void replaceAllIssuesOfProject(String projectKey, String branchName, List<ServerIssue> issues) {
    issuesByFileByBranchByProject.put(projectKey, Map.of(branchName, issues.stream().collect(Collectors.groupingBy(ServerIssue::getFilePath))));
    issues.forEach(issue -> issuesByKey.put(issue.getKey(), issue));
  }

  @Override
  public List<ServerIssue> load(String projectKey, String branchName, String sqFilePath) {
    return issuesByFileByBranchByProject.getOrDefault(projectKey, Map.of())
      .getOrDefault(branchName, Map.of())
      .getOrDefault(sqFilePath, List.of());
  }

  @Override
  public void replaceAllTaintOfFile(String projectKey, String branchName, String filePath, List<ServerTaintIssue> issues) {
    taintIssuesByFileByBranchByProject.computeIfAbsent(projectKey, __ -> new HashMap<>())
      .computeIfAbsent(branchName, __ -> new HashMap<>())
      .put(filePath, issues);
  }

  @Override
  public List<ServerTaintIssue> loadTaint(String projectKey, String branchName, String sqFilePath) {
    return taintIssuesByFileByBranchByProject.getOrDefault(projectKey, Map.of())
      .getOrDefault(branchName, Map.of())
      .getOrDefault(sqFilePath, List.of());
  }

  @Override
  public void updateIssue(String issueKey, Consumer<ServerIssue> issueConsumer) {
    issueConsumer.accept(issuesByKey.get(issueKey));
  }

  @Override
  public void close() {
    // nothing to do
  }
}
