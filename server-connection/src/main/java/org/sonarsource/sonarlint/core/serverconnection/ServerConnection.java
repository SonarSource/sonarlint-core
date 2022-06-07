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
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverapi.push.RuleSetChangedEvent;
import org.sonarsource.sonarlint.core.serverconnection.events.EventDispatcher;
import org.sonarsource.sonarlint.core.serverconnection.events.ServerEventsAutoSubscriber;
import org.sonarsource.sonarlint.core.serverconnection.prefix.FileTreeMatcher;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStorageStatusReader;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageReader;
import org.sonarsource.sonarlint.core.serverconnection.storage.XodusServerIssueStore;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

public class ServerConnection {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final String connectionId;
  private final Set<Language> enabledLanguages;
  private final ProjectStorageStatusReader projectStorageStatusReader;
  private final GlobalStores globalStores;
  private final GlobalUpdateStatusReader globalStatusReader;
  private final ProjectStorage projectStorage;
  private final StorageReader storageReader;
  private final PluginsStorage pluginsStorage;
  private final IssueStoreReader issueStoreReader;
  private final LocalStorageSynchronizer storageSynchronizer;
  private final GlobalStorageUpdateExecutor globalStorageUpdateExecutor;
  private final ProjectStorageUpdateExecutor projectStorageUpdateExecutor;
  private final ServerEventsAutoSubscriber serverEventsAutoSubscriber;
  private final ServerIssueUpdater issuesUpdater;
  private final XodusServerIssueStore serverIssueStore;

  public ServerConnection(Path globalStorageRoot, String connectionId, Set<Language> enabledLanguages, Set<String> embeddedPluginKeys) {
    this.connectionId = connectionId;
    this.enabledLanguages = enabledLanguages;

    var connectionStorageRoot = globalStorageRoot.resolve(encodeForFs(connectionId));

    this.globalStores = new GlobalStores(connectionStorageRoot);
    this.globalStatusReader = new GlobalUpdateStatusReader(globalStores.getServerInfoStore(), globalStores.getStorageStatusStore());

    var projectStoragePaths = new ProjectStoragePaths(connectionStorageRoot);
    this.projectStorageStatusReader = new ProjectStorageStatusReader(projectStoragePaths);

    var projectsStorageRoot = connectionStorageRoot.resolve("projects");
    projectStorage = new ProjectStorage(projectsStorageRoot);

    this.storageReader = new StorageReader(projectStoragePaths);
    serverIssueStore = new XodusServerIssueStore(projectsStorageRoot);
    this.issueStoreReader = new IssueStoreReader(serverIssueStore);
    this.issuesUpdater = new ServerIssueUpdater(serverIssueStore, new IssueDownloader());
    this.pluginsStorage = new PluginsStorage(connectionStorageRoot.resolve("plugins"));
    this.storageSynchronizer = new LocalStorageSynchronizer(enabledLanguages, embeddedPluginKeys, pluginsStorage, projectStorage);
    this.globalStorageUpdateExecutor = new GlobalStorageUpdateExecutor(globalStores.getGlobalStorage());
    this.projectStorageUpdateExecutor = new ProjectStorageUpdateExecutor(projectStoragePaths, issuesUpdater);
    pluginsStorage.cleanUp();
    var eventRouter = new EventDispatcher()
      .dispatch(RuleSetChangedEvent.class, new UpdateStorageOnRuleSetChanged(projectStorage));
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

  public void checkStatus(@Nullable String projectKey) {
    var updateStatus = globalStatusReader.read();
    if (updateStatus == null) {
      throw new StorageException("Missing storage for connection");
    }
    if (updateStatus.isStale()) {
      throw new StorageException("Outdated storage for connection");
    }
    if (projectKey != null) {
      var projectUpdateStatus = getProjectStorageStatus(projectKey);
      if (projectUpdateStatus == null) {
        throw new StorageException(String.format("No storage for project '%s'. Please update the binding.", projectKey));
      } else if (projectUpdateStatus.isStale()) {
        throw new StorageException(String.format("Stored data for project '%s' is stale because "
          + "it was created with a different version of SonarLint. Please update the binding.", projectKey));
      }
    }
  }

  public ProjectStorageStatus getProjectStorageStatus(String projectKey) {
    return projectStorageStatusReader.apply(projectKey);
  }

  public Map<String, ServerProject> downloadAllProjects(EndpointParams endpoint, HttpClient client, ProgressMonitor monitor) {
    try {
      return new ProjectListDownloader(new ServerApiHelper(endpoint, client), globalStores.getServerProjectsStore()).fetch(monitor);
    } catch (Exception e) {
      // null as cause so that it doesn't get wrapped
      throw new DownloadException("Failed to update project list: " + e.getMessage(), null);
    }
  }

  public GlobalStorageStatus getGlobalStorageStatus() {
    return globalStatusReader.read();
  }

  public SynchronizationResult sync(EndpointParams endpoint, HttpClient client, Set<String> projectKeys, ProgressMonitor monitor) {
    var serverApi = new ServerApi(new ServerApiHelper(endpoint, client));
    return storageSynchronizer.synchronize(serverApi, projectKeys, monitor);
  }

  public GlobalStorageStatus update(EndpointParams endpoint, HttpClient client, ProgressMonitor monitor) {
    globalStorageUpdateExecutor.update(new ServerApiHelper(endpoint, client), monitor);
    return globalStatusReader.read();
  }

  public Map<String, ServerProject> allProjectsByKey() {
    try {
      return globalStores.getServerProjectsStore().getAll();
    } catch (StorageException e) {
      LOG.error("Unable to read projects keys from the storage", e);
      return Map.of();
    }
  }

  public List<ServerIssue> getServerIssues(ProjectBinding projectBinding, String ideFilePath) {
    return issueStoreReader.getServerIssues(projectBinding, ideFilePath);
  }

  public List<ServerTaintIssue> getServerTaintIssues(ProjectBinding projectBinding, String ideFilePath) {
    return issueStoreReader.getServerTaintIssues(projectBinding, ideFilePath);
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

  public List<ServerIssue> downloadServerIssuesForFile(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath, @Nullable String branchName,
    ProgressMonitor progress) {
    issuesUpdater.updateFileIssues(new ServerApiHelper(endpoint, client), projectBinding, ideFilePath, branchName, progress);
    return getServerIssues(projectBinding, ideFilePath);
  }

  public void downloadServerIssuesForProject(EndpointParams endpoint, HttpClient client, String projectKey, @Nullable String branchName,
    ProgressMonitor progress) {
    issuesUpdater.update(new ServerApiHelper(endpoint, client), projectKey, branchName, progress);
  }

  public void updateProject(EndpointParams endpoint, HttpClient client, String projectKey, @Nullable String branchName, ProgressMonitor monitor) {
    var globalStorageStatus = globalStatusReader.read();
    if (globalStorageStatus == null || globalStorageStatus.isStale()) {
      throw new StorageException("Missing or outdated storage for connection '" + connectionId + "'");
    }
    projectStorageUpdateExecutor.update(new ServerApiHelper(endpoint, client), projectKey, branchName, monitor);
  }

  public void stop(boolean deleteStorage) {
    serverEventsAutoSubscriber.stop();
    serverIssueStore.close();
    if (deleteStorage) {
      globalStores.deleteAll();
    }
  }

}
