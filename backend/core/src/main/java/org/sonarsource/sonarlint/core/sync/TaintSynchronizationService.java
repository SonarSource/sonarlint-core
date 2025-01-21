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

import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.TaintVulnerabilitiesSynchronizedEvent;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverconnection.IssueDownloader;
import org.sonarsource.sonarlint.core.serverconnection.ServerIssueUpdater;
import org.sonarsource.sonarlint.core.serverconnection.TaintIssueDownloader;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.UpdateSummary;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.stream.Collectors.groupingBy;

@Named
@Singleton
public class TaintSynchronizationService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConfigurationRepository configurationRepository;
  private final SonarProjectBranchTrackingService branchTrackingService;
  private final StorageService storageService;
  private final LanguageSupportRepository languageSupportRepository;
  private final ServerApiProvider serverApiProvider;
  private final ApplicationEventPublisher eventPublisher;

  public TaintSynchronizationService(ConfigurationRepository configurationRepository, SonarProjectBranchTrackingService branchTrackingService,
    StorageService storageService, LanguageSupportRepository languageSupportRepository,
    ServerApiProvider serverApiProvider, ApplicationEventPublisher eventPublisher) {
    this.configurationRepository = configurationRepository;
    this.branchTrackingService = branchTrackingService;
    this.storageService = storageService;
    this.languageSupportRepository = languageSupportRepository;
    this.serverApiProvider = serverApiProvider;
    this.eventPublisher = eventPublisher;
  }

  public void synchronizeTaintVulnerabilities(String connectionId, String projectKey, SonarLintCancelMonitor cancelMonitor) {
    serverApiProvider.withValidConnection(connectionId, serverApi -> {
      var allScopes = configurationRepository.getBoundScopesToConnectionAndSonarProject(connectionId, projectKey);
      var allScopesByOptBranch = allScopes.stream()
        .collect(groupingBy(b -> branchTrackingService.awaitEffectiveSonarProjectBranch(b.getConfigScopeId())));
      allScopesByOptBranch
        .forEach((branchNameOpt, scopes) -> branchNameOpt.ifPresent(branchName -> synchronizeTaintVulnerabilities(serverApi, connectionId, projectKey, branchName, cancelMonitor)));
    });
  }

  public void synchronizeTaintVulnerabilities(ServerApi serverApi, String connectionId, String projectKey, String branch, SonarLintCancelMonitor cancelMonitor) {
    if (languageSupportRepository.areTaintVulnerabilitiesSupported()) {
      var summary = updateServerTaintIssuesForProject(connectionId, serverApi, projectKey, branch, cancelMonitor);
      if (summary.hasAnythingChanged()) {
        eventPublisher.publishEvent(new TaintVulnerabilitiesSynchronizedEvent(connectionId, projectKey, branch, summary));
      }
    }
  }

  private UpdateSummary<ServerTaintIssue> updateServerTaintIssuesForProject(String connectionId, ServerApi serverApi, String projectKey,
    String branchName, SonarLintCancelMonitor cancelMonitor) {
    var storage = storageService.getStorageFacade().connection(connectionId);
    var enabledLanguagesToSync = languageSupportRepository.getEnabledLanguagesInConnectedMode().stream().filter(SonarLanguage::shouldSyncInConnectedMode)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    var issuesUpdater = new ServerIssueUpdater(storage, new IssueDownloader(enabledLanguagesToSync), new TaintIssueDownloader(enabledLanguagesToSync));
    if (serverApi.isSonarCloud()) {
      return issuesUpdater.downloadProjectTaints(serverApi, projectKey, branchName, cancelMonitor);
    } else {
      LOG.info("[SYNC] Synchronizing taint issues for project '{}' on branch '{}'", projectKey, branchName);
      return issuesUpdater.syncTaints(serverApi, projectKey, branchName, enabledLanguagesToSync, cancelMonitor);
    }
  }

}
