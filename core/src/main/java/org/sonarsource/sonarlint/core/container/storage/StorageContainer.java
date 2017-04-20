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
package org.sonarsource.sonarlint.core.container.storage;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.Plugin;
import org.sonar.api.internal.ApiVersion;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.TempFolder;
import org.sonar.api.utils.UriReader;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectId;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleListDownloader;
import org.sonarsource.sonarlint.core.container.global.ExtensionInstaller;
import org.sonarsource.sonarlint.core.container.global.GlobalTempFolderProvider;
import org.sonarsource.sonarlint.core.container.storage.partialupdate.PartialUpdater;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginJarExploder;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginRepository;
import org.sonarsource.sonarlint.core.plugin.PluginClassloaderFactory;
import org.sonarsource.sonarlint.core.plugin.PluginCopier;
import org.sonarsource.sonarlint.core.plugin.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.PluginLoader;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class StorageContainer extends ComponentContainer {
  private static final Logger LOG = LoggerFactory.getLogger(StorageContainer.class);

  public static StorageContainer create(ConnectedGlobalConfiguration globalConfig) {
    StorageContainer container = new StorageContainer();
    container.add(globalConfig);
    return container;
  }

  @Override
  protected void doBeforeStart() {
    add(
      // storage directories and tmp
      StorageManager.class,
      new GlobalTempFolderProvider(),

      // plugins
      DefaultPluginRepository.class,
      PluginCopier.class,
      PluginLoader.class,
      PluginClassloaderFactory.class,
      DefaultPluginJarExploder.class,
      StoragePluginIndexProvider.class,
      new PluginCacheProvider(),

      // storage readers
      AllModulesReader.class,
      IssueStoreReader.class,
      ProjectStorageStatusReader.class,
      StorageRuleDetailsReader.class,
      IssueStoreFactory.class,

      // analysis
      StorageAnalyzer.class,

      // needed during analysis (immutable)
      UriReader.class,
      ExtensionInstaller.class,
      new StorageRulesProvider(),
      new StorageQProfilesProvider(),
      new SonarQubeRulesProvider(),
      SonarRuntimeImpl.forSonarLint(ApiVersion.load(System2.INSTANCE)),
      System2.INSTANCE);
  }

  @Override
  protected void doAfterStart() {
    ConnectedGlobalConfiguration config = getComponentByType(ConnectedGlobalConfiguration.class);
    GlobalStorageStatus updateStatus = getGlobalStorageStatus();
    if (updateStatus != null) {
      LOG.info("Using storage for server '{}' (last update {})", config.getServerId(),
        new SimpleDateFormat().format(updateStatus.getLastUpdateDate()));
      installPlugins();
    } else {
      LOG.warn("No storage for server '{}'. Please update.", config.getServerId());
    }
  }

  protected void installPlugins() {
    DefaultPluginRepository pluginRepository = getComponentByType(DefaultPluginRepository.class);
    for (PluginInfo pluginInfo : pluginRepository.getPluginInfos()) {
      Plugin instance = pluginRepository.getPluginInstance(pluginInfo.getKey());
      addExtension(pluginInfo, instance);
    }
  }

  public AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener) {
    return getComponentByType(StorageAnalyzer.class).analyze(this, configuration, issueListener);
  }

  public RuleDetails getRuleDetails(String ruleKeyStr) {
    return getComponentByType(StorageRuleDetailsReader.class).apply(ruleKeyStr);
  }

  public GlobalStorageStatus getGlobalStorageStatus() {
    return getComponentByType(StorageManager.class).getGlobalStorageStatus();
  }

  public ProjectStorageStatus getProjectStorageStatus(ProjectId projectId) {
    return getComponentByType(ProjectStorageStatusReader.class).readStatus(projectId);
  }

  public Map<String, RemoteModule> allModulesByKey() {
    return getComponentByType(AllModulesReader.class).get();
  }

  public List<ServerIssue> getServerIssues(ProjectId projectId, String filePath) {
    return getComponentByType(IssueStoreReader.class).getServerIssues(projectId, filePath);
  }

  public List<ServerIssue> downloadServerIssues(ServerConfiguration serverConfig, ProjectId projectId, String filePath) {
    IssueStoreReader issueStoreReader = getComponentByType(IssueStoreReader.class);
    StorageManager storageManager = getComponentByType(StorageManager.class);
    PartialUpdater updater = PartialUpdater.create(storageManager, serverConfig, issueStoreReader);
    updater.updateFileIssues(projectId, filePath);
    return getServerIssues(projectId, filePath);
  }

  public void downloadServerIssues(ServerConfiguration serverConfig, ProjectId projectId) {
    IssueStoreReader issueStoreReader = getComponentByType(IssueStoreReader.class);
    StorageManager storageManager = getComponentByType(StorageManager.class);
    PartialUpdater updater = PartialUpdater.create(storageManager, serverConfig, issueStoreReader);
    TempFolder tempFolder = getComponentByType(TempFolder.class);
    updater.updateFileIssues(projectId, tempFolder);
  }

  public Map<String, RemoteModule> downloadModuleList(ServerConfiguration serverConfig, ProgressWrapper progress) {
    ModuleListDownloader moduleListDownloader = getComponentByType(ModuleListDownloader.class);
    StorageManager storageManager = getComponentByType(StorageManager.class);
    try {
      moduleListDownloader.fetchModulesListTo(storageManager.getGlobalStorageRoot(), storageManager.readServerInfosFromStorage().getVersion());
    } catch (Exception e) {
      // null as cause so that it doesn't get wrapped
      throw new DownloadException("Failed to update module list: " + e.getMessage(), null);
    }
    return allModulesByKey();
  }

  public void deleteStorage() {
    StorageManager storageManager = getComponentByType(StorageManager.class);
    FileUtils.deleteRecursively(storageManager.getServerStorageRoot());
  }
}
