/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.HotspotDownloader;
import org.sonarsource.sonarlint.core.serverconnection.ServerHotspotUpdater;
import org.sonarsource.sonarlint.core.serverconnection.ServerInfoSynchronizer;
import org.sonarsource.sonarlint.core.storage.StorageService;

import static java.util.stream.Collectors.groupingBy;

@Named
@Singleton
public class HotspotSynchronizationService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConfigurationRepository configurationRepository;
  private final SonarProjectBranchTrackingService branchTrackingService;
  private final StorageService storageService;
  private final LanguageSupportRepository languageSupportRepository;
  private final ServerApiProvider serverApiProvider;

  public HotspotSynchronizationService(ConfigurationRepository configurationRepository, SonarProjectBranchTrackingService branchTrackingService,
    StorageService storageService, LanguageSupportRepository languageSupportRepository,
    ServerApiProvider serverApiProvider) {
    this.configurationRepository = configurationRepository;
    this.branchTrackingService = branchTrackingService;
    this.storageService = storageService;
    this.languageSupportRepository = languageSupportRepository;
    this.serverApiProvider = serverApiProvider;
  }

  public void syncServerHotspotsForProject(String connectionId, String projectKey) {
    serverApiProvider.getServerApi(connectionId).ifPresent(serverApi -> {
      var allScopes = configurationRepository.getBoundScopesToConnectionAndSonarProject(connectionId, projectKey);
      var allScopesByOptBranch = allScopes.stream()
        .collect(groupingBy(b -> branchTrackingService.awaitEffectiveSonarProjectBranch(b.getConfigScopeId())));
      allScopesByOptBranch
        .forEach((branchNameOpt, scopes) -> branchNameOpt.ifPresent(branchName -> syncServerHotspotsForProject(serverApi, connectionId, projectKey, branchName)));
    });
  }

  private void syncServerHotspotsForProject(ServerApi serverApi, String connectionId, String projectKey, String branchName) {
    var storage = storageService.getStorageFacade().connection(connectionId);
    var serverVersion = getSonarServerVersion(serverApi, storage);
    var enabledLanguagesToSync = languageSupportRepository.getEnabledLanguagesInConnectedMode().stream().filter(Language::shouldSyncInConnectedMode)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    var hotspotsUpdater = new ServerHotspotUpdater(storage, new HotspotDownloader(enabledLanguagesToSync));
    if (HotspotApi.supportHotspotsPull(serverApi.isSonarCloud(), serverVersion)) {
      LOG.info("[SYNC] Synchronizing hotspots for project '{}' on branch '{}'", projectKey, branchName);
      hotspotsUpdater.sync(serverApi.hotspot(), projectKey, branchName, enabledLanguagesToSync);
    } else {
      LOG.debug("Incremental hotspot sync is not supported. Skipping.");
    }
  }

  private static Version getSonarServerVersion(ServerApi serverApi, ConnectionStorage storage) {
    var serverInfoSynchronizer = new ServerInfoSynchronizer(storage);
    return serverInfoSynchronizer.readOrSynchronizeServerInfo(serverApi).getVersion();
  }

  public void fetchProjectHotspots(Binding binding, String activeBranch) {
    serverApiProvider.getServerApi(binding.getConnectionId())
      .ifPresent(serverApi -> downloadAllServerHotspots(binding.getConnectionId(), serverApi, binding.getSonarProjectKey(), activeBranch, new ProgressMonitor(null)));
  }

  private void downloadAllServerHotspots(String connectionId, ServerApi serverApi, String projectKey, String branchName, ProgressMonitor progress) {
    var storage = storageService.getStorageFacade().connection(connectionId);
    var serverVersion = getSonarServerVersion(serverApi, storage);
    var enabledLanguagesToSync = languageSupportRepository.getEnabledLanguagesInConnectedMode().stream().filter(Language::shouldSyncInConnectedMode)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    var hotspotsUpdater = new ServerHotspotUpdater(storage, new HotspotDownloader(enabledLanguagesToSync));
    hotspotsUpdater.updateAll(serverApi.hotspot(), projectKey, branchName, () -> serverVersion, progress);
  }

  public void fetchFileHotspots(Binding binding, String activeBranch, Path serverFilePath) {
    serverApiProvider.getServerApi(binding.getConnectionId())
      .ifPresent(serverApi -> downloadAllServerHotspotsForFile(binding.getConnectionId(), serverApi, binding.getSonarProjectKey(), serverFilePath, activeBranch));
  }

  private void downloadAllServerHotspotsForFile(String connectionId, ServerApi serverApi, String projectKey, Path serverRelativeFilePath, String branchName) {
    var storage = storageService.getStorageFacade().connection(connectionId);
    var serverVersion = getSonarServerVersion(serverApi, storage);
    var enabledLanguagesToSync = languageSupportRepository.getEnabledLanguagesInConnectedMode().stream().filter(Language::shouldSyncInConnectedMode)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    var hotspotsUpdater = new ServerHotspotUpdater(storage, new HotspotDownloader(enabledLanguagesToSync));
    hotspotsUpdater.updateForFile(serverApi.hotspot(), projectKey, serverRelativeFilePath, branchName, () -> serverVersion);
  }

}
