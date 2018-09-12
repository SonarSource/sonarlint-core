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
package org.sonarsource.sonarlint.core.container.storage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.LoadedAnalyzer;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteProject;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStoreUtils;
import org.sonarsource.sonarlint.core.container.global.GlobalExtensionContainer;
import org.sonarsource.sonarlint.core.container.storage.partialupdate.PartialUpdater;
import org.sonarsource.sonarlint.core.plugin.PluginRepository;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class StorageContainerHandler {
  private final StorageAnalyzer storageAnalyzer;
  private final StorageRuleDetailsReader storageRuleDetailsReader;
  private final GlobalUpdateStatusReader globalUpdateStatusReader;
  private final PluginRepository pluginRepository;
  private final ProjectStorageStatusReader projectStorageStatusReader;
  private final AllProjectReader allProjectReader;
  private final StoragePaths storagePaths;
  private final TempFolder tempFolder;
  private final StorageReader storageReader;
  private final StorageFileExclusions storageExclusions;
  private final IssueStoreReader issueStoreReader;

  public StorageContainerHandler(StorageAnalyzer storageAnalyzer, StorageRuleDetailsReader storageRuleDetailsReader, GlobalUpdateStatusReader globalUpdateStatusReader,
    PluginRepository pluginRepository, ProjectStorageStatusReader projectStorageStatusReader, AllProjectReader allProjectReader,
    StoragePaths storagePaths, StorageReader storageReader, TempFolder tempFolder, StorageFileExclusions storageExclusions,
    IssueStoreReader issueStoreReader) {
    this.storageAnalyzer = storageAnalyzer;
    this.storageRuleDetailsReader = storageRuleDetailsReader;
    this.globalUpdateStatusReader = globalUpdateStatusReader;
    this.pluginRepository = pluginRepository;
    this.projectStorageStatusReader = projectStorageStatusReader;
    this.allProjectReader = allProjectReader;
    this.storagePaths = storagePaths;
    this.storageReader = storageReader;
    this.tempFolder = tempFolder;
    this.storageExclusions = storageExclusions;
    this.issueStoreReader = issueStoreReader;
  }

  public AnalysisResults analyze(GlobalExtensionContainer globalExtensionContainer, ConnectedAnalysisConfiguration configuration, IssueListener issueListener,
    ProgressWrapper progress) {
    return storageAnalyzer.analyze(globalExtensionContainer, configuration, issueListener, progress);
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

  public ProjectStorageStatus getProjectStorageStatus(String projectKey) {
    return projectStorageStatusReader.apply(projectKey);
  }

  public Map<String, RemoteProject> allProjectsByKey() {
    return allProjectReader.get();
  }

  public List<ServerIssue> getServerIssues(String moduleKey, String filePath) {
    return issueStoreReader.getServerIssues(moduleKey, filePath);
  }

  public Set<String> getExcludedFiles(String moduleKey, Collection<String> filePaths, Predicate<String> testFilePredicate) {
    return storageExclusions.getExcludedFiles(moduleKey, filePaths, testFilePredicate);
  }

  public List<ServerIssue> downloadServerIssues(ServerConfiguration serverConfig, String moduleKey, String filePath) {
    PartialUpdater updater = PartialUpdater.create(storageReader, storagePaths, serverConfig, this::createIsseStoreUtils);
    updater.updateFileIssues(moduleKey, filePath);
    return getServerIssues(moduleKey, filePath);
  }

  public void downloadServerIssues(ServerConfiguration serverConfig, String moduleKey) {
    PartialUpdater updater = PartialUpdater.create(storageReader, storagePaths, serverConfig, this::createIsseStoreUtils);
    updater.updateFileIssues(moduleKey, tempFolder);
  }

  public Map<String, RemoteProject> downloadProjectList(ServerConfiguration serverConfig, ProgressWrapper progress) {

    PartialUpdater updater = PartialUpdater.create(storageReader, storagePaths, serverConfig, this::createIsseStoreUtils);
    updater.updateModuleList(progress);
    return allProjectsByKey();
  }

  private IssueStoreUtils createIsseStoreUtils(String projectKey) {
    Sonarlint.ProjectPathPrefixes pathPrefixes = storageReader.readProjectPathPrefixes(projectKey);
    Sonarlint.ProjectConfiguration configuration = storageReader.readProjectConfig(projectKey);
    IssueStoreUtils issueStoreUtils = new IssueStoreUtils(configuration, pathPrefixes);
    return issueStoreUtils;
  }

  public void deleteStorage() {
    FileUtils.deleteRecursively(storagePaths.getServerStorageRoot());
  }

}
