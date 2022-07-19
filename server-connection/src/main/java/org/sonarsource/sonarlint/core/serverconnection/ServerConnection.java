/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2022 SonarSource SA
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.RuleSetChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.serverconnection.events.EventDispatcher;
import org.sonarsource.sonarlint.core.serverconnection.events.ServerEventsAutoSubscriber;
import org.sonarsource.sonarlint.core.serverconnection.events.issue.UpdateStorageOnIssueChanged;
import org.sonarsource.sonarlint.core.serverconnection.events.ruleset.UpdateStorageOnRuleSetChanged;
import org.sonarsource.sonarlint.core.serverconnection.events.taint.UpdateStorageOnTaintVulnerabilityRaised;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.prefix.FileTreeMatcher;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageReader;
import org.sonarsource.sonarlint.core.serverconnection.storage.XodusServerIssueStore;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

public class ServerConnection {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final String connectionId;
  private final Set<Language> enabledLanguages;
  private final ProjectStorage projectStorage;
  private final StorageReader storageReader;
  private final PluginsStorage pluginsStorage;
  private final IssueStoreReader issueStoreReader;
  private final LocalStorageSynchronizer storageSynchronizer;
  private final ProjectStorageUpdateExecutor projectStorageUpdateExecutor;
  private final ServerEventsAutoSubscriber serverEventsAutoSubscriber;
  private final ServerIssueUpdater issuesUpdater;
  private final XodusServerIssueStore serverIssueStore;
  private final boolean isSonarCloud;

  private final Path connectionStorageRoot;

  public ServerConnection(Path globalStorageRoot, String connectionId, boolean isSonarCloud, Set<Language> enabledLanguages, Set<String> embeddedPluginKeys) {
    this.connectionId = connectionId;
    this.isSonarCloud = isSonarCloud;
    this.enabledLanguages = enabledLanguages;

    connectionStorageRoot = globalStorageRoot.resolve(encodeForFs(connectionId));

    var projectStoragePaths = new ProjectStoragePaths(connectionStorageRoot);

    var projectsStorageRoot = connectionStorageRoot.resolve("projects");
    projectStorage = new ProjectStorage(projectsStorageRoot);

    this.storageReader = new StorageReader(projectStoragePaths);
    serverIssueStore = new XodusServerIssueStore(projectsStorageRoot);
    this.issueStoreReader = new IssueStoreReader(serverIssueStore);
    this.issuesUpdater = new ServerIssueUpdater(serverIssueStore, new IssueDownloader(enabledLanguages), new TaintIssueDownloader(enabledLanguages));
    this.pluginsStorage = new PluginsStorage(connectionStorageRoot.resolve("plugins"));
    this.storageSynchronizer = new LocalStorageSynchronizer(enabledLanguages, embeddedPluginKeys, pluginsStorage, projectStorage);
    this.projectStorageUpdateExecutor = new ProjectStorageUpdateExecutor(projectStoragePaths);
    pluginsStorage.cleanUp();
    var eventRouter = new EventDispatcher()
      .dispatch(RuleSetChangedEvent.class, new UpdateStorageOnRuleSetChanged(projectStorage))
      .dispatch(IssueChangedEvent.class, new UpdateStorageOnIssueChanged(serverIssueStore))
      .dispatch(TaintVulnerabilityRaisedEvent.class, new UpdateStorageOnTaintVulnerabilityRaised(serverIssueStore));
    this.serverEventsAutoSubscriber = new ServerEventsAutoSubscriber(eventRouter);
  }

  public Map<String, Path> getStoredPluginPathsByKey() {
    return pluginsStorage.getStoredPluginPathsByKey();
  }

  public AnalyzerConfiguration getAnalyzerConfiguration(String projectKey) {
    return projectStorage.getAnalyzerConfiguration(projectKey);
  }

