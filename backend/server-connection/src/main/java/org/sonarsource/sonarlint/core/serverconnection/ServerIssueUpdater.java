/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.UpdateSummary;

import static java.util.stream.Collectors.toSet;
import static org.sonarsource.sonarlint.core.serverconnection.ServerUpdaterUtils.computeLastSync;

public class ServerIssueUpdater {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConnectionStorage storage;
  private final IssueDownloader issueDownloader;
  private final TaintIssueDownloader taintIssueDownloader;

  public ServerIssueUpdater(ConnectionStorage storage, IssueDownloader issueDownloader, TaintIssueDownloader taintIssueDownloader) {
    this.storage = storage;
    this.issueDownloader = issueDownloader;
    this.taintIssueDownloader = taintIssueDownloader;
  }

  public void update(ServerApi serverApi, String projectKey, String branchName, Set<SonarLanguage> enabledLanguages, SonarLintCancelMonitor cancelMonitor) {
    if (serverApi.isSonarCloud()) {
      LOG.debug("Start downloading issues from SonarQube Cloud");
      var start = Instant.now();
      var issues = issueDownloader.downloadFromBatch(serverApi, projectKey, branchName, cancelMonitor);
      var finishedDownload = Instant.now();
      LOG.debug("Finished downloading {} issues from SonarQube Cloud in {} ms", issues.size(), finishedDownload.toEpochMilli() - start.toEpochMilli());
      var issueStore = storage.project(projectKey).findings();
      LOG.debug("Issue store type: {}", issueStore.getClass().getSimpleName());
      issueStore.replaceAllIssuesOfBranch(branchName, issues, enabledLanguages);
      var finishedWritingToStorage = Instant.now();
      LOG.debug("Finished updating {} issues in storage in {} ms", issues.size(), finishedWritingToStorage.toEpochMilli() - finishedDownload.toEpochMilli());
    } else {
      sync(serverApi, projectKey, branchName, issueDownloader.getEnabledLanguages(), cancelMonitor);
    }
  }

  public void sync(ServerApi serverApi, String projectKey, String branchName, Set<SonarLanguage> enabledLanguages, SonarLintCancelMonitor cancelMonitor) {
    var lastSync = storage.project(projectKey).findings().getLastIssueSyncTimestamp(branchName);

    lastSync = computeLastSync(enabledLanguages, lastSync, storage.project(projectKey).findings().getLastIssueEnabledLanguages(branchName));
    var start = Instant.now();
    var result = issueDownloader.downloadFromPull(serverApi, projectKey, branchName, lastSync, cancelMonitor);
    var downloadTime = Instant.now();
    LOG.debug("Downloaded {} issues took {}", result.getChangedIssues().size(), downloadTime.toEpochMilli() - start.toEpochMilli());
    storage.project(projectKey).findings().mergeIssues(branchName, result.getChangedIssues(), result.getClosedIssueKeys(),
      result.getQueryTimestamp(), enabledLanguages);
    LOG.debug("Finished updating {} issues in storage in {} ms", result.getChangedIssues().size(), Instant.now().toEpochMilli() - downloadTime.toEpochMilli());
  }

