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
package org.sonarsource.sonarlint.core.container.storage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.global.GlobalExtensionContainer;
import org.sonarsource.sonarlint.core.container.storage.partialupdate.PartialUpdater;
import org.sonarsource.sonarlint.core.container.storage.partialupdate.PartialUpdaterFactory;
import org.sonarsource.sonarlint.core.plugin.PluginRepository;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules.ActiveRule;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.project.ServerProject;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class StorageContainerHandler {
  private final StorageAnalyzer storageAnalyzer;
  private final GlobalUpdateStatusReader globalUpdateStatusReader;
  private final PluginRepository pluginRepository;
  private final ProjectStorageStatusReader projectStorageStatusReader;
  private final AllProjectReader allProjectReader;
  private final StoragePaths storagePaths;
  private final StorageReader storageReader;
  private final StorageFileExclusions storageExclusions;
  private final IssueStoreReader issueStoreReader;
  private final PartialUpdaterFactory partialUpdaterFactory;

  public StorageContainerHandler(StorageAnalyzer storageAnalyzer, GlobalUpdateStatusReader globalUpdateStatusReader,
    PluginRepository pluginRepository, ProjectStorageStatusReader projectStorageStatusReader, AllProjectReader allProjectReader, StoragePaths storagePaths,
    StorageReader storageReader, StorageFileExclusions storageExclusions, IssueStoreReader issueStoreReader, PartialUpdaterFactory partialUpdaterFactory) {
    this.storageAnalyzer = storageAnalyzer;
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

  public ConnectedRuleDetails getRuleDetails(String ruleKeyStr) {
    return getRuleDetailsWithSeverity(ruleKeyStr, null);
  }

  private ConnectedRuleDetails getRuleDetailsWithSeverity(String ruleKeyStr, @Nullable String overridenSeverity) {
    Sonarlint.Rules.Rule rule = readRule(ruleKeyStr);
    String type = StringUtils.isEmpty(rule.getType()) ? null : rule.getType();

    Language language = Language.forKey(rule.getLang()).orElseThrow(() -> new IllegalArgumentException("Unknown language for rule " + ruleKeyStr + ": " + rule.getLang()));
    return new DefaultRuleDetails(ruleKeyStr, rule.getName(), rule.getHtmlDesc(), overridenSeverity != null ? overridenSeverity : rule.getSeverity(), type, language,
      rule.getHtmlNote());
  }

  private Sonarlint.Rules.Rule readRule(String ruleKeyStr) {
    Sonarlint.Rules rulesFromStorage = storageReader.readRules();
    RuleKey ruleKey = RuleKey.parse(ruleKeyStr);
    Sonarlint.Rules.Rule rule = rulesFromStorage.getRulesByKeyMap().get(ruleKeyStr);
    if (rule == null) {
      throw new IllegalArgumentException("Unable to find rule with key " + ruleKey);
    }
    return rule;
  }

  public ConnectedRuleDetails getRuleDetails(String ruleKeyStr, @Nullable String projectKey) {
    QProfiles qProfiles = storageReader.readQProfiles();
    Map<String, String> qProfilesByLanguage;
    if (projectKey == null) {
      qProfilesByLanguage = qProfiles.getDefaultQProfilesByLanguageMap();
    } else {
      qProfilesByLanguage = storageReader.readProjectConfig(projectKey).getQprofilePerLanguageMap();
    }
    for (String qProfileKey : qProfilesByLanguage.values()) {
      Sonarlint.ActiveRules activeRulesFromStorage = storageReader.readActiveRules(qProfileKey);
      if (activeRulesFromStorage.getActiveRulesByKeyMap().containsKey(ruleKeyStr)) {
        ActiveRule ar = activeRulesFromStorage.getActiveRulesByKeyMap().get(ruleKeyStr);
        return getRuleDetailsWithSeverity(ruleKeyStr, ar.getSeverity());
      }
    }
    throw new IllegalArgumentException("Unable to find active rule with key " + ruleKeyStr);
  }

  public GlobalStorageStatus getGlobalStorageStatus() {
    return globalUpdateStatusReader.get();
  }

  public Collection<PluginDetails> getPluginDetails() {
    return pluginRepository.getPluginDetails();
  }

  public ProjectStorageStatus getProjectStorageStatus(String projectKey) {
    return projectStorageStatusReader.apply(projectKey);
  }

  public Map<String, ServerProject> allProjectsByKey() {
    return allProjectReader.get();
  }

  public List<ServerIssue> getServerIssues(ProjectBinding projectBinding, String ideFilePath) {
    return issueStoreReader.getServerIssues(projectBinding, ideFilePath);
  }

  public <G> List<G> getExcludedFiles(ProjectBinding projectBinding, Collection<G> files, Function<G, String> ideFilePathExtractor, Predicate<G> testFilePredicate) {
    return storageExclusions.getExcludedFiles(projectBinding, files, ideFilePathExtractor, testFilePredicate);
  }

  public List<ServerIssue> downloadServerIssues(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath,
    boolean fetchTaintVulnerabilities, ProgressWrapper progress) {
    PartialUpdater updater = partialUpdaterFactory.create(endpoint, client);
    Sonarlint.ProjectConfiguration configuration = storageReader.readProjectConfig(projectBinding.projectKey());
    updater.updateFileIssues(projectBinding, configuration, ideFilePath, fetchTaintVulnerabilities, progress);
    return getServerIssues(projectBinding, ideFilePath);
  }

  public void downloadServerIssues(EndpointParams endpoint, HttpClient client, String projectKey, boolean fetchTaintVulnerabilities, ProgressWrapper progress) {
    PartialUpdater updater = partialUpdaterFactory.create(endpoint, client);
    Sonarlint.ProjectConfiguration configuration = storageReader.readProjectConfig(projectKey);
    updater.updateFileIssues(projectKey, configuration, fetchTaintVulnerabilities, progress);
  }

  public ProjectBinding calculatePathPrefixes(String projectKey, Collection<String> ideFilePaths) {
    List<Path> idePathList = ideFilePaths.stream()
      .map(Paths::get)
      .collect(Collectors.toList());
    List<Path> sqPathList = storageReader.readProjectComponents(projectKey)
      .getComponentList().stream()
      .map(Paths::get)
      .collect(Collectors.toList());

    FileMatcher fileMatcher = new FileMatcher();
    FileMatcher.Result match = fileMatcher.match(sqPathList, idePathList);
    return new ProjectBinding(projectKey, FilenameUtils.separatorsToUnix(match.sqPrefix().toString()),
      FilenameUtils.separatorsToUnix(match.idePrefix().toString()));

  }

  public Map<String, ServerProject> downloadProjectList(EndpointParams endpoint, HttpClient client, ProgressWrapper progress) {
    PartialUpdater updater = partialUpdaterFactory.create(endpoint, client);
    updater.updateProjectList(progress);
    return allProjectsByKey();
  }

  public void deleteStorage() {
    FileUtils.deleteRecursively(storagePaths.getServerStorageRoot());
  }

}
