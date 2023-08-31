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
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.commons.push.ServerEvent;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverconnection.events.ServerEventsAutoSubscriber;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.prefix.FileTreeMatcher;

public class ServerConnection {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final Version SECRET_ANALYSIS_MIN_SQ_VERSION = Version.create("9.9");

  private static final Version CLEAN_CODE_TAXONOMY_MIN_SQ_VERSION = Version.create("10.2");

  private final Set<Language> enabledLanguagesToSync;
  private final IssueStoreReader issueStoreReader;
  private final LocalStorageSynchronizer storageSynchronizer;
  private final ProjectStorageUpdateExecutor projectStorageUpdateExecutor;
  private final ServerEventsAutoSubscriber serverEventsAutoSubscriber;
  private final ServerIssueUpdater issuesUpdater;
  private final ServerHotspotUpdater hotspotsUpdater;
  private final boolean isSonarCloud;
  private final ServerInfoSynchronizer serverInfoSynchronizer;
  private final ConnectionStorage storage;
  private final StorageFacade storageFacade;

  public ServerConnection(Path globalStorageRoot, String connectionId, boolean isSonarCloud, Set<Language> enabledLanguages, Set<String> embeddedPluginKeys, Path workDir) {
    this(StorageFacadeCache.get().getOrCreate(globalStorageRoot, workDir), connectionId, isSonarCloud, enabledLanguages, embeddedPluginKeys);
  }

  public ServerConnection(StorageFacade storageFacade, String connectionId, boolean isSonarCloud, Set<Language> enabledLanguages, Set<String> embeddedPluginKeys) {
    this.isSonarCloud = isSonarCloud;
    this.enabledLanguagesToSync = enabledLanguages.stream().filter(Language::shouldSyncInConnectedMode).collect(Collectors.toCollection(LinkedHashSet::new));

    this.storageFacade = storageFacade;
    this.storage = storageFacade.connection(connectionId);
    this.issueStoreReader = new IssueStoreReader(storage);
    this.issuesUpdater = new ServerIssueUpdater(storage, new IssueDownloader(enabledLanguagesToSync), new TaintIssueDownloader(enabledLanguagesToSync));
    this.hotspotsUpdater = new ServerHotspotUpdater(storage, new HotspotDownloader(enabledLanguagesToSync));
    serverInfoSynchronizer = new ServerInfoSynchronizer(storage);
    this.storageSynchronizer = new LocalStorageSynchronizer(enabledLanguagesToSync, embeddedPluginKeys, serverInfoSynchronizer, storage);
    this.projectStorageUpdateExecutor = new ProjectStorageUpdateExecutor(storage);
    storage.plugins().cleanUp();
    this.serverEventsAutoSubscriber = new ServerEventsAutoSubscriber(storage, enabledLanguagesToSync);
  }

  public Map<String, Path> getStoredPluginPathsByKey() {
    return storage.plugins().getStoredPluginPathsByKey();
  }

  public AnalyzerConfiguration getAnalyzerConfiguration(String projectKey) {
    return storage.project(projectKey).analyzerConfiguration().read();
  }

  public ProjectBranches getProjectBranches(String projectKey) {
    return storage.project(projectKey).branches().read();
  }

  public Map<String, ServerProject> downloadAllProjects(EndpointParams endpoint, HttpClient client, ProgressMonitor monitor) {
    try {
      return new ServerApi(endpoint, client).component().getAllProjects(monitor).stream().collect(Collectors.toMap(ServerProject::getKey, p -> p));
    } catch (Exception e) {
      LOG.error("Failed to get project list", e);
    }
    return Map.of();
  }

  public SynchronizationResult sync(EndpointParams endpoint, HttpClient client, Set<String> projectKeys, ProgressMonitor monitor) {
    var serverApi = new ServerApi(new ServerApiHelper(endpoint, client));
    return storageSynchronizer.synchronize(serverApi, projectKeys, monitor);
  }

  public List<ServerIssue> getServerIssues(ProjectBinding projectBinding, String branchName, String ideFilePath) {
    return issueStoreReader.getServerIssues(projectBinding, branchName, ideFilePath);
  }

  public List<ServerTaintIssue> getServerTaintIssues(ProjectBinding projectBinding, String branchName, String ideFilePath) {
    return issueStoreReader.getServerTaintIssues(projectBinding, branchName, ideFilePath);
  }

  public List<ServerTaintIssue> getServerTaintIssues(ProjectBinding projectBinding, String branchName) {
    return issueStoreReader.getRawServerTaintIssues(projectBinding, branchName);
  }

  public void subscribeForEvents(EndpointParams endpoint, HttpClient client, Set<String> projectKeys, Consumer<ServerEvent> clientEventConsumer, ClientLogOutput clientLogOutput) {
    serverEventsAutoSubscriber.subscribePermanently(new ServerApi(new ServerApiHelper(endpoint, client)), projectKeys,
      clientEventConsumer, clientLogOutput);
  }

  public ProjectBinding calculatePathPrefixes(String projectKey, Collection<String> ideFilePaths) {
    List<Path> idePathList = ideFilePaths.stream()
      .map(Paths::get)
      .collect(Collectors.toList());
    List<Path> sqPathList = storage.project(projectKey).components().read()
      .getComponentList().stream()
      .map(Paths::get)
      .collect(Collectors.toList());
    var fileMatcher = new FileTreeMatcher();
    var match = fileMatcher.match(sqPathList, idePathList);
    return new ProjectBinding(projectKey, FilenameUtils.separatorsToUnix(match.sqPrefix().toString()),
      FilenameUtils.separatorsToUnix(match.idePrefix().toString()));
  }

