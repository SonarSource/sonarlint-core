/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.sync;

import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.ScaIssuesSynchronizedEvent;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerScaIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.UpdateSummary;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.stream.Collectors.toSet;
import static org.sonarsource.sonarlint.core.serverconnection.ServerSettings.SCA_ENABLED;

public class ScaSynchronizationService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final StorageService storageService;
  private final ApplicationEventPublisher eventPublisher;

  public ScaSynchronizationService(StorageService storageService, ApplicationEventPublisher eventPublisher) {
    this.storageService = storageService;
    this.eventPublisher = eventPublisher;
  }

  public void synchronize(ServerApi serverApi, String connectionId, String sonarProjectKey, String branchName, SonarLintCancelMonitor cancelMonitor) {
    if (!isScaSupported(serverApi, connectionId)) {
      return;
    }
    LOG.info("[SYNC] Synchronizing SCA issues for project '{}' on branch '{}'", sonarProjectKey, branchName);

    var summary = updateServerScaIssuesForProject(serverApi, connectionId, sonarProjectKey, branchName, cancelMonitor);
    if (summary.hasAnythingChanged()) {
      eventPublisher.publishEvent(new ScaIssuesSynchronizedEvent(connectionId, sonarProjectKey, branchName, summary));
    }
  }

  private UpdateSummary<ServerScaIssue> updateServerScaIssuesForProject(ServerApi serverApi, String connectionId, String sonarProjectKey, String branchName,
    SonarLintCancelMonitor cancelMonitor) {
    var issuesReleases = serverApi.sca().getIssuesReleases(sonarProjectKey, branchName, cancelMonitor);
    var findingsStore = storageService.connection(connectionId).project(sonarProjectKey).findings();

    var previousScaIssues = findingsStore.loadScaIssues(branchName);
    var previousScaIssueKeys = previousScaIssues.stream().map(ServerScaIssue::key).collect(toSet());

    var scaIssues = issuesReleases.issuesReleases().stream()
      .map(issueRelease -> new ServerScaIssue(
        issueRelease.key(),
        ServerScaIssue.Type.valueOf(issueRelease.type().name()),
        ServerScaIssue.Severity.valueOf(issueRelease.severity().name()),
        ServerScaIssue.Status.valueOf(issueRelease.status().name()),
        issueRelease.release().packageName(),
        issueRelease.release().version(),
        issueRelease.transitions().stream().map(Enum::name).map(ServerScaIssue.Transition::valueOf).toList()))
      .toList();

    findingsStore.replaceAllScaIssuesOfBranch(branchName, scaIssues);

    var newScaIssueKeys = scaIssues.stream().map(ServerScaIssue::key).collect(toSet());
    var deletedScaIssueIds = previousScaIssues.stream()
      .map(ServerScaIssue::key)
      .filter(key -> !newScaIssueKeys.contains(key))
      .collect(toSet());
    var addedScaIssues = scaIssues.stream()
      .filter(issue -> !previousScaIssueKeys.contains(issue.key()))
      .toList();
    var updatedScaIssues = scaIssues.stream()
      .filter(issue -> previousScaIssueKeys.contains(issue.key()))
      .toList();

    return new UpdateSummary<>(deletedScaIssueIds, addedScaIssues, updatedScaIssues);
  }

  private boolean isScaSupported(ServerApi serverApi, String connectionId) {
    if (serverApi.isSonarCloud()) {
      return false;
    }
    var serverInfo = storageService.connection(connectionId).serverInfo().read();
    return serverInfo.flatMap(info -> info.globalSettings().getAsBoolean(SCA_ENABLED)).orElse(false);
  }
}
