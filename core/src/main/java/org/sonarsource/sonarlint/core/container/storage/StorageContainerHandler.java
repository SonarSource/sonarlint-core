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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.LoadedAnalyzer;
import org.sonarsource.sonarlint.core.client.api.connected.ModuleStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.storage.partialupdate.PartialUpdater;
import org.sonarsource.sonarlint.core.plugin.PluginRepository;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class StorageContainerHandler {
  private final StorageAnalyzer storageAnalyzer;
  private final StorageRuleDetailsReader storageRuleDetailsReader;
  private final GlobalUpdateStatusReader globalUpdateStatusReader;
  private final PluginRepository pluginRepository;
  private final ModuleStorageStatusReader moduleStorageStatusReader;
  private final IssueStoreReader issueStoreReader;
  private final AllModulesReader allModulesReader;
  private final StoragePaths storagePaths;
  private final TempFolder tempFolder;
  private final StorageReader storageReader;

  public StorageContainerHandler(StorageAnalyzer storageAnalyzer, StorageRuleDetailsReader storageRuleDetailsReader, GlobalUpdateStatusReader globalUpdateStatusReader,
    PluginRepository pluginRepository, ModuleStorageStatusReader moduleStorageStatusReader, IssueStoreReader issueStoreReader, AllModulesReader allModulesReader,
    StoragePaths storagePaths, StorageReader storageReader, TempFolder tempFolder) {
    this.storageAnalyzer = storageAnalyzer;
    this.storageRuleDetailsReader = storageRuleDetailsReader;
    this.globalUpdateStatusReader = globalUpdateStatusReader;
    this.pluginRepository = pluginRepository;
    this.moduleStorageStatusReader = moduleStorageStatusReader;
    this.issueStoreReader = issueStoreReader;
    this.allModulesReader = allModulesReader;
    this.storagePaths = storagePaths;
    this.storageReader = storageReader;
    this.tempFolder = tempFolder;
  }

  public AnalysisResults analyze(StorageContainer container, ConnectedAnalysisConfiguration configuration, IssueListener issueListener) {
    return storageAnalyzer.analyze(container, configuration, issueListener);
  }

  public RuleDetails getRuleDetails(String ruleKeyStr) {
    return storageRuleDetailsReader.apply(ruleKeyStr);
  }

  public GlobalStorageStatus getGlobalStorageStatus() {
    return globalUpdateStatusReader.get();
  }

  public Collection<LoadedAnalyzer> getAnalyzers() {
    return pluginRepository.getLoadedAnalyzers();
  }

  public ModuleStorageStatus getModuleStorageStatus(String moduleKey) {
    return moduleStorageStatusReader.apply(moduleKey);
  }

  public Map<String, RemoteModule> allModulesByKey() {
    return allModulesReader.get();
  }

  public List<ServerIssue> getServerIssues(String moduleKey, String filePath) {
    return issueStoreReader.getServerIssues(moduleKey, filePath);
  }

  public List<ServerIssue> downloadServerIssues(ServerConfiguration serverConfig, String moduleKey, String filePath) {
    PartialUpdater updater = PartialUpdater.create(storageReader, storagePaths, serverConfig, issueStoreReader);
    updater.updateFileIssues(moduleKey, filePath);
    return getServerIssues(moduleKey, filePath);
  }

  public void downloadServerIssues(ServerConfiguration serverConfig, String moduleKey) {
    PartialUpdater updater = PartialUpdater.create(storageReader, storagePaths, serverConfig, issueStoreReader);
    updater.updateFileIssues(moduleKey, tempFolder);
  }

  public Map<String, RemoteModule> downloadModuleList(ServerConfiguration serverConfig, ProgressWrapper progress) {
    PartialUpdater updater = PartialUpdater.create(storageReader, storagePaths, serverConfig, issueStoreReader);
    updater.updateModuleList(progress);
    return allModulesByKey();
  }

  public void deleteStorage() {
    FileUtils.deleteRecursively(storagePaths.getServerStorageRoot());
  }
}
