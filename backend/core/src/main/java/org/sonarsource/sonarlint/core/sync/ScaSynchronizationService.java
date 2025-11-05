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
import org.sonarsource.sonarlint.core.event.DependencyRisksSynchronizedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.features.Feature;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.serverconnection.repository.ServerInfoRepository;
import org.sonarsource.sonarlint.core.serverconnection.repository.ServerIssuesRepository;
import org.sonarsource.sonarlint.core.serverconnection.storage.UpdateSummary;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.stream.Collectors.toSet;

public class ScaSynchronizationService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ServerIssuesRepository serverIssuesRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final boolean isScaSynchronizationEnabled;
  private final ServerInfoRepository serverInfoRepository;

  public ScaSynchronizationService(ServerIssuesRepository serverIssuesRepository, ApplicationEventPublisher eventPublisher, InitializeParams initializeParams, ServerInfoRepository serverInfoRepository) {
    this.serverIssuesRepository = serverIssuesRepository;
    this.eventPublisher = eventPublisher;
    this.isScaSynchronizationEnabled = initializeParams.getBackendCapabilities().contains(BackendCapability.SCA_SYNCHRONIZATION);
    this.serverInfoRepository = serverInfoRepository;
  }

  public void synchronize(ServerApi serverApi, String connectionId, String sonarProjectKey, String branchName, SonarLintCancelMonitor cancelMonitor) {
    if (!isScaSynchronizationEnabled) {
      return;
    }
    if (!isScaSupported(connectionId)) {
      return;
    }
    LOG.info("[SYNC] Synchronizing dependency risks for project '{}' on branch '{}'", sonarProjectKey, branchName);

    var summary = updateServerDependencyRisksForProject(serverApi, connectionId, sonarProjectKey, branchName, cancelMonitor);
    if (summary.hasAnythingChanged()) {
      eventPublisher.publishEvent(new DependencyRisksSynchronizedEvent(connectionId, sonarProjectKey, branchName, summary));
    }
  }

  private UpdateSummary<ServerDependencyRisk> updateServerDependencyRisksForProject(ServerApi serverApi, String connectionId, String sonarProjectKey, String branchName,
    SonarLintCancelMonitor cancelMonitor) {
    var issuesReleases = serverApi.sca().getIssuesReleases(sonarProjectKey, branchName, cancelMonitor);

    var previousDependencyRisks = serverIssuesRepository.loadDependencyRisks(connectionId, sonarProjectKey, branchName);
    var previousDependencyRiskKeys = previousDependencyRisks.stream().map(ServerDependencyRisk::key).collect(toSet());

    var serverDependencyRisks = issuesReleases.issuesReleases().stream()
      .map(issueRelease -> new ServerDependencyRisk(
        issueRelease.key(),
        ServerDependencyRisk.Type.valueOf(issueRelease.type().name()),
        ServerDependencyRisk.Severity.valueOf(issueRelease.severity().name()),
        ServerDependencyRisk.SoftwareQuality.valueOf(issueRelease.quality().name()),
        ServerDependencyRisk.Status.valueOf(issueRelease.status().name()),
        issueRelease.release().packageName(),
        issueRelease.release().version(),
        issueRelease.vulnerabilityId(),
        issueRelease.cvssScore(),
        issueRelease.transitions().stream().map(Enum::name).map(ServerDependencyRisk.Transition::valueOf).toList()))
      .toList();

    serverIssuesRepository.replaceAllDependencyRisksOfBranch(connectionId, sonarProjectKey, branchName, serverDependencyRisks);

    var newDependencyRiskKeys = serverDependencyRisks.stream().map(ServerDependencyRisk::key).collect(toSet());
    var deletedDependencyRiskIds = previousDependencyRisks.stream()
      .map(ServerDependencyRisk::key)
      .filter(key -> !newDependencyRiskKeys.contains(key))
      .collect(toSet());
    var addedDependencyRisks = serverDependencyRisks.stream()
      .filter(issue -> !previousDependencyRiskKeys.contains(issue.key()))
      .toList();
    var updatedDependencyRisks = serverDependencyRisks.stream()
      .filter(issue -> previousDependencyRiskKeys.contains(issue.key()))
      .toList();

    return new UpdateSummary<>(deletedDependencyRiskIds, addedDependencyRisks, updatedDependencyRisks);
  }

  private boolean isScaSupported(String connectionId) {
    var serverInfo = serverInfoRepository.read(connectionId);
    return serverInfo.map(info -> info.hasFeature(Feature.SCA)).orElse(false);
  }
}
