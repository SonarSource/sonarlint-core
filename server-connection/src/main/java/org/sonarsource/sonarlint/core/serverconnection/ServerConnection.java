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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

public class ServerConnection {
  private static final Version SECRET_ANALYSIS_MIN_SQ_VERSION = Version.create("9.9");

  private static final Version CLEAN_CODE_TAXONOMY_MIN_SQ_VERSION = Version.create("10.2");

  private final Set<Language> enabledLanguagesToSync;
  private final IssueStoreReader issueStoreReader;
  private final LocalStorageSynchronizer storageSynchronizer;
  private final ProjectStorageUpdateExecutor projectStorageUpdateExecutor;
  private final ServerIssueUpdater issuesUpdater;
  private final boolean isSonarCloud;
  private final ServerInfoSynchronizer serverInfoSynchronizer;
  private final ConnectionStorage storage;

  public ServerConnection(Path globalStorageRoot, String connectionId, boolean isSonarCloud, Set<Language> enabledLanguages, Set<String> embeddedPluginKeys, Path workDir) {
    this(StorageFacadeCache.get().getOrCreate(globalStorageRoot, workDir), connectionId, isSonarCloud, enabledLanguages, embeddedPluginKeys);
  }

  public ServerConnection(StorageFacade storageFacade, String connectionId, boolean isSonarCloud, Set<Language> enabledLanguages, Set<String> embeddedPluginKeys) {
    this.isSonarCloud = isSonarCloud;
    this.enabledLanguagesToSync = enabledLanguages.stream().filter(Language::shouldSyncInConnectedMode).collect(Collectors.toCollection(LinkedHashSet::new));

    this.storage = storageFacade.connection(connectionId);
    this.issueStoreReader = new IssueStoreReader(storage);
    this.issuesUpdater = new ServerIssueUpdater(storage, new IssueDownloader(enabledLanguagesToSync), new TaintIssueDownloader(enabledLanguagesToSync));
    serverInfoSynchronizer = new ServerInfoSynchronizer(storage);
    this.storageSynchronizer = new LocalStorageSynchronizer(enabledLanguagesToSync, embeddedPluginKeys, serverInfoSynchronizer, storage);
    this.projectStorageUpdateExecutor = new ProjectStorageUpdateExecutor(storage);
    storage.plugins().cleanUp();
  }

  public Map<String, Path> getStoredPluginPathsByKey() {
    return storage.plugins().getStoredPluginPathsByKey();
  }

  public AnalyzerConfiguration getAnalyzerConfiguration(String projectKey) {
    return storage.project(projectKey).analyzerConfiguration().read();
  }

  public SynchronizationResult sync(EndpointParams endpoint, HttpClient client, Set<String> projectKeys, ProgressMonitor monitor) {
    return sync(new ServerApi(new ServerApiHelper(endpoint, client)), projectKeys, monitor);
  }

  public boolean sync(ServerApi serverApi) {
    return storageSynchronizer.synchronize(serverApi);
  }

  public void sync(ServerApi serverApi, String projectKey) {
    storageSynchronizer.synchronize(serverApi, projectKey);
  }

  public SynchronizationResult sync(ServerApi serverApi, Set<String> projectKeys, ProgressMonitor monitor) {
    return storageSynchronizer.synchronize(serverApi, projectKeys, monitor);
  }

  public List<ServerIssue> getServerIssues(ProjectBinding projectBinding, String branchName, String ideFilePath) {
    return issueStoreReader.getServerIssues(projectBinding, branchName, ideFilePath);
  }

  public Version readOrSynchronizeServerVersion(ServerApi serverApi) {
    return serverInfoSynchronizer.readOrSynchronizeServerInfo(serverApi).getVersion();
  }

  public void downloadServerIssuesForProject(EndpointParams endpoint, HttpClient client, String projectKey, String branchName) {
    downloadServerIssuesForProject(new ServerApi(new ServerApiHelper(endpoint, client)), projectKey, branchName);
  }

  public void downloadServerIssuesForProject(ServerApi serverApi, String projectKey, String branchName) {
    var serverVersion = readOrSynchronizeServerVersion(serverApi);
    issuesUpdater.update(serverApi, projectKey, branchName, isSonarCloud, serverVersion);
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

  public void updateProject(EndpointParams endpoint, HttpClient client, String projectKey, ProgressMonitor monitor) {
    projectStorageUpdateExecutor.update(new ServerApi(new ServerApiHelper(endpoint, client)), projectKey, monitor);
  }
}
