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
package org.sonarsource.sonarlint.core.serverconnection;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.qualityprofile.QualityProfile;
import org.sonarsource.sonarlint.core.serverconnection.repository.AnalyzerConfigurationRepository;
import org.sonarsource.sonarlint.core.serverconnection.repository.NewCodeDefinitionRepository;
import org.sonarsource.sonarlint.core.serverconnection.repository.PluginsRepository;
import org.sonarsource.sonarlint.core.serverconnection.repository.ServerInfoRepository;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;

import static java.util.stream.Collectors.toSet;

public class LocalStorageSynchronizer {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Set<String> enabledLanguageKeys;
  private final ServerInfoRepository serverInfoRepository;
  private final String connectionId;
  private final ServerInfoSynchronizer serverInfoSynchronizer;
  private final PluginsSynchronizer pluginsSynchronizer;

  public LocalStorageSynchronizer(Set<SonarLanguage> enabledLanguages, Set<String> embeddedPluginKeys, ServerInfoSynchronizer serverInfoSynchronizer, ServerInfoRepository serverInfoRepository, PluginsRepository pluginsRepository, String connectionId) {
    this.enabledLanguageKeys = enabledLanguages.stream().map(SonarLanguage::getSonarLanguageKey).collect(toSet());
    this.serverInfoRepository = serverInfoRepository;
    this.connectionId = connectionId;
    this.serverInfoSynchronizer = serverInfoSynchronizer;
    this.pluginsSynchronizer = new PluginsSynchronizer(enabledLanguages, pluginsRepository, connectionId, embeddedPluginKeys);
  }

  public Summary synchronizeServerInfosAndPlugins(ServerApi serverApi, SonarLintCancelMonitor cancelMonitor) {
    serverInfoSynchronizer.synchronize(serverApi, cancelMonitor);
    var version = serverInfoRepository.read(connectionId).orElseThrow().version();
    var pluginSynchronizationSummary = pluginsSynchronizer.synchronize(serverApi, version, cancelMonitor);
    return new Summary(version, pluginSynchronizationSummary.anyPluginSynchronized());
  }

  private static AnalyzerSettingsUpdateSummary diffAnalyzerConfiguration(AnalyzerConfiguration original, AnalyzerConfiguration updated) {
    var originalSettings = original.getSettings().getAll();
    var updatedSettings = updated.getSettings().getAll();
    var diff = Maps.difference(originalSettings, updatedSettings);
    var updatedSettingsValueByKey = diff.entriesDiffering().entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().rightValue()));
    updatedSettingsValueByKey.putAll(diff.entriesOnlyOnRight());
    updatedSettingsValueByKey.putAll(diff.entriesOnlyOnLeft().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> "")));
    return new AnalyzerSettingsUpdateSummary(updatedSettingsValueByKey);
  }

  public AnalyzerSettingsUpdateSummary synchronizeAnalyzerConfig(ServerApi serverApi, String projectKey, AnalyzerConfigurationRepository analyzerConfigurationRepository, NewCodeDefinitionRepository newCodeDefinitionRepository, SonarLintCancelMonitor cancelMonitor) {
    var updatedAnalyzerConfiguration = downloadAnalyzerConfig(serverApi, projectKey, analyzerConfigurationRepository, cancelMonitor);
    AnalyzerSettingsUpdateSummary configUpdateSummary;
    try {
      var originalAnalyzerConfiguration = analyzerConfigurationRepository.read(connectionId, projectKey);
      configUpdateSummary = diffAnalyzerConfiguration(originalAnalyzerConfiguration, updatedAnalyzerConfiguration);
    } catch (StorageException e) {
      configUpdateSummary = new AnalyzerSettingsUpdateSummary(updatedAnalyzerConfiguration.getSettings().getAll());
    }

    analyzerConfigurationRepository.store(connectionId, projectKey, updatedAnalyzerConfiguration);
    var version = serverInfoRepository.read(connectionId).orElseThrow().version();
    serverApi.newCodeApi().getNewCodeDefinition(projectKey, null, version, cancelMonitor)
      .ifPresent(ncd -> newCodeDefinitionRepository.store(connectionId, projectKey, ncd));
    return configUpdateSummary;
  }

  private AnalyzerConfiguration downloadAnalyzerConfig(ServerApi serverApi, String projectKey, AnalyzerConfigurationRepository analyzerConfigurationRepository, SonarLintCancelMonitor cancelMonitor) {
    LOG.info("[SYNC] Synchronizing analyzer configuration for project '{}'", projectKey);
    LOG.info("[SYNC] Languages enabled for synchronization: {}", enabledLanguageKeys);
    Map<String, RuleSet> currentRuleSets;
    int currentSchemaVersion;
    try {
      var analyzerConfiguration = analyzerConfigurationRepository.read(connectionId, projectKey);
      currentRuleSets = analyzerConfiguration.getRuleSetByLanguageKey();
      currentSchemaVersion = analyzerConfiguration.getSchemaVersion();
    } catch (StorageException e) {
      currentRuleSets = Map.of();
      currentSchemaVersion = 0;
    }
    var shouldForceRuleSetUpdate = outdatedSchema(currentSchemaVersion);
    var currentRuleSetsFinal = currentRuleSets;
    var settings = new Settings(serverApi.settings().getProjectSettings(projectKey, cancelMonitor));
    var ruleSetsByLanguageKey = serverApi.qualityProfile().getQualityProfiles(projectKey, cancelMonitor).stream()
      .filter(qualityProfile -> enabledLanguageKeys.contains(qualityProfile.getLanguage()))
      .collect(Collectors.toMap(QualityProfile::getLanguage, profile -> toRuleSet(serverApi, currentRuleSetsFinal, profile, shouldForceRuleSetUpdate, cancelMonitor)));
    return new AnalyzerConfiguration(settings, ruleSetsByLanguageKey, AnalyzerConfiguration.CURRENT_SCHEMA_VERSION);
  }

  private static RuleSet toRuleSet(ServerApi serverApi, Map<String, RuleSet> currentRuleSets, QualityProfile profile, boolean forceUpdate,
    SonarLintCancelMonitor cancelMonitor) {
    var language = profile.getLanguage();
    if (forceUpdate ||
      newlySupportedLanguage(currentRuleSets, language) ||
      profileModifiedSinceLastSync(currentRuleSets, profile, language)) {
      var profileKey = profile.getKey();
      LOG.info("[SYNC] Fetching rule set for language '{}' from profile '{}'", language, profileKey);
      var profileActiveRules = serverApi.rules().getAllActiveRules(profileKey, cancelMonitor);
      return new RuleSet(profileActiveRules, profile.getRulesUpdatedAt());
    } else {
      LOG.info("[SYNC] Active rules for '{}' are up-to-date", language);
      return currentRuleSets.get(language);
    }
  }

  private static boolean profileModifiedSinceLastSync(Map<String, RuleSet> currentRuleSets, QualityProfile profile, String language) {
    return !currentRuleSets.get(language).getLastModified().equals(profile.getRulesUpdatedAt());
  }

  private static boolean newlySupportedLanguage(Map<String, RuleSet> currentRuleSets, String language) {
    return !currentRuleSets.containsKey(language);
  }

  private static boolean outdatedSchema(int currentSchemaVersion) {
    return currentSchemaVersion < AnalyzerConfiguration.CURRENT_SCHEMA_VERSION;
  }

  public record Summary(Version version, boolean anyPluginSynchronized) {
  }
}
