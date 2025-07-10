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

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.HotspotDownloader;
import org.sonarsource.sonarlint.core.serverconnection.ServerHotspotUpdater;
import org.sonarsource.sonarlint.core.serverconnection.ServerInfoSynchronizer;
import org.sonarsource.sonarlint.core.storage.StorageService;

public class HotspotSynchronizationService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final StorageService storageService;
  private final LanguageSupportRepository languageSupportRepository;
  private final SonarQubeClientManager sonarQubeClientManager;

  public HotspotSynchronizationService(StorageService storageService, LanguageSupportRepository languageSupportRepository, SonarQubeClientManager sonarQubeClientManager) {
    this.storageService = storageService;
    this.languageSupportRepository = languageSupportRepository;
    this.sonarQubeClientManager = sonarQubeClientManager;
  }

  public void syncServerHotspotsForProject(ServerApi serverApi, String connectionId, String projectKey, String branchName, SonarLintCancelMonitor cancelMonitor) {
    var storage = storageService.connection(connectionId);
    var serverVersion = getSonarServerVersion(serverApi, storage, cancelMonitor);
    var enabledLanguagesToSync = languageSupportRepository.getEnabledLanguagesInConnectedMode().stream().filter(SonarLanguage::shouldSyncInConnectedMode)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    var hotspotsUpdater = new ServerHotspotUpdater(storage, new HotspotDownloader(enabledLanguagesToSync));
    if (HotspotApi.supportHotspotsPull(serverApi.isSonarCloud(), serverVersion)) {
      LOG.info("[SYNC] Synchronizing hotspots for project '{}' on branch '{}'", projectKey, branchName);
      hotspotsUpdater.sync(serverApi.hotspot(), projectKey, branchName, enabledLanguagesToSync, cancelMonitor);
    } else {
      LOG.debug("Incremental hotspot sync is not supported. Skipping.");
    }
  }

  private static Version getSonarServerVersion(ServerApi serverApi, ConnectionStorage storage, SonarLintCancelMonitor cancelMonitor) {
    var serverInfoSynchronizer = new ServerInfoSynchronizer(storage);
    return serverInfoSynchronizer.readOrSynchronizeServerInfo(serverApi, cancelMonitor).version();
  }

  public void fetchProjectHotspots(Binding binding, String activeBranch, SonarLintCancelMonitor cancelMonitor) {
    sonarQubeClientManager.withActiveClient(binding.connectionId(), serverApi ->
      downloadAllServerHotspots(binding.connectionId(), serverApi, binding.sonarProjectKey(), activeBranch, cancelMonitor));
  }

  private void downloadAllServerHotspots(String connectionId, ServerApi serverApi, String projectKey, String branchName, SonarLintCancelMonitor cancelMonitor) {
    var storage = storageService.connection(connectionId);
    var serverVersion = getSonarServerVersion(serverApi, storage, cancelMonitor);
    var enabledLanguagesToSync = languageSupportRepository.getEnabledLanguagesInConnectedMode().stream().filter(SonarLanguage::shouldSyncInConnectedMode)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    var hotspotsUpdater = new ServerHotspotUpdater(storage, new HotspotDownloader(enabledLanguagesToSync));
    hotspotsUpdater.updateAll(serverApi.hotspot(), projectKey, branchName, () -> serverVersion, cancelMonitor);
  }

  public void fetchFileHotspots(Binding binding, String activeBranch, Path serverFilePath, SonarLintCancelMonitor cancelMonitor) {
    sonarQubeClientManager.withActiveClient(binding.connectionId(), serverApi ->
      downloadAllServerHotspotsForFile(binding.connectionId(), serverApi, binding.sonarProjectKey(), serverFilePath, activeBranch, cancelMonitor));
  }

  private void downloadAllServerHotspotsForFile(String connectionId, ServerApi serverApi, String projectKey, Path serverRelativeFilePath, String branchName,
    SonarLintCancelMonitor cancelMonitor) {
    var storage = storageService.connection(connectionId);
    var serverVersion = getSonarServerVersion(serverApi, storage, cancelMonitor);
    var enabledLanguagesToSync = languageSupportRepository.getEnabledLanguagesInConnectedMode().stream().filter(SonarLanguage::shouldSyncInConnectedMode)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    var hotspotsUpdater = new ServerHotspotUpdater(storage, new HotspotDownloader(enabledLanguagesToSync));
    hotspotsUpdater.updateForFile(serverApi.hotspot(), projectKey, serverRelativeFilePath, branchName, () -> serverVersion, cancelMonitor);
  }

}
