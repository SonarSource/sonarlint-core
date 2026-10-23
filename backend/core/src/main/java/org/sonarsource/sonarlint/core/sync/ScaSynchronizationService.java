/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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

import io.sentry.Sentry;
import java.util.ArrayList;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.DependencyRisksSynchronizedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.exception.InvalidServerItemException;
import org.sonarsource.sonarlint.core.serverapi.features.Feature;
import org.sonarsource.sonarlint.core.serverapi.sca.GetIssuesReleasesResponse;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.serverconnection.storage.UpdateSummary;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.stream.Collectors.toSet;

public class ScaSynchronizationService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final StorageService storageService;
  private final ApplicationEventPublisher eventPublisher;
  private final boolean isScaSynchronizationEnabled;

  public ScaSynchronizationService(StorageService storageService, ApplicationEventPublisher eventPublisher, InitializeParams initializeParams) {
    this.storageService = storageService;
    this.eventPublisher = eventPublisher;
    this.isScaSynchronizationEnabled = initializeParams.getBackendCapabilities().contains(BackendCapability.SCA_SYNCHRONIZATION);
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
    var findingsStore = storageService.connection(connectionId).project(sonarProjectKey).findings();

    var previousDependencyRisks = findingsStore.loadDependencyRisks(branchName);
    var previousDependencyRiskKeys = previousDependencyRisks.stream().map(ServerDependencyRisk::key).collect(toSet());

    var serverDependencyRisks = new ArrayList<ServerDependencyRisk>();
    for (var issueRelease : issuesReleases.issuesReleases()) {
      try {
        serverDependencyRisks.add(convertDependencyRisk(issueRelease));
      } catch (InvalidServerItemException e) {
        LOG.warn("[SYNC] Skipping dependency risk '{}': {}", issueRelease == null ? null : issueRelease.key(), e.getMessage(), e);
        Sentry.captureException(e);
      }
    }

    findingsStore.replaceAllDependencyRisksOfBranch(branchName, serverDependencyRisks);

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

  private static ServerDependencyRisk convertDependencyRisk(@Nullable GetIssuesReleasesResponse.IssuesRelease serverItem) {
    var issueRelease = required(serverItem, "item");
    required(issueRelease.key(), "key");
    required(issueRelease.type(), "type");
    required(issueRelease.severity(), "severity");
    required(issueRelease.quality(), "quality");
    required(issueRelease.status(), "status");
    required(issueRelease.release(), "release");
    required(issueRelease.release().packageName(), "release.packageName");
    required(issueRelease.release().version(), "release.version");
    required(issueRelease.transitions(), "transitions");
    issueRelease.transitions().forEach(transition -> required(transition, "transition"));
    return new ServerDependencyRisk(
      issueRelease.key(),
      parseEnum("type", () -> ServerDependencyRisk.Type.valueOf(issueRelease.type().name())),
      parseEnum("severity", () -> ServerDependencyRisk.Severity.valueOf(issueRelease.severity().name())),
      parseEnum("quality", () -> ServerDependencyRisk.SoftwareQuality.valueOf(issueRelease.quality().name())),
      parseEnum("status", () -> ServerDependencyRisk.Status.valueOf(issueRelease.status().name())),
      issueRelease.release().packageName(),
      issueRelease.release().version(),
      issueRelease.vulnerabilityId(),
      issueRelease.cvssScore(),
      issueRelease.transitions().stream().map(transition -> parseEnum("transition", () -> ServerDependencyRisk.Transition.valueOf(transition.name()))).toList());
  }

  private static <T> T required(@Nullable T value, String field) {
    if (value == null) {
      throw new InvalidServerItemException("Missing or unknown dependency risk " + field);
    }
    return value;
  }

  private static <T> T parseEnum(String field, Supplier<T> parser) {
    try {
      return parser.get();
    } catch (IllegalArgumentException e) {
      throw new InvalidServerItemException("Unsupported dependency risk " + field, e);
    }
  }

  private boolean isScaSupported(String connectionId) {
    var serverInfo = storageService.connection(connectionId).serverInfo().read();
    return serverInfo.map(info -> info.hasFeature(Feature.SCA)).orElse(false);
  }
}
