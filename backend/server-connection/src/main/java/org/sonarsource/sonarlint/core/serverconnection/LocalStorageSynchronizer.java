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
package org.sonarsource.sonarlint.core.serverconnection;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.branches.ServerBranch;
import org.sonarsource.sonarlint.core.serverapi.qualityprofile.QualityProfile;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;

import static java.util.stream.Collectors.toSet;

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

  public SynchronizationResult synchronize(ServerApi serverApi, Set<String> projectKeys, ProgressMonitor progressMonitor) {
    serverInfoSynchronizer.synchronize(serverApi);
    var version = storage.serverInfo().read().orElseThrow().getVersion();

    var anyPluginUpdated = pluginsSynchronizer.synchronize(serverApi, progressMonitor);
    projectKeys.stream()
      .collect(Collectors.toMap(Function.identity(), projectKey -> synchronizeAnalyzerConfig(serverApi, projectKey, progressMonitor)))
      .forEach((projectKey, analyzerConfig) -> storage.project(projectKey).analyzerConfiguration().store(analyzerConfig));
    var branchByProjectKey = projectKeys.stream()
      .collect(Collectors.toMap(Function.identity(), projectKey -> synchronizeProjectBranches(serverApi, projectKey)));
    branchByProjectKey
      .forEach((projectKey, branches) -> storage.project(projectKey).branches().store(branches));
    projectKeys.forEach(projectKey -> {
      progressMonitor.checkCancel();
      serverApi.newCodeApi().getNewCodeDefinition(projectKey, null, version)
        .ifPresent(ncd -> storage.project(projectKey).newCodeDefinition().store(ncd));
    });

    return new SynchronizationResult(anyPluginUpdated);
  }

  public boolean synchronize(ServerApi serverApi) {
    serverInfoSynchronizer.synchronize(serverApi);
    return pluginsSynchronizer.synchronize(serverApi, new ProgressMonitor(null));
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

  public AnalyzerSettingsUpdateSummary synchronize(ServerApi serverApi, String projectKey) {
    var updatedAnalyzerConfiguration = synchronizeAnalyzerConfig(serverApi, projectKey, new ProgressMonitor(null));
    AnalyzerSettingsUpdateSummary configUpdateSummary;
    try {
      var originalAnalyzerConfiguration = storage.project(projectKey).analyzerConfiguration().read();
      configUpdateSummary = diffAnalyzerConfiguration(originalAnalyzerConfiguration, updatedAnalyzerConfiguration);
    } catch (StorageException e) {
      configUpdateSummary = null;
    }

    storage.project(projectKey).analyzerConfiguration().store(updatedAnalyzerConfiguration);
    var version = storage.serverInfo().read().orElseThrow().getVersion();
    serverApi.newCodeApi().getNewCodeDefinition(projectKey, null, version)
      .ifPresent(ncd -> storage.project(projectKey).newCodeDefinition().store(ncd));
    return configUpdateSummary;
  }

  private AnalyzerConfiguration synchronizeAnalyzerConfig(ServerApi serverApi, String projectKey, ProgressMonitor progressMonitor) {
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
    var settings = new Settings(serverApi.settings().getProjectSettings(projectKey));
    var ruleSetsByLanguageKey = serverApi.qualityProfile().getQualityProfiles(projectKey).stream()
      .filter(qualityProfile -> enabledLanguageKeys.contains(qualityProfile.getLanguage()))
      .collect(Collectors.toMap(QualityProfile::getLanguage, profile -> toRuleSet(serverApi, currentRuleSetsFinal, profile, shouldForceRuleSetUpdate, progressMonitor)));
    return new AnalyzerConfiguration(settings, ruleSetsByLanguageKey, AnalyzerConfiguration.CURRENT_SCHEMA_VERSION);
  }

  private static RuleSet toRuleSet(ServerApi serverApi, Map<String, RuleSet> currentRuleSets, QualityProfile profile, boolean forceUpdate,
    ProgressMonitor progressMonitor) {
    var language = profile.getLanguage();
    if (forceUpdate ||
      newlySupportedLanguage(currentRuleSets, language) ||
      profileModifiedSinceLastSync(currentRuleSets, profile, language)) {
      var profileKey = profile.getKey();
      LOG.info("[SYNC] Fetching rule set for language '{}' from profile '{}'", language, profileKey);
      var profileActiveRules = serverApi.rules().getAllActiveRules(profileKey, progressMonitor);
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

  private static ProjectBranches synchronizeProjectBranches(ServerApi serverApi, String projectKey) {
    LOG.info("[SYNC] Synchronizing project branches for project '{}'", projectKey);
    var allBranches = serverApi.branches().getAllBranches(projectKey);
    var mainBranch = allBranches.stream().filter(ServerBranch::isMain).findFirst().map(ServerBranch::getName)
      .orElseThrow(() -> new IllegalStateException("No main branch for project '" + projectKey + "'"));
    return new ProjectBranches(allBranches.stream().map(ServerBranch::getName).collect(toSet()), mainBranch);
  }
}