  public ProjectBranches getProjectBranches(String projectKey) {
    return projectStorage.getProjectBranches(projectKey);
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

  public void subscribeForEvents(EndpointParams endpoint, HttpClient client, Set<String> projectKeys, ClientLogOutput clientLogOutput) {
    serverEventsAutoSubscriber.subscribePermanently(new ServerApi(new ServerApiHelper(endpoint, client)), projectKeys, enabledLanguages, clientLogOutput);
  }

  public ProjectBinding calculatePathPrefixes(String projectKey, Collection<String> ideFilePaths) {
    List<Path> idePathList = ideFilePaths.stream()
      .map(Paths::get)
      .collect(Collectors.toList());
    List<Path> sqPathList = storageReader.readProjectComponents(projectKey)
      .getComponentList().stream()
      .map(Paths::get)
      .collect(Collectors.toList());
    var fileMatcher = new FileTreeMatcher();
    var match = fileMatcher.match(sqPathList, idePathList);
    return new ProjectBinding(projectKey, FilenameUtils.separatorsToUnix(match.sqPrefix().toString()),
      FilenameUtils.separatorsToUnix(match.idePrefix().toString()));
  }

  public void downloadServerIssuesForFile(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath, @Nullable String branchName) {
    var serverApi = new ServerApi(new ServerApiHelper(endpoint, client));
    var serverVersion = checkStatusAndGetServerVersion(serverApi);
    issuesUpdater.updateFileIssues(serverApi, projectBinding, ideFilePath, branchName, isSonarCloud, serverVersion);
  }

  public void downloadServerTaintIssuesForFile(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath, @Nullable String branchName,
    ProgressMonitor progress) {
    var serverApi = new ServerApi(new ServerApiHelper(endpoint, client));
    var serverVersion = checkStatusAndGetServerVersion(serverApi);
    issuesUpdater.updateFileTaints(serverApi, projectBinding, ideFilePath, branchName, isSonarCloud, serverVersion, progress);
  }

  private static Version checkStatusAndGetServerVersion(ServerApi serverApi) {
    var status = new ServerVersionAndStatusChecker(serverApi).checkVersionAndStatus();
    return Version.create(status.getVersion());
  }

  public void downloadServerIssuesForProject(EndpointParams endpoint, HttpClient client, String projectKey, String branchName) {
    var serverApi = new ServerApi(new ServerApiHelper(endpoint, client));
    var serverVersion = checkStatusAndGetServerVersion(serverApi);
    issuesUpdater.update(serverApi, projectKey, branchName, isSonarCloud, serverVersion);
  }

  public void syncServerIssuesForProject(EndpointParams endpoint, HttpClient client, String projectKey, String branchName) {
    var serverApi = new ServerApi(new ServerApiHelper(endpoint, client));
    var serverVersion = checkStatusAndGetServerVersion(serverApi);
    if (IssueApi.supportIssuePull(isSonarCloud, serverVersion)) {
      LOG.info("[SYNC] Synchronizing issues for project '{}' on branch '{}'", projectKey, branchName);
      issuesUpdater.sync(serverApi, projectKey, branchName);
    } else {
      LOG.debug("Incremental issue sync is not supported. Skipping.");
    }
  }

  public void syncServerTaintIssuesForProject(EndpointParams endpoint, HttpClient client, String projectKey, String branchName) {
    var serverApi = new ServerApi(new ServerApiHelper(endpoint, client));
    var serverVersion = checkStatusAndGetServerVersion(serverApi);
    if (IssueApi.supportIssuePull(isSonarCloud, serverVersion)) {
      LOG.info("[SYNC] Synchronizing taint issues for project '{}' on branch '{}'", projectKey, branchName);
      issuesUpdater.syncTaints(serverApi, projectKey, branchName);
    } else {
      LOG.debug("Incremental taint issue sync is not supported. Skipping.");
    }
  }

  public void updateProject(EndpointParams endpoint, HttpClient client, String projectKey, ProgressMonitor monitor) {
    projectStorageUpdateExecutor.update(new ServerApi(new ServerApiHelper(endpoint, client)), projectKey, monitor);
  }

  public void stop(boolean deleteStorage) {
    serverEventsAutoSubscriber.stop();
    serverIssueStore.close();
    if (deleteStorage) {
      FileUtils.deleteRecursively(connectionStorageRoot);
    }
  }

}
