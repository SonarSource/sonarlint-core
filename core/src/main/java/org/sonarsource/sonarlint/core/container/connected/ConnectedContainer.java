/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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

import java.util.List;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalUpdateRequiredException;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloaderImpl;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.container.connected.update.PluginListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.PluginReferencesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectFileListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectHierarchyDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectQualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.QualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.RulesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.SettingsDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.check.GlobalSettingsUpdateChecker;
import org.sonarsource.sonarlint.core.container.connected.update.check.GlobalStorageUpdateChecker;
import org.sonarsource.sonarlint.core.container.connected.update.check.PluginsUpdateChecker;
import org.sonarsource.sonarlint.core.container.connected.update.check.ProjectStorageUpdateChecker;
import org.sonarsource.sonarlint.core.container.connected.update.check.QualityProfilesUpdateChecker;
import org.sonarsource.sonarlint.core.container.connected.update.perform.GlobalStorageUpdateExecutor;
import org.sonarsource.sonarlint.core.container.connected.update.perform.ProjectStorageUpdateExecutor;
import org.sonarsource.sonarlint.core.container.connected.update.perform.ServerIssueUpdater;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.global.GlobalTempFolderProvider;
import org.sonarsource.sonarlint.core.container.storage.ProjectStorageStatusReader;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;
import org.sonarsource.sonarlint.core.plugin.cache.PluginHashes;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class ConnectedContainer extends ComponentContainer {

  private static final Logger LOG = Loggers.get(ConnectedContainer.class);

  private final ServerConfiguration serverConfiguration;
  private final ConnectedGlobalConfiguration globalConfig;

  public ConnectedContainer(ConnectedGlobalConfiguration globalConfig, ServerConfiguration serverConfiguration) {
    this.globalConfig = globalConfig;
    this.serverConfiguration = serverConfiguration;
  }

  @Override
  protected void doBeforeStart() {
    add(
      globalConfig,
      serverConfiguration,
      new GlobalTempFolderProvider(),
      ServerVersionAndStatusChecker.class,
      PluginVersionChecker.class,
      SonarLintWsClient.class,
      GlobalStorageUpdateExecutor.class,
      GlobalStorageUpdateChecker.class,
      ProjectStorageUpdateChecker.class,
      PluginsUpdateChecker.class,
      GlobalSettingsUpdateChecker.class,
      ProjectConfigurationDownloader.class,
      QualityProfilesUpdateChecker.class,
      ProjectStorageUpdateExecutor.class,
      ProjectFileListDownloader.class,
      ServerIssueUpdater.class,
      IssueStorePaths.class,
      PluginReferencesDownloader.class,
      SettingsDownloader.class,
      ProjectQualityProfilesDownloader.class,
      ProjectListDownloader.class,
      PluginListDownloader.class,
      ProjectHierarchyDownloader.class,
      RulesDownloader.class,
      QualityProfilesDownloader.class,
      IssueDownloaderImpl.class,
      IssueStoreFactory.class,
      new PluginCacheProvider(),
      PluginHashes.class,
      StoragePaths.class,
      StorageReader.class,
      ProjectStorageStatusReader.class);
  }

  public List<SonarAnalyzer> update(ProgressWrapper progress) {
    return getComponentByType(GlobalStorageUpdateExecutor.class).update(progress);
  }

  public void updateProject(String projectKey, ProgressWrapper progress) {
    GlobalStorageStatus updateStatus = getComponentByType(StorageReader.class).getGlobalStorageStatus();
    if (updateStatus == null) {
      throw new GlobalUpdateRequiredException("Please update server first");
    }
    getComponentByType(ProjectStorageUpdateExecutor.class).update(projectKey, progress);
  }

  public StorageUpdateCheckResult checkForUpdate(ProgressWrapper progress) {
    try {
      return getComponentByType(GlobalStorageUpdateChecker.class).checkForUpdate(progress);
    } catch (Exception e) {
      String msg = "Error when checking for global configuration update";
      LOG.debug(msg, e);
      // null as cause so that it doesn't get wrapped
      throw new DownloadException(msg + ": " + e.getMessage(), null);
    }
  }

  public StorageUpdateCheckResult checkForUpdate(String projectKey, ProgressWrapper progress) {
    ProjectStorageStatus moduleUpdateStatus = getComponentByType(ProjectStorageStatusReader.class).apply(projectKey);
    if (moduleUpdateStatus == null || moduleUpdateStatus.isStale()) {
      throw new StorageException(String.format("No data stored for project '%s' or invalid format. Please update the binding.", projectKey), false);
    }
    try {
      return getComponentByType(ProjectStorageUpdateChecker.class).checkForUpdates(projectKey, progress);
    } catch (Exception e) {
      String msg = "Error when checking for configuration update of project '" + projectKey + "'";
      LOG.debug(msg, e);
      // null as cause so that it doesn't get wrapped
      throw new DownloadException(msg + ": " + e.getMessage(), null);
    }
  }

}
