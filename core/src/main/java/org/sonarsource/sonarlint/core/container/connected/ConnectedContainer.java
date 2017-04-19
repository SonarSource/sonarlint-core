/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectId;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloaderImpl;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleHierarchyDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.PluginReferencesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectQualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.QualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.RulesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.SettingsDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.check.GlobalSettingsUpdateChecker;
import org.sonarsource.sonarlint.core.container.connected.update.check.GlobalStorageUpdateChecker;
import org.sonarsource.sonarlint.core.container.connected.update.check.ProjectStorageUpdateChecker;
import org.sonarsource.sonarlint.core.container.connected.update.check.PluginsUpdateChecker;
import org.sonarsource.sonarlint.core.container.connected.update.check.QualityProfilesUpdateChecker;
import org.sonarsource.sonarlint.core.container.connected.update.perform.GlobalStorageUpdateExecutor;
import org.sonarsource.sonarlint.core.container.connected.update.perform.ModuleStorageUpdateExecutor;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.global.GlobalTempFolderProvider;
import org.sonarsource.sonarlint.core.container.storage.ProjectStorageStatusReader;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;
import org.sonarsource.sonarlint.core.plugin.cache.PluginHashes;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class ConnectedContainer extends ComponentContainer {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectedContainer.class);

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
      ModuleStorageUpdateExecutor.class,
      PluginReferencesDownloader.class,
      SettingsDownloader.class,
      ProjectQualityProfilesDownloader.class,
      ModuleListDownloader.class,
      ModuleHierarchyDownloader.class,
      RulesDownloader.class,
      QualityProfilesDownloader.class,
      IssueDownloaderImpl.class,
      IssueStoreFactory.class,
      new PluginCacheProvider(),
      PluginHashes.class,
      StorageManager.class,
      ProjectStorageStatusReader.class);
  }

  public void update(ProgressWrapper progress) {
    getComponentByType(GlobalStorageUpdateExecutor.class).update(progress);
  }

  public void updateModule(ProjectId projectId) {
    GlobalStorageStatus updateStatus = getComponentByType(StorageManager.class).getGlobalStorageStatus();
    if (updateStatus == null) {
      throw new IllegalStateException("Please update server first");
    }
    getComponentByType(ModuleStorageUpdateExecutor.class).update(projectId);
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

  public StorageUpdateCheckResult checkForUpdate(ProjectId projectId, ProgressWrapper progress) {
    ProjectStorageStatus moduleUpdateStatus = getComponentByType(ProjectStorageStatusReader.class).readStatus(projectId);
    if (moduleUpdateStatus == null || moduleUpdateStatus.isStale()) {
      throw new StorageException(String.format("No data stored for module '%s' or invalid format. Please update the binding.", projectId), false);
    }
    try {
      return getComponentByType(ProjectStorageUpdateChecker.class).checkForUpdates(projectId, progress);
    } catch (Exception e) {
      String msg = "Error when checking for configuration update of module '" + projectId + "'";
      LOG.debug(msg, e);
      // null as cause so that it doesn't get wrapped
      throw new DownloadException(msg + ": " + e.getMessage(), null);
    }
  }

}
