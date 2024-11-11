/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.ConnectionManager;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.serverapi.ServerApiErrorHandlingWrapper;
import org.sonarsource.sonarlint.core.serverconnection.IssueDownloader;
import org.sonarsource.sonarlint.core.serverconnection.ServerIssueUpdater;
import org.sonarsource.sonarlint.core.serverconnection.TaintIssueDownloader;
import org.sonarsource.sonarlint.core.storage.StorageService;

@Named
@Singleton
public class IssueSynchronizationService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final StorageService storageService;
  private final LanguageSupportRepository languageSupportRepository;
  private final ConnectionManager connectionManager;

  public IssueSynchronizationService(StorageService storageService, LanguageSupportRepository languageSupportRepository,
    ConnectionManager connectionManager) {
    this.storageService = storageService;
    this.languageSupportRepository = languageSupportRepository;
    this.connectionManager = connectionManager;
  }

  public void syncServerIssuesForProject(ServerApiErrorHandlingWrapper serverApiWrapper, String connectionId, String projectKey, String branchName, boolean isSonarCloud,
    SonarLintCancelMonitor cancelMonitor) {
    var storage = storageService.getStorageFacade().connection(connectionId);
    var enabledLanguagesToSync = languageSupportRepository.getEnabledLanguagesInConnectedMode().stream().filter(SonarLanguage::shouldSyncInConnectedMode)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    var issuesUpdater = new ServerIssueUpdater(storage, new IssueDownloader(enabledLanguagesToSync), new TaintIssueDownloader(enabledLanguagesToSync));
    if (isSonarCloud) {
      LOG.debug("Incremental issue sync is not supported by SonarCloud. Skipping.");
    } else {
      LOG.info("[SYNC] Synchronizing issues for project '{}' on branch '{}'", projectKey, branchName);
      issuesUpdater.sync(serverApiWrapper, projectKey, branchName, enabledLanguagesToSync, cancelMonitor);
    }
  }

  public void fetchProjectIssues(Binding binding, String activeBranch, SonarLintCancelMonitor cancelMonitor) {
    if (connectionManager.hasConnection(binding.getConnectionId())) {
      downloadServerIssuesForProject(binding.getConnectionId(), connectionManager.getServerApiWrapperOrThrow(binding.getConnectionId()),
        binding.getSonarProjectKey(), activeBranch, cancelMonitor);
    }
  }

  private void downloadServerIssuesForProject(String connectionId, ServerApiErrorHandlingWrapper serverApiWrapper, String projectKey, String branchName,
    SonarLintCancelMonitor cancelMonitor) {
    var storage = storageService.getStorageFacade().connection(connectionId);
    var enabledLanguagesToSync = languageSupportRepository.getEnabledLanguagesInConnectedMode().stream().filter(SonarLanguage::shouldSyncInConnectedMode)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    var issuesUpdater = new ServerIssueUpdater(storage, new IssueDownloader(enabledLanguagesToSync), new TaintIssueDownloader(enabledLanguagesToSync));
    issuesUpdater.update(serverApiWrapper, projectKey, branchName, cancelMonitor);
  }

  public void fetchFileIssues(Binding binding, Path serverFileRelativePath, String activeBranch, SonarLintCancelMonitor cancelMonitor) {
    if (connectionManager.hasConnection(binding.getConnectionId())) {
      downloadServerIssuesForFile(binding.getConnectionId(), connectionManager.getServerApiWrapperOrThrow(binding.getConnectionId()), binding.getSonarProjectKey(),
        serverFileRelativePath, activeBranch, cancelMonitor);
    }
  }

  public void downloadServerIssuesForFile(String connectionId, ServerApiErrorHandlingWrapper serverApiWrapper, String projectKey, Path serverFileRelativePath, String branchName,
    SonarLintCancelMonitor cancelMonitor) {
    var storage = storageService.getStorageFacade().connection(connectionId);
    var enabledLanguagesToSync = languageSupportRepository.getEnabledLanguagesInConnectedMode().stream().filter(SonarLanguage::shouldSyncInConnectedMode)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    var issuesUpdater = new ServerIssueUpdater(storage, new IssueDownloader(enabledLanguagesToSync), new TaintIssueDownloader(enabledLanguagesToSync));
    issuesUpdater.updateFileIssuesIfNeeded(serverApiWrapper, projectKey, serverFileRelativePath, branchName, cancelMonitor);
  }

}
