/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.serverconnection.AnalyzerConfiguration;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBranches;
import org.sonarsource.sonarlint.core.serverconnection.RuleSet;
import org.sonarsource.sonarlint.core.serverconnection.Settings;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufUtil.writeToFile;

public class ProjectStorage {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Path projectsRootPath;
  private final RWLock rwLock = new RWLock();

  public ProjectStorage(Path projectsRootPath) {
    this.projectsRootPath = projectsRootPath;
  }

  public void store(String projectKey, AnalyzerConfiguration analyzerConfiguration) {
    var pbFilePath = getAnalyzerConfigFilePath(projectKey);
    FileUtils.mkdirs(pbFilePath.getParent());
    var data = adapt(analyzerConfiguration);
    LOG.debug("Storing project analyzer configuration in {}", pbFilePath);
    rwLock.write(() -> writeToFile(data, pbFilePath));
  }

  public AnalyzerConfiguration getAnalyzerConfiguration(String projectKey) {
    var projectFilePath = getAnalyzerConfigFilePath(projectKey);
    return adapt(rwLock.read(() -> readConfiguration(projectFilePath)));
  }

  public void store(String projectKey, ProjectBranches projectBranches) {
    var pbFilePath = getProjectBranchesFilePath(projectKey);
    FileUtils.mkdirs(pbFilePath.getParent());
    var data = adapt(projectBranches);
    LOG.debug("Storing project branches in {}", pbFilePath);
    rwLock.write(() -> writeToFile(data, pbFilePath));
  }

  public ProjectBranches getProjectBranches(String projectKey) {
    var pbFilePath = getProjectBranchesFilePath(projectKey);
    return adapt(rwLock.read(() -> ProtobufUtil.readFile(pbFilePath, Sonarlint.ProjectBranches.parser())));
  }

  private static ProjectBranches adapt(Sonarlint.ProjectBranches projectBranches) {
    return new ProjectBranches(Set.copyOf(projectBranches.getBranchNameList()), projectBranches.getMainBranchName());
  }

  private static Sonarlint.ProjectBranches adapt(ProjectBranches projectBranches) {
    return Sonarlint.ProjectBranches.newBuilder()
      .addAllBranchName(projectBranches.getBranchNames())
      .setMainBranchName(projectBranches.getMainBranchName())
      .build();
  }

  public void update(String projectKey, UnaryOperator<AnalyzerConfiguration> updater) {
    var projectFilePath = getAnalyzerConfigFilePath(projectKey);
    FileUtils.mkdirs(projectFilePath.getParent());
    rwLock.write(() -> {
      Sonarlint.AnalyzerConfiguration config;
      try {
        config = readConfiguration(projectFilePath);
      } catch (StorageException e) {
        LOG.warn("Unable to read storage. Creating a new one.", e);
        config = Sonarlint.AnalyzerConfiguration.newBuilder().build();
      }
      writeToFile(adapt(updater.apply(adapt(config))), projectFilePath);
      LOG.debug("Storing project data in {}", projectFilePath);
    });
  }

  private static Sonarlint.AnalyzerConfiguration readConfiguration(Path projectFilePath) {
    return ProtobufUtil.readFile(projectFilePath, Sonarlint.AnalyzerConfiguration.parser());
  }

  private static AnalyzerConfiguration adapt(Sonarlint.AnalyzerConfiguration analyzerConfiguration) {
    return new AnalyzerConfiguration(
      new Settings(analyzerConfiguration.getSettingsMap()),
      analyzerConfiguration.getRuleSetsByLanguageKeyMap().entrySet().stream().collect(Collectors.toMap(
        Map.Entry::getKey,
        e -> adapt(e.getValue()))), analyzerConfiguration.getSchemaVersion());
  }

  private static RuleSet adapt(Sonarlint.RuleSet ruleSet) {
    return new RuleSet(
      ruleSet.getRuleList().stream().map(ProjectStorage::adapt).collect(Collectors.toList()),
      ruleSet.getLastModified());
  }

  private static ServerActiveRule adapt(Sonarlint.RuleSet.ActiveRule rule) {
    return new ServerActiveRule(
      rule.getRuleKey(),
      IssueSeverity.valueOf(rule.getSeverity()),
      rule.getParamsMap(),
      rule.getTemplateKey());
  }

  private static Sonarlint.AnalyzerConfiguration adapt(AnalyzerConfiguration analyzerConfiguration) {
    return Sonarlint.AnalyzerConfiguration.newBuilder()
      .setSchemaVersion(analyzerConfiguration.getSchemaVersion())
      .putAllSettings(analyzerConfiguration.getSettings().getAll())
      .putAllRuleSetsByLanguageKey(analyzerConfiguration.getRuleSetByLanguageKey().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> adapt(e.getValue()))))
      .build();
  }

  private static Sonarlint.RuleSet adapt(RuleSet ruleSet) {
    return Sonarlint.RuleSet.newBuilder()
      .setLastModified(ruleSet.getLastModified())
      .addAllRule(ruleSet.getRules().stream().map(ProjectStorage::adapt).collect(Collectors.toList())).build();
  }

  private static Sonarlint.RuleSet.ActiveRule adapt(ServerActiveRule rule) {
    return Sonarlint.RuleSet.ActiveRule.newBuilder()
      .setRuleKey(rule.getRuleKey())
      .setSeverity(rule.getSeverity().name())
      .setTemplateKey(rule.getTemplateKey())
      .putAllParams(rule.getParams())
      .build();
  }

  private Path getAnalyzerConfigFilePath(String projectKey) {
    return projectsRootPath.resolve(encodeForFs(projectKey)).resolve("analyzer_config.pb");
  }

  private Path getProjectBranchesFilePath(String projectKey) {
    return projectsRootPath.resolve(encodeForFs(projectKey)).resolve("project_branches.pb");
  }

}
