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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.LoadedAnalyzer;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteProject;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.global.GlobalExtensionContainer;
import org.sonarsource.sonarlint.core.container.storage.partialupdate.PartialUpdater;
import org.sonarsource.sonarlint.core.container.storage.partialupdate.PartialUpdaterFactory;
import org.sonarsource.sonarlint.core.plugin.PluginRepository;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.ReversePathTree;

public class StorageContainerHandler {
  private final StorageAnalyzer storageAnalyzer;
  private final StorageRuleDetailsReader storageRuleDetailsReader;
  private final GlobalUpdateStatusReader globalUpdateStatusReader;
  private final PluginRepository pluginRepository;
  private final ProjectStorageStatusReader projectStorageStatusReader;
  private final AllProjectReader allProjectReader;
  private final StoragePaths storagePaths;
  private final StorageReader storageReader;
  private final StorageFileExclusions storageExclusions;
  private final IssueStoreReader issueStoreReader;
  private final PartialUpdaterFactory partialUpdaterFactory;

  public StorageContainerHandler(StorageAnalyzer storageAnalyzer, StorageRuleDetailsReader storageRuleDetailsReader, GlobalUpdateStatusReader globalUpdateStatusReader,
    PluginRepository pluginRepository, ProjectStorageStatusReader projectStorageStatusReader, AllProjectReader allProjectReader, StoragePaths storagePaths,
    StorageReader storageReader, StorageFileExclusions storageExclusions, IssueStoreReader issueStoreReader, PartialUpdaterFactory partialUpdaterFactory) {
    this.storageAnalyzer = storageAnalyzer;
    this.storageRuleDetailsReader = storageRuleDetailsReader;
    this.globalUpdateStatusReader = globalUpdateStatusReader;
    this.pluginRepository = pluginRepository;
    this.projectStorageStatusReader = projectStorageStatusReader;
    this.allProjectReader = allProjectReader;
    this.storagePaths = storagePaths;
    this.storageReader = storageReader;
    this.storageExclusions = storageExclusions;
    this.issueStoreReader = issueStoreReader;
    this.partialUpdaterFactory = partialUpdaterFactory;
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

  public List<ServerIssue> getServerIssues(ProjectBinding projectBinding, String filePath) {
    return issueStoreReader.getServerIssues(projectBinding, filePath);
  }

  public Set<String> getExcludedFiles(String projectKey, Collection<String> filePaths, Predicate<String> testFilePredicate) {
    return storageExclusions.getExcludedFiles(projectKey, filePaths, testFilePredicate);
  }

  public List<ServerIssue> downloadServerIssues(ServerConfiguration serverConfig, ProjectBinding projectBinding, String filePath) {
    PartialUpdater updater = partialUpdaterFactory.create(serverConfig);
    Sonarlint.ProjectConfiguration configuration = storageReader.readProjectConfig(projectBinding.projectKey());
    updater.updateFileIssues(projectBinding, configuration, filePath);
    return getServerIssues(projectBinding, filePath);
  }

  public void downloadServerIssues(ServerConfiguration serverConfig, String projectKey) {
    PartialUpdater updater = partialUpdaterFactory.create(serverConfig);
    Sonarlint.ProjectConfiguration configuration = storageReader.readProjectConfig(projectKey);
    updater.updateFileIssues(projectKey, configuration);
  }

  public ProjectBinding calculatePathPrefixes(String projectKey, Collection<String> localFilePaths) {
    List<Path> localPathList = localFilePaths.stream()
      .map(Paths::get)
      .collect(Collectors.toList());
    List<Path> sqPathList = storageReader.readProjectComponents(projectKey)
      .getComponentList().stream()
      .map(Paths::get)
      .collect(Collectors.toList());

    FileMatcher fileMatcher = new FileMatcher(new ReversePathTree());
    FileMatcher.Result match = fileMatcher.match(sqPathList, localPathList);
    return new ProjectBinding(projectKey, FilenameUtils.separatorsToUnix(match.mostCommonSqPrefix().toString()),
      FilenameUtils.separatorsToUnix(match.mostCommonLocalPrefix().toString()));

  }

  public Map<String, RemoteProject> downloadProjectList(ServerConfiguration serverConfig, ProgressWrapper progress) {
    PartialUpdater updater = partialUpdaterFactory.create(serverConfig);
    updater.updateProjectList(progress);
    return allProjectsByKey();
  }

  public void deleteStorage() {
    FileUtils.deleteRecursively(storagePaths.getServerStorageRoot());
  }

}
