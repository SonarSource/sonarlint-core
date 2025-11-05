/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.common.ImpactPayload;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.serverconnection.AnalyzerConfiguration;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.RuleSet;
import org.sonarsource.sonarlint.core.serverconnection.Settings;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import org.sonarsource.sonarlint.core.serverconnection.storage.RWLock;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil.writeToFile;

/**
 * Protobuf-based implementation of AnalyzerConfigurationRepository.
 */
public class ProtobufAnalyzerConfigurationRepository implements AnalyzerConfigurationRepository {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Path storageRoot;

  public ProtobufAnalyzerConfigurationRepository(Path storageRoot) {
    this.storageRoot = storageRoot;
  }

  private Path getStorageFilePath(String connectionId, String projectKey) {
    var connectionStorageRoot = storageRoot.resolve(encodeForFs(connectionId));
    var projectsStorageRoot = connectionStorageRoot.resolve("projects");
    var projectStorageRoot = projectsStorageRoot.resolve(encodeForFs(projectKey));
    return projectStorageRoot.resolve("analyzer_config.pb");
  }

  @Override
  public boolean hasActiveRules(String connectionId, String projectKey) {
    var storageFilePath = getStorageFilePath(connectionId, projectKey);
    if (!Files.exists(storageFilePath)) {
      LOG.debug("Analyzer configuration storage doesn't exist: {}", storageFilePath);
      return false;
    }
    return tryRead(connectionId, projectKey).isPresent();
  }

  @Override
  public boolean hasSettings(String connectionId, String projectKey) {
    return hasActiveRules(connectionId, projectKey);
  }

  @Override
  public void store(String connectionId, String projectKey, AnalyzerConfiguration analyzerConfiguration) {
    var storageFilePath = getStorageFilePath(connectionId, projectKey);
    FileUtils.mkdirs(storageFilePath.getParent());
    var data = adapt(analyzerConfiguration);
    LOG.debug("Storing project analyzer configuration in {}", storageFilePath);
    new RWLock().write(() -> writeToFile(data, storageFilePath));
    LOG.debug("Stored project analyzer configuration");
  }

  private Optional<AnalyzerConfiguration> tryRead(String connectionId, String projectKey) {
    try {
      return Optional.of(read(connectionId, projectKey));
    } catch (Exception e) {
      LOG.debug("Could not load analyzer configuration storage", e);
      return Optional.empty();
    }
  }

  @Override
  public AnalyzerConfiguration read(String connectionId, String projectKey) {
    var storageFilePath = getStorageFilePath(connectionId, projectKey);
    return adapt(new RWLock().read(() -> readConfiguration(storageFilePath)));
  }

  @Override
  public void update(String connectionId, String projectKey, UnaryOperator<AnalyzerConfiguration> updater) {
    var storageFilePath = getStorageFilePath(connectionId, projectKey);
    FileUtils.mkdirs(storageFilePath.getParent());
    new RWLock().write(() -> {
      Sonarlint.AnalyzerConfiguration config;
      try {
        config = readConfiguration(storageFilePath);
      } catch (StorageException e) {
        LOG.warn("Unable to read storage. Creating a new one.", e);
        config = Sonarlint.AnalyzerConfiguration.newBuilder().build();
      }
      writeToFile(adapt(updater.apply(adapt(config))), storageFilePath);
      LOG.debug("Storing project data in {}", storageFilePath);
    });
  }

  private static Sonarlint.AnalyzerConfiguration readConfiguration(Path projectFilePath) {
    return ProtobufFileUtil.readFile(projectFilePath, Sonarlint.AnalyzerConfiguration.parser());
  }

  private static AnalyzerConfiguration adapt(Sonarlint.AnalyzerConfiguration analyzerConfiguration) {
    return new AnalyzerConfiguration(
      new Settings(analyzerConfiguration.getSettingsMap()),
      analyzerConfiguration.getRuleSetsByLanguageKeyMap().entrySet().stream().collect(Collectors.toMap(
        Map.Entry::getKey,
        e -> adapt(e.getValue()))),
      analyzerConfiguration.getSchemaVersion());
  }

  private static Sonarlint.AnalyzerConfiguration adapt(AnalyzerConfiguration analyzerConfiguration) {
    return Sonarlint.AnalyzerConfiguration.newBuilder()
      .setSchemaVersion(analyzerConfiguration.getSchemaVersion())
      .putAllSettings(analyzerConfiguration.getSettings().getAll())
      .putAllRuleSetsByLanguageKey(analyzerConfiguration.getRuleSetByLanguageKey().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> adapt(e.getValue()))))
      .build();
  }

  private static RuleSet adapt(Sonarlint.RuleSet ruleSet) {
    return new RuleSet(
      ruleSet.getRuleList().stream().map(ProtobufAnalyzerConfigurationRepository::adapt).toList(),
      ruleSet.getLastModified());
  }

  private static ServerActiveRule adapt(Sonarlint.RuleSet.ActiveRule rule) {
    return new ServerActiveRule(
      rule.getRuleKey(),
      IssueSeverity.valueOf(rule.getSeverity()),
      rule.getParamsMap(),
      rule.getTemplateKey(),
      rule.getOverriddenImpactsList().stream()
        .map(impact -> new ImpactPayload(impact.getSoftwareQuality(), impact.getSeverity()))
        .toList());
  }

  private static Sonarlint.RuleSet adapt(RuleSet ruleSet) {
    return Sonarlint.RuleSet.newBuilder()
      .setLastModified(ruleSet.getLastModified())
      .addAllRule(ruleSet.getRules().stream().map(ProtobufAnalyzerConfigurationRepository::adapt).toList()).build();
  }

  private static Sonarlint.RuleSet.ActiveRule adapt(ServerActiveRule rule) {
    return Sonarlint.RuleSet.ActiveRule.newBuilder()
      .setRuleKey(rule.ruleKey())
      .setSeverity(rule.severity().name())
      .setTemplateKey(rule.templateKey())
      .putAllParams(rule.params())
      .addAllOverriddenImpacts(rule.overriddenImpacts().stream()
        .map(impact -> Sonarlint.RuleSet.ActiveRule.newBuilder().addOverriddenImpactsBuilder()
          .setSoftwareQuality(impact.softwareQuality())
          .setSeverity(impact.severity())
          .build())
        .toList())
      .build();
  }
}