  public UpdateSummary<ServerTaintIssue> syncTaints(ServerApi serverApi, String projectKey, String branchName, Set<SonarLanguage> enabledLanguages,
    SonarLintCancelMonitor cancelMonitor) {
    var serverIssueStore = storage.project(projectKey).findings();

    var lastSync = serverIssueStore.getLastTaintSyncTimestamp(branchName);

    lastSync = computeLastSync(enabledLanguages, lastSync, storage.project(projectKey).findings().getLastTaintEnabledLanguages(branchName));

    var result = taintIssueDownloader.downloadTaintFromPull(serverApi, projectKey, branchName, lastSync, cancelMonitor);
    var previousTaintIssues = serverIssueStore.loadTaint(branchName);
    var previousTaintIssueKeys = previousTaintIssues.stream().map(ServerTaintIssue::getSonarServerKey).collect(toSet());
    serverIssueStore.mergeTaintIssues(branchName, result.getChangedTaintIssues(), result.getClosedIssueKeys(), result.getQueryTimestamp(), enabledLanguages);
    var deletedTaintVulnerabilityIds = previousTaintIssues.stream().filter(issue -> result.getClosedIssueKeys().contains(issue.getSonarServerKey())).map(ServerTaintIssue::getId)
      .collect(toSet());
    var addedTaintVulnerabilities = result.getChangedTaintIssues().stream().filter(issue -> !previousTaintIssueKeys.contains(issue.getSonarServerKey()))
      .toList();
    var updatedTaintVulnerabilities = result.getChangedTaintIssues().stream().filter(issue -> previousTaintIssueKeys.contains(issue.getSonarServerKey()))
      .toList();
    return new UpdateSummary<>(deletedTaintVulnerabilityIds, addedTaintVulnerabilities, updatedTaintVulnerabilities);
  }

  public void updateFileIssuesIfNeeded(ServerApi serverApi, String projectKey, Path serverFileRelativePath, String branchName, SonarLintCancelMonitor cancelMonitor) {
    if (serverApi.isSonarCloud()) {
      updateFileIssues(serverApi, projectKey, serverFileRelativePath, branchName, cancelMonitor);
    } else {
      LOG.debug("Skip downloading file issues on SonarQube ");
    }
  }

  public void updateFileIssues(ServerApi serverApi, String projectKey, Path serverFileRelativePath, String branchName, SonarLintCancelMonitor cancelMonitor) {
    var fileKey = IssueStorePaths.componentKey(projectKey, serverFileRelativePath);
    List<ServerIssue<?>> issues = new ArrayList<>();
    try {
      issues.addAll(issueDownloader.downloadFromBatch(serverApi, fileKey, branchName, cancelMonitor));
    } catch (Exception e) {
      // null as cause so that it doesn't get wrapped
      throw new DownloadException("Failed to update file issues: " + e.getMessage(), null);
    }
    storage.project(projectKey).findings().replaceAllIssuesOfFile(branchName, serverFileRelativePath, issues);
  }

  public UpdateSummary<ServerTaintIssue> downloadProjectTaints(ServerApi serverApi, String projectKey, String branchName, Set<SonarLanguage> enabledLanguages,
    SonarLintCancelMonitor cancelMonitor) {
    List<ServerTaintIssue> newTaintIssues;
    try {
      newTaintIssues = new ArrayList<>(taintIssueDownloader.downloadTaintFromIssueSearch(serverApi, projectKey, branchName, cancelMonitor));
    } catch (Exception e) {
      // null as cause so that it doesn't get wrapped
      throw new DownloadException("Failed to update file taint vulnerabilities: " + e.getMessage(), null);
    }
    var findingsStorage = storage.project(projectKey).findings();
    var previousTaintIssues = findingsStorage.loadTaint(branchName);
    var previousTaintIssueKeys = previousTaintIssues.stream().map(ServerTaintIssue::getSonarServerKey).collect(toSet());
    findingsStorage.replaceAllTaintsOfBranch(branchName, newTaintIssues, enabledLanguages);
    var newTaintIssueKeys = newTaintIssues.stream().map(ServerTaintIssue::getSonarServerKey).collect(toSet());
    var deletedTaintVulnerabilityIds = previousTaintIssues.stream().filter(issue -> !newTaintIssueKeys.contains(issue.getSonarServerKey())).map(ServerTaintIssue::getId)
      .collect(toSet());
    var addedTaintVulnerabilities = newTaintIssues.stream().filter(issue -> !previousTaintIssueKeys.contains(issue.getSonarServerKey()))
      .toList();
    var updatedTaintVulnerabilities = newTaintIssues.stream().filter(issue -> previousTaintIssueKeys.contains(issue.getSonarServerKey()))
      .toList();
    return new UpdateSummary<>(deletedTaintVulnerabilityIds, addedTaintVulnerabilities, updatedTaintVulnerabilities);
  }
}
