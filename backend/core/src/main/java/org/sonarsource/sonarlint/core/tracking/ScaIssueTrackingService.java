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
package org.sonarsource.sonarlint.core.tracking;

import java.util.Collections;
import java.util.List;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.ScaIssuesSynchronizedEvent;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ScaIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sca.DidChangeScaIssuesParams;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerScaIssue;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.ScaSynchronizationService;
import org.springframework.context.event.EventListener;

/**
 * Service responsible for tracking SCA (Software Composition Analysis) issues.
 * This service provides functionality to list and manage SCA issues for bound configuration scopes,
 * integrating with SonarQube servers to synchronize and retrieve issue data.
 */
public class ScaIssueTrackingService {
  private final ConfigurationRepository configurationRepository;
  private final SonarProjectBranchTrackingService branchTrackingService;
  private final ScaSynchronizationService scaSynchronizationService;
  private final StorageService storageService;
  private final SonarQubeClientManager sonarQubeClientManager;
  private final SonarLintRpcClient client;

  public ScaIssueTrackingService(ConfigurationRepository configurationRepository, SonarProjectBranchTrackingService branchTrackingService,
    ScaSynchronizationService scaSynchronizationService, StorageService storageService, SonarQubeClientManager sonarQubeClientManager, SonarLintRpcClient client) {
    this.configurationRepository = configurationRepository;
    this.branchTrackingService = branchTrackingService;
    this.scaSynchronizationService = scaSynchronizationService;
    this.storageService = storageService;
    this.sonarQubeClientManager = sonarQubeClientManager;
    this.client = client;
  }

  public List<ScaIssueDto> listAll(String configurationScopeId, boolean shouldRefresh, SonarLintCancelMonitor cancelMonitor) {
    return configurationRepository.getEffectiveBinding(configurationScopeId)
      .map(binding -> loadScaIssues(configurationScopeId, binding, shouldRefresh, cancelMonitor))
      .orElseGet(Collections::emptyList);
  }

  @EventListener
  public void onScaIssuesSynchronized(ScaIssuesSynchronizedEvent event) {
    var summary = event.summary();
    var connectionId = event.connectionId();
    var sonarProjectKey = event.sonarProjectKey();
    configurationRepository.getBoundScopesToConnectionAndSonarProject(connectionId, sonarProjectKey)
      .forEach(boundScope -> client.didChangeScaIssues(new DidChangeScaIssuesParams(boundScope.getConfigScopeId(), summary.deletedItemIds(),
        summary.addedItems().stream()
          .map(ScaIssueTrackingService::toDto)
          .toList(),
        summary.updatedItems().stream()
          .map(ScaIssueTrackingService::toDto)
          .toList())));
  }

  private List<ScaIssueDto> loadScaIssues(String configurationScopeId, Binding binding, boolean shouldRefresh, SonarLintCancelMonitor cancelMonitor) {
    return branchTrackingService.awaitEffectiveSonarProjectBranch(configurationScopeId)
      .map(matchedBranch -> {
        if (shouldRefresh) {
          sonarQubeClientManager.withActiveClient(binding.connectionId(),
            serverApi -> scaSynchronizationService.synchronize(serverApi, binding.connectionId(), binding.sonarProjectKey(), matchedBranch, cancelMonitor));
        }
        var projectStorage = storageService.binding(binding);
        return projectStorage.findings().loadScaIssues(matchedBranch)
          .stream().map(ScaIssueTrackingService::toDto)
          .toList();
      }).orElseGet(Collections::emptyList);
  }

  private static ScaIssueDto toDto(ServerScaIssue serverScaIssue) {
    return new ScaIssueDto(
      serverScaIssue.key(),
      ScaIssueDto.Type.valueOf(serverScaIssue.type().name()),
      ScaIssueDto.Severity.valueOf(serverScaIssue.severity().name()),
      ScaIssueDto.Status.valueOf(serverScaIssue.status().name()),
      serverScaIssue.packageName(),
      serverScaIssue.packageVersion(),
      serverScaIssue.transitions().stream()
        .map(transition -> ScaIssueDto.Transition.valueOf(transition.name()))
        .toList());
  }
}
