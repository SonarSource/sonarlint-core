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

import java.util.Optional;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.sonarsource.sonarlint.core.ConnectionManager;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.branches.ServerBranch;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBranches;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.stream.Collectors.toSet;

/**
 * This service manages the synchronization of the SonarProject branches from the Sonar server in the local storage.
 */
@Named
@Singleton
public class SonarProjectBranchesSynchronizationService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final StorageService storageService;
  private final ConnectionManager connectionManager;
  private final ApplicationEventPublisher eventPublisher;

  public SonarProjectBranchesSynchronizationService(StorageService storageService, ConnectionManager connectionManager, ApplicationEventPublisher eventPublisher) {
    this.storageService = storageService;
    this.connectionManager = connectionManager;
    this.eventPublisher = eventPublisher;
  }

  public void sync(String connectionId, String sonarProjectKey, SonarLintCancelMonitor cancelMonitor) {
    connectionManager.withValidConnection(connectionId, serverApi -> {
      var branchesStorage = storageService.getStorageFacade().connection(connectionId).project(sonarProjectKey).branches();
      Optional<ProjectBranches> oldBranches = Optional.empty();
      if (branchesStorage.exists()) {
        oldBranches = Optional.of(branchesStorage.read());
      }
      var newBranches = getProjectBranches(serverApi, sonarProjectKey, cancelMonitor);
      branchesStorage.store(newBranches);
      if (oldBranches.isEmpty() || !oldBranches.get().equals(newBranches)) {
        LOG.debug("Project branches changed for project '{}'", sonarProjectKey);
        eventPublisher.publishEvent(new SonarProjectBranchesChangedEvent(connectionId, sonarProjectKey));
      }
    });
  }

  public ProjectBranches getProjectBranches(ServerApi serverApi, String projectKey, SonarLintCancelMonitor cancelMonitor) {
    LOG.info("Synchronizing project branches for project '{}'", projectKey);
    var allBranches = serverApi.branches().getAllBranches(projectKey, cancelMonitor);
    var mainBranch = allBranches.stream().filter(ServerBranch::isMain).findFirst().map(ServerBranch::getName)
      .orElseThrow(() -> new IllegalStateException("No main branch for project '" + projectKey + "'"));
    return new ProjectBranches(allBranches.stream().map(ServerBranch::getName).collect(toSet()), mainBranch);
  }

  public String findMainBranch(String connectionId, String projectKey, SonarLintCancelMonitor cancelMonitor) {
    var branchesStorage = storageService.binding(new Binding(connectionId, projectKey)).branches();
    if (branchesStorage.exists()) {
      var storedBranches = branchesStorage.read();
      return storedBranches.getMainBranchName();
    } else {
      return connectionManager.withValidConnectionAndReturn(connectionId,
          serverApi -> getProjectBranches(serverApi, projectKey, cancelMonitor))
        .map(ProjectBranches::getMainBranchName).orElseThrow();
    }
  }

}
