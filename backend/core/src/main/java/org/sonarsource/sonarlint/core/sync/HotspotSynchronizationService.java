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
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.core.serverconnection.HotspotDownloader;
import org.sonarsource.sonarlint.core.serverconnection.ServerHotspotUpdater;
import org.sonarsource.sonarlint.core.storage.StorageService;

@Named
@Singleton
public class HotspotSynchronizationService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final StorageService storageService;
  private final LanguageSupportRepository languageSupportRepository;
  private final ConnectionManager connectionManager;

  public HotspotSynchronizationService(StorageService storageService, LanguageSupportRepository languageSupportRepository, ConnectionManager connectionManager) {
    this.storageService = storageService;
    this.languageSupportRepository = languageSupportRepository;
    this.connectionManager = connectionManager;
  }

  public void syncServerHotspotsForProject(ServerApiErrorHandlingWrapper serverApiWrapper, String connectionId, String projectKey, String branchName,
    SonarLintCancelMonitor cancelMonitor) {
    var storage = storageService.getStorageFacade().connection(connectionId);
    var serverVersion = connectionManager.getSonarServerVersion(connectionId, storage, cancelMonitor);
    var enabledLanguagesToSync = languageSupportRepository.getEnabledLanguagesInConnectedMode().stream().filter(SonarLanguage::shouldSyncInConnectedMode)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    var hotspotsUpdater = new ServerHotspotUpdater(storage, new HotspotDownloader(enabledLanguagesToSync));
    if (HotspotApi.supportHotspotsPull(serverApiWrapper.isSonarCloud(), serverVersion)) {
      LOG.info("[SYNC] Synchronizing hotspots for project '{}' on branch '{}'", projectKey, branchName);
      hotspotsUpdater.sync(serverApiWrapper, projectKey, branchName, enabledLanguagesToSync, cancelMonitor);
    } else {
      LOG.debug("Incremental hotspot sync is not supported. Skipping.");
    }
  }

  public void fetchProjectHotspots(Binding binding, String activeBranch, SonarLintCancelMonitor cancelMonitor) {
    if (connectionManager.hasConnection(binding.getConnectionId())) {
      downloadAllServerHotspots(binding, activeBranch, cancelMonitor);
    }
  }

  private void downloadAllServerHotspots(Binding binding, String branchName, SonarLintCancelMonitor cancelMonitor) {
    var connectionId = binding.getConnectionId();
    var projectKey = binding.getSonarProjectKey();
    var storage = storageService.getStorageFacade().connection(connectionId);
    var enabledLanguagesToSync = languageSupportRepository.getEnabledLanguagesInConnectedMode().stream().filter(SonarLanguage::shouldSyncInConnectedMode)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    var hotspotsUpdater = new ServerHotspotUpdater(storage, new HotspotDownloader(enabledLanguagesToSync));
    var serverApiWrapper = connectionManager.getServerApiWrapperOrThrow(connectionId);
    hotspotsUpdater.updateAll(serverApiWrapper, projectKey, branchName, cancelMonitor);
  }

  public void fetchFileHotspots(Binding binding, String activeBranch, Path serverFilePath, SonarLintCancelMonitor cancelMonitor) {
    if (connectionManager.hasConnection(binding.getConnectionId())) {
      downloadAllServerHotspotsForFile(binding.getConnectionId(), binding.getSonarProjectKey(), serverFilePath, activeBranch, cancelMonitor);
    }
  }

  private void downloadAllServerHotspotsForFile(String connectionId, String projectKey, Path serverRelativeFilePath, String branchName,
    SonarLintCancelMonitor cancelMonitor) {
    var storage = storageService.getStorageFacade().connection(connectionId);
    var serverVersion = connectionManager.getSonarServerVersion(connectionId, storage, cancelMonitor);
    var enabledLanguagesToSync = languageSupportRepository.getEnabledLanguagesInConnectedMode().stream().filter(SonarLanguage::shouldSyncInConnectedMode)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    var hotspotsUpdater = new ServerHotspotUpdater(storage, new HotspotDownloader(enabledLanguagesToSync));
    var serverApiWrapper = connectionManager.getServerApiWrapperOrThrow(connectionId);
    hotspotsUpdater.updateForFile(serverApiWrapper, projectKey, serverRelativeFilePath, branchName, serverVersion, cancelMonitor);
  }

}
