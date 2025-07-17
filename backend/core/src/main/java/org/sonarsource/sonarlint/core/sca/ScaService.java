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
package org.sonarsource.sonarlint.core.sca;

import java.util.UUID;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskTransition;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerScaIssue;
import org.sonarsource.sonarlint.core.storage.StorageService;

public class ScaService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConfigurationRepository configurationRepository;
  private final StorageService storageService;
  private final SonarQubeClientManager sonarQubeClientManager;
  private final SonarProjectBranchTrackingService branchTrackingService;

  public ScaService(ConfigurationRepository configurationRepository, StorageService storageService, SonarQubeClientManager sonarQubeClientManager,
    SonarProjectBranchTrackingService branchTrackingService) {
    this.configurationRepository = configurationRepository;
    this.storageService = storageService;
    this.sonarQubeClientManager = sonarQubeClientManager;
    this.branchTrackingService = branchTrackingService;
  }

  public void changeStatus(String configurationScopeId, UUID issueReleaseKey, DependencyRiskTransition transition, @CheckForNull String comment,
    SonarLintCancelMonitor cancelMonitor) {
    var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
    var serverConnection = sonarQubeClientManager.getClientOrThrow(binding.connectionId());
    var projectServerIssueStore = storageService.binding(binding).findings();
    var branchName = branchTrackingService.awaitEffectiveSonarProjectBranch(configurationScopeId);

    if (branchName.isEmpty()) {
      throw new IllegalArgumentException("Could not determine matched branch for configuration scope " + configurationScopeId);
    }

    var scaIssues = projectServerIssueStore.loadScaIssues(branchName.get());
    var dependencyRiskOpt = scaIssues.stream().filter(issue -> issue.key().equals(issueReleaseKey)).findFirst();

    if (dependencyRiskOpt.isEmpty()) {
      throw new ScaIssueNotFoundException("Dependency Risk with key " + issueReleaseKey.toString() + " was not found", issueReleaseKey.toString());
    }

    var dependencyRisk = dependencyRiskOpt.get();

    if (!dependencyRisk.transitions().contains(adaptTransition(transition))) {
      throw new IllegalArgumentException("Transition " + transition + " is not allowed for this SCA issue");
    }

    if ((transition == DependencyRiskTransition.ACCEPT || transition == DependencyRiskTransition.SAFE || transition == DependencyRiskTransition.FIXED)
      && (comment == null || comment.isBlank())) {
      throw new IllegalArgumentException("Comment is required for ACCEPT, FIXED, and SAFE transitions");
    }

    LOG.info("Changing SCA issue status for issue {} to {} with comment: {}", issueReleaseKey, transition, comment);

    serverConnection.withClientApi(serverApi -> serverApi.sca().changeStatus(issueReleaseKey, transition.name(), comment, cancelMonitor));
  }

  private static ServerScaIssue.Transition adaptTransition(DependencyRiskTransition transition) {
    return switch (transition) {
      case REOPEN -> ServerScaIssue.Transition.REOPEN;
      case CONFIRM -> ServerScaIssue.Transition.CONFIRM;
      case ACCEPT -> ServerScaIssue.Transition.ACCEPT;
      case SAFE -> ServerScaIssue.Transition.SAFE;
      case FIXED -> ServerScaIssue.Transition.FIXED;
    };
  }

  public static class ScaIssueNotFoundException extends RuntimeException {
    private final String issueKey;

    public ScaIssueNotFoundException(String message, String issueKey) {
      super(message);
      this.issueKey = issueKey;
    }

    public String getIssueKey() {
      return issueKey;
    }
  }
}
