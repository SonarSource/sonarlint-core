/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;

public class ServerConnection {
  private static final Version SECRET_ANALYSIS_MIN_SQ_VERSION = Version.create("9.9");

  private static final Version CLEAN_CODE_TAXONOMY_MIN_SQ_VERSION = Version.create("10.2");

  private final Set<SonarLanguage> enabledLanguagesToSync;
  private final LocalStorageSynchronizer storageSynchronizer;
  private final boolean isSonarCloud;
  private final ServerInfoSynchronizer serverInfoSynchronizer;
  private final ConnectionStorage storage;

  public ServerConnection(Path globalStorageRoot, String connectionId, boolean isSonarCloud, Set<SonarLanguage> enabledLanguages, Set<String> embeddedPluginKeys, Path workDir) {
    this(StorageFacadeCache.get().getOrCreate(globalStorageRoot, workDir), connectionId, isSonarCloud, enabledLanguages, embeddedPluginKeys);
  }

  public ServerConnection(StorageFacade storageFacade, String connectionId, boolean isSonarCloud, Set<SonarLanguage> enabledLanguages, Set<String> embeddedPluginKeys) {
    this.isSonarCloud = isSonarCloud;
    this.enabledLanguagesToSync = enabledLanguages.stream().filter(SonarLanguage::shouldSyncInConnectedMode).collect(Collectors.toCollection(LinkedHashSet::new));

    this.storage = storageFacade.connection(connectionId);
    serverInfoSynchronizer = new ServerInfoSynchronizer(storage);
    this.storageSynchronizer = new LocalStorageSynchronizer(enabledLanguagesToSync, embeddedPluginKeys, serverInfoSynchronizer, storage);
    storage.plugins().cleanUp();
  }

  public Map<String, Path> getStoredPluginPathsByKey() {
    return storage.plugins().getStoredPluginPathsByKey();
  }

  public AnalyzerConfiguration getAnalyzerConfiguration(String projectKey) {
    return storage.project(projectKey).analyzerConfiguration().read();
  }

  public boolean sync(ServerApi serverApi) {
    return storageSynchronizer.synchronize(serverApi);
  }

  public AnalyzerSettingsUpdateSummary sync(ServerApi serverApi, String projectKey) {
    return storageSynchronizer.synchronize(serverApi, projectKey);
  }

  public SynchronizationResult sync(ServerApi serverApi, Set<String> projectKeys, ProgressMonitor monitor) {
    return storageSynchronizer.synchronize(serverApi, projectKeys, monitor);
  }

  public Version readOrSynchronizeServerVersion(ServerApi serverApi) {
    return serverInfoSynchronizer.readOrSynchronizeServerInfo(serverApi).getVersion();
  }

  public boolean permitsHotspotTracking() {
    // when storage is not present, consider hotspots should not be detected
    return storage.serverInfo().read()
      .map(serverInfo -> HotspotApi.permitsTracking(isSonarCloud, serverInfo::getVersion))
      .orElse(false);
  }

  public boolean supportsSecretAnalysis() {
    // when storage is not present, assume that secrets are not supported by server
    return isSonarCloud || storage.serverInfo().read()
      .map(serverInfo -> serverInfo.getVersion().compareToIgnoreQualifier(SECRET_ANALYSIS_MIN_SQ_VERSION) >= 0)
      .orElse(false);
  }

  public boolean shouldSkipCleanCodeTaxonomy() {
    // In connected mode, Clean Code taxonomy is skipped if the server is SonarQube < 10.2
    return !isSonarCloud && storage.serverInfo().read()
      .map(serverInfo -> serverInfo.getVersion().compareToIgnoreQualifier(CLEAN_CODE_TAXONOMY_MIN_SQ_VERSION) < 0)
      .orElse(false);
  }

  public Set<SonarLanguage> getEnabledLanguagesToSync() {
    return enabledLanguagesToSync;
  }
}
