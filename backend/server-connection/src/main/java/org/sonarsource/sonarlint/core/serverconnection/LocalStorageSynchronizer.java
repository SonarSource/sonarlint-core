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

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.qualityprofile.QualityProfile;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;

import static java.util.stream.Collectors.toSet;
import static org.sonarsource.sonarlint.core.serverconnection.PluginsSynchronizer.CUSTOM_SECRETS_MIN_SQ_VERSION;

public class LocalStorageSynchronizer {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Set<String> enabledLanguageKeys;
  private final ConnectionStorage storage;
  private final ServerInfoSynchronizer serverInfoSynchronizer;
  private final PluginsSynchronizer pluginsSynchronizer;

  public LocalStorageSynchronizer(Set<SonarLanguage> enabledLanguages, Set<String> embeddedPluginKeys, ServerInfoSynchronizer serverInfoSynchronizer, ConnectionStorage storage) {
    this.enabledLanguageKeys = enabledLanguages.stream().map(SonarLanguage::getSonarLanguageKey).collect(toSet());
    this.storage = storage;
    this.pluginsSynchronizer = new PluginsSynchronizer(enabledLanguages, storage, embeddedPluginKeys);
    this.serverInfoSynchronizer = serverInfoSynchronizer;
  }

  public boolean synchronizeServerInfosAndPlugins(ServerApi serverApi, SonarLintCancelMonitor cancelMonitor) {
    serverInfoSynchronizer.synchronize(serverApi, cancelMonitor);
    var version = storage.serverInfo().read().orElseThrow().getVersion();
    // INFO: In order to download `sonar-text` alongside `sonar-text-enterprise` on SQ 10.4+ we have to change the
    //       plug-in synchronizer to work correctly the moment the connection is established and the plug-ins are
    //       downloaded for the first time and also everytime the plug-ins are refreshed (e.g. after IDE restart).
    var supportsCustomSecrets = !serverApi.isSonarCloud()
      && version.compareToIgnoreQualifier(CUSTOM_SECRETS_MIN_SQ_VERSION) >= 0;
    return pluginsSynchronizer.synchronize(serverApi, supportsCustomSecrets, cancelMonitor);
  }

  private static AnalyzerSettingsUpdateSummary diffAnalyzerConfiguration(AnalyzerConfiguration original, AnalyzerConfiguration updated) {
    Map<String, String> originalSettings = original.getSettings().getAll();
    Map<String, String> updatedSettings = updated.getSettings().getAll();
    MapDifference<String, String> diff = Maps.difference(originalSettings, updatedSettings);
    var updatedSettingsValueByKey = diff.entriesDiffering().entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().rightValue()));
    updatedSettingsValueByKey.putAll(diff.entriesOnlyOnRight());
    updatedSettingsValueByKey.putAll(diff.entriesOnlyOnLeft().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> "")));
    return new AnalyzerSettingsUpdateSummary(updatedSettingsValueByKey);
  }

  public AnalyzerSettingsUpdateSummary synchronizeAnalyzerConfig(ServerApi serverApi, String projectKey, SonarLintCancelMonitor cancelMonitor) {
    var updatedAnalyzerConfiguration = downloadAnalyzerConfig(serverApi, projectKey, cancelMonitor);
    AnalyzerSettingsUpdateSummary configUpdateSummary;
    try {
      var originalAnalyzerConfiguration = storage.project(projectKey).analyzerConfiguration().read();
      configUpdateSummary = diffAnalyzerConfiguration(originalAnalyzerConfiguration, updatedAnalyzerConfiguration);
    } catch (StorageException e) {
      configUpdateSummary = new AnalyzerSettingsUpdateSummary(updatedAnalyzerConfiguration.getSettings().getAll());
    }

    storage.project(projectKey).analyzerConfiguration().store(updatedAnalyzerConfiguration);
    var version = storage.serverInfo().read().orElseThrow().getVersion();
    serverApi.newCodeApi().getNewCodeDefinition(projectKey, null, version, cancelMonitor)
      .ifPresent(ncd -> storage.project(projectKey).newCodeDefinition().store(ncd));
    return configUpdateSummary;
  }

  private AnalyzerConfiguration downloadAnalyzerConfig(ServerApi serverApi, String projectKey, SonarLintCancelMonitor cancelMonitor) {
    LOG.info("[SYNC] Synchronizing analyzer configuration for project '{}'", projectKey);
    Map<String, RuleSet> currentRuleSets;
    int currentSchemaVersion;
    try {
      var analyzerConfiguration = storage.project(projectKey).analyzerConfiguration().read();
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
}
