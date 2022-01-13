/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.RWLock;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;

import static org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths.encodeForFs;
import static org.sonarsource.sonarlint.core.container.storage.ProtobufUtil.writeToFile;

public class ProjectStorage {
  private static final Logger LOG = Loggers.get(ProjectStorage.class);

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
    return adapt(rwLock.read(() -> !Files.exists(pbFilePath) ? Sonarlint.ProjectBranches.newBuilder().build()
      : ProtobufUtil.readFile(pbFilePath, Sonarlint.ProjectBranches.parser())));
  }

  private ProjectBranches adapt(Sonarlint.ProjectBranches projectBranches) {
    return new ProjectBranches(Set.copyOf(projectBranches.getBranchNameList()), Optional.ofNullable(StringUtils.trimToNull(projectBranches.getMainBranchName())));
  }

  private Sonarlint.ProjectBranches adapt(ProjectBranches projectBranches) {
    return Sonarlint.ProjectBranches.newBuilder()
      .addAllBranchName(projectBranches.getBranchNames())
      .setMainBranchName(projectBranches.getMainBranchName().orElse(""))
      .build();
  }

  public void update(String projectKey, UnaryOperator<AnalyzerConfiguration> updater) {
    var projectFilePath = getAnalyzerConfigFilePath(projectKey);
    FileUtils.mkdirs(projectFilePath.getParent());
    rwLock.write(() -> {
      writeToFile(adapt(updater.apply(adapt(readConfiguration(projectFilePath)))), projectFilePath);
      LOG.debug("Storing project data in {}", projectFilePath);
    });
  }

  private static Sonarlint.AnalyzerConfiguration readConfiguration(Path projectFilePath) {
    return !Files.exists(projectFilePath) ? Sonarlint.AnalyzerConfiguration.newBuilder().build()
      : ProtobufUtil.readFile(projectFilePath, Sonarlint.AnalyzerConfiguration.parser());
  }

  private static AnalyzerConfiguration adapt(Sonarlint.AnalyzerConfiguration analyzerConfiguration) {
    return new AnalyzerConfiguration(
      new Settings(analyzerConfiguration.getSettingsMap()),
      analyzerConfiguration.getRuleSetsByLanguageKeyMap().entrySet().stream().collect(Collectors.toMap(
        Map.Entry::getKey,
        e -> adapt(e.getValue()))));
  }

  private static RuleSet adapt(Sonarlint.RuleSet ruleSet) {
    return new RuleSet(
      ruleSet.getRulesList().stream().map(ProjectStorage::adapt).collect(Collectors.toList()),
      ruleSet.getLastModified());
  }

  private static ServerActiveRule adapt(Sonarlint.RuleSet.ActiveRule rule) {
    return new ServerActiveRule(
      rule.getRuleKey(),
      rule.getSeverity(),
      rule.getParamsMap(),
      rule.getTemplateKey());
  }

  private static Sonarlint.AnalyzerConfiguration adapt(AnalyzerConfiguration analyzerConfiguration) {
    return Sonarlint.AnalyzerConfiguration.newBuilder()
      .putAllSettings(analyzerConfiguration.getSettings().getAll())
      .putAllRuleSetsByLanguageKey(analyzerConfiguration.getRuleSetByLanguageKey().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> adapt(e.getValue()))))
      .build();
  }

  private static Sonarlint.RuleSet adapt(RuleSet ruleSet) {
    return Sonarlint.RuleSet.newBuilder()
      .setLastModified(ruleSet.getLastModified())
      .addAllRules(ruleSet.getRules().stream().map(ProjectStorage::adapt).collect(Collectors.toList())).build();
  }

  private static Sonarlint.RuleSet.ActiveRule adapt(ServerActiveRule rule) {
    return Sonarlint.RuleSet.ActiveRule.newBuilder()
      .setRuleKey(rule.getRuleKey())
      .setSeverity(rule.getSeverity())
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