  public void downloadServerIssuesForFile(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath, String branchName) {
    var serverApi = new ServerApi(new ServerApiHelper(endpoint, client));
    var serverVersion = readOrSynchronizeServerVersion(serverApi);
    issuesUpdater.updateFileIssues(serverApi, projectBinding, ideFilePath, branchName, isSonarCloud, serverVersion);
  }

  public void downloadServerIssuesForFile(ServerApi serverApi, String projectKey, String serverFileRelativePath, String branchName) {
    var serverVersion = readOrSynchronizeServerVersion(serverApi);
    issuesUpdater.updateFileIssues(serverApi, projectKey, serverFileRelativePath, branchName, isSonarCloud, serverVersion);
  }

  public void downloadServerTaintIssuesForFile(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath, String branchName,
    ProgressMonitor progress) {
    var serverApi = new ServerApi(new ServerApiHelper(endpoint, client));
    var serverVersion = readOrSynchronizeServerVersion(serverApi);
    issuesUpdater.updateFileTaints(serverApi, projectBinding, ideFilePath, branchName, isSonarCloud, serverVersion, progress);
  }

  private Version readOrSynchronizeServerVersion(ServerApi serverApi) {
    return serverInfoSynchronizer.readOrSynchronizeServerInfo(serverApi).getVersion();
  }

  public void downloadServerIssuesForProject(EndpointParams endpoint, HttpClient client, String projectKey, String branchName) {
    downloadServerIssuesForProject(new ServerApi(new ServerApiHelper(endpoint, client)), projectKey, branchName);
  }

  public void downloadServerIssuesForProject(ServerApi serverApi, String projectKey, String branchName) {
    var serverVersion = readOrSynchronizeServerVersion(serverApi);
    issuesUpdater.update(serverApi, projectKey, branchName, isSonarCloud, serverVersion);
  }

  public void downloadAllServerHotspots(EndpointParams endpoint, HttpClient client, String projectKey, String branchName, ProgressMonitor progress) {
    var serverApi = new ServerApi(new ServerApiHelper(endpoint, client));
    hotspotsUpdater.updateAll(serverApi.hotspot(), projectKey, branchName, () -> readOrSynchronizeServerVersion(serverApi), progress);
  }

  public void downloadAllServerHotspotsForFile(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath, String branchName) {
    var serverApi = new ServerApi(new ServerApiHelper(endpoint, client));
    hotspotsUpdater.updateForFile(serverApi.hotspot(), projectBinding, ideFilePath, branchName, () -> readOrSynchronizeServerVersion(serverApi));
  }

  public Collection<ServerHotspot> getServerHotspots(ProjectBinding projectBinding, String branchName, String ideFilePath) {
    return issueStoreReader.getServerHotspots(projectBinding, branchName, ideFilePath);
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

  public void syncServerIssuesForProject(EndpointParams endpoint, HttpClient client, String projectKey, String branchName) {
    syncServerIssuesForProject(new ServerApi(new ServerApiHelper(endpoint, client)), projectKey, branchName);
  }

  public void syncServerIssuesForProject(ServerApi serverApi, String projectKey, String branchName) {
    var serverVersion = readOrSynchronizeServerVersion(serverApi);
    if (IssueApi.supportIssuePull(isSonarCloud, serverVersion)) {
      LOG.info("[SYNC] Synchronizing issues for project '{}' on branch '{}'", projectKey, branchName);
      issuesUpdater.sync(serverApi, projectKey, branchName, enabledLanguagesToSync);
    } else {
      LOG.debug("Incremental issue sync is not supported. Skipping.");
    }
  }

  public void syncServerTaintIssuesForProject(EndpointParams endpoint, HttpClient client, String projectKey, String branchName) {
    syncServerTaintIssuesForProject(new ServerApi(new ServerApiHelper(endpoint, client)), projectKey, branchName);
  }

  public void syncServerTaintIssuesForProject(ServerApi serverApi, String projectKey, String branchName) {
    var serverVersion = readOrSynchronizeServerVersion(serverApi);
    if (IssueApi.supportIssuePull(isSonarCloud, serverVersion)) {
      LOG.info("[SYNC] Synchronizing taint issues for project '{}' on branch '{}'", projectKey, branchName);
      issuesUpdater.syncTaints(serverApi, projectKey, branchName, enabledLanguagesToSync);
    } else {
      LOG.debug("Incremental taint issue sync is not supported. Skipping.");
    }
  }

  public void syncServerHotspotsForProject(EndpointParams endpoint, HttpClient client, String projectKey, String branchName) {
    syncServerHotspotsForProject(new ServerApi(new ServerApiHelper(endpoint, client)), projectKey, branchName);
  }

  public void syncServerHotspotsForProject(ServerApi serverApi, String projectKey, String branchName) {
    var serverVersion = readOrSynchronizeServerVersion(serverApi);
    if (HotspotApi.supportHotspotsPull(isSonarCloud, serverVersion)) {
      LOG.info("[SYNC] Synchronizing hotspots for project '{}' on branch '{}'", projectKey, branchName);
      hotspotsUpdater.sync(serverApi.hotspot(), projectKey, branchName, enabledLanguagesToSync);
    } else {
      LOG.debug("Incremental hotspot sync is not supported. Skipping.");
    }
  }

  public void updateProject(EndpointParams endpoint, HttpClient client, String projectKey, ProgressMonitor monitor) {
    projectStorageUpdateExecutor.update(new ServerApi(new ServerApiHelper(endpoint, client)), projectKey, monitor);
  }

  public void stop(boolean deleteStorage) {
    serverEventsAutoSubscriber.stop();
    StorageFacadeCache.get().close(storageFacade);
    if (deleteStorage) {
      storage.delete();
    }
  }
}
