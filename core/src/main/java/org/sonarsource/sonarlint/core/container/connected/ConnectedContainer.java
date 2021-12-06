/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleHierarchyDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectFileListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.perform.GlobalStorageUpdateExecutor;
import org.sonarsource.sonarlint.core.container.connected.update.perform.ProjectStorageUpdateExecutor;
import org.sonarsource.sonarlint.core.container.connected.update.perform.ServerIssueUpdater;
import org.sonarsource.sonarlint.core.container.storage.GlobalStores;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.container.storage.ProjectStorageStatusReader;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsMinVersions;
import org.sonarsource.sonarlint.core.plugin.commons.pico.ComponentContainer;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverapi.source.SourceApi;
import org.sonarsource.sonarlint.core.serverapi.system.ServerVersionAndStatusChecker;

public class ConnectedContainer extends ComponentContainer {

  private final GlobalStores globalStores;
  private final EndpointParams endpoint;
  private final ConnectedGlobalConfiguration globalConfig;
  private final HttpClient client;

  public ConnectedContainer(ConnectedGlobalConfiguration globalConfig, GlobalStores globalStores, EndpointParams endpoint, HttpClient client) {
    this.globalConfig = globalConfig;
    this.globalStores = globalStores;
    this.endpoint = endpoint;
    this.client = client;
  }

  @Override
  protected void doBeforeStart() {
    add(
      globalConfig,
      endpoint,
      globalStores,
      globalStores.getGlobalStorage(),
      ServerVersionAndStatusChecker.class,
      PluginsMinVersions.class,
      new ServerApiHelper(endpoint, client),
      GlobalStorageUpdateExecutor.class,
      ProjectConfigurationDownloader.class,
      ProjectStorageUpdateExecutor.class,
      ProjectFileListDownloader.class,
      ServerIssueUpdater.class,
      IssueStorePaths.class,
      ProjectListDownloader.class,
      ModuleHierarchyDownloader.class,
      IssueDownloader.class,
      IssueApi.class,
      SourceApi.class,
      IssueStoreFactory.class,
      ProjectStoragePaths.class,
      StorageReader.class,
      ProjectStorageStatusReader.class);
  }

  public void update(ProgressMonitor progress) {
    getComponentByType(GlobalStorageUpdateExecutor.class).update(progress);
  }

  public void updateProject(String projectKey, boolean fetchTaintVulnerabilities, @Nullable GlobalStorageStatus globalStorageStatus, ProgressMonitor progress) {
    if (globalStorageStatus == null || globalStorageStatus.isStale()) {
      throw new StorageException("Missing or outdated storage for connection '" + globalConfig.getConnectionId() + "'");
    }
    getComponentByType(ProjectStorageUpdateExecutor.class).update(projectKey, fetchTaintVulnerabilities, progress);
  }

}
