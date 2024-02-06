/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import org.sonarsource.sonarlint.core.serverconnection.storage.RWLock;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil.writeToFile;

public class AnalyzerConfigurationStorage {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final RWLock rwLock = new RWLock();
  private final Path storageFilePath;

  public AnalyzerConfigurationStorage(Path projectStorageRoot) {
    this.storageFilePath = projectStorageRoot.resolve("analyzer_config.pb");
  }

  public void store(AnalyzerConfiguration analyzerConfiguration) {
    FileUtils.mkdirs(storageFilePath.getParent());
    var data = adapt(analyzerConfiguration);
    LOG.debug("Storing project analyzer configuration in {}", storageFilePath);
    rwLock.write(() -> writeToFile(data, storageFilePath));
    LOG.debug("Stored project analyzer configuration");
  }

  public AnalyzerConfiguration read() {
    return adapt(rwLock.read(() -> readConfiguration(storageFilePath)));
  }

  public void update(UnaryOperator<AnalyzerConfiguration> updater) {
    FileUtils.mkdirs(storageFilePath.getParent());
    rwLock.write(() -> {
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
      ruleSet.getRuleList().stream().map(AnalyzerConfigurationStorage::adapt).collect(Collectors.toList()),
      ruleSet.getLastModified());
  }

  private static ServerActiveRule adapt(Sonarlint.RuleSet.ActiveRule rule) {
    return new ServerActiveRule(
      rule.getRuleKey(),
      IssueSeverity.valueOf(rule.getSeverity()),
      rule.getParamsMap(),
      rule.getTemplateKey());
  }

  private static Sonarlint.RuleSet adapt(RuleSet ruleSet) {
    return Sonarlint.RuleSet.newBuilder()
      .setLastModified(ruleSet.getLastModified())
      .addAllRule(ruleSet.getRules().stream().map(AnalyzerConfigurationStorage::adapt).collect(Collectors.toList())).build();
  }

  private static Sonarlint.RuleSet.ActiveRule adapt(ServerActiveRule rule) {
    return Sonarlint.RuleSet.ActiveRule.newBuilder()
      .setRuleKey(rule.getRuleKey())
      .setSeverity(rule.getSeverity().name())
      .setTemplateKey(rule.getTemplateKey())
      .putAllParams(rule.getParams())
      .build();
  }
}
