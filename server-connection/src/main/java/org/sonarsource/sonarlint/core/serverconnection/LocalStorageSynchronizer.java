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

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.branches.ServerBranch;
import org.sonarsource.sonarlint.core.serverapi.qualityprofile.QualityProfile;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;

import static java.util.stream.Collectors.toSet;

public class LocalStorageSynchronizer {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Set<String> enabledLanguageKeys;
  private final ServerInfoSynchronizer serverInfoSynchronizer;
  private final PluginsSynchronizer pluginsSynchronizer;
  private final ProjectStorage projectStorage;

  public LocalStorageSynchronizer(Set<Language> enabledLanguages, Set<String> embeddedPluginKeys, ServerInfoSynchronizer serverInfoSynchronizer, PluginsStorage pluginsStorage,
    ProjectStorage projectStorage) {
    this.enabledLanguageKeys = enabledLanguages.stream().map(Language::getLanguageKey).collect(toSet());
    this.projectStorage = projectStorage;
    this.pluginsSynchronizer = new PluginsSynchronizer(enabledLanguages, pluginsStorage, embeddedPluginKeys);
    this.serverInfoSynchronizer = serverInfoSynchronizer;
  }

  public SynchronizationResult synchronize(ServerApi serverApi, Set<String> projectKeys, ProgressMonitor progressMonitor) {
    serverInfoSynchronizer.synchronize(serverApi);

    var anyPluginUpdated = pluginsSynchronizer.synchronize(serverApi, progressMonitor);
    projectKeys.stream()
      .collect(Collectors.toMap(Function.identity(), projectKey -> synchronizeAnalyzerConfig(serverApi, projectKey, progressMonitor)))
      .forEach(projectStorage::store);
    var branchByProjectKey = projectKeys.stream()
      .collect(Collectors.toMap(Function.identity(), projectKey -> synchronizeProjectBranches(serverApi, projectKey)));
    branchByProjectKey
      .forEach(projectStorage::store);
    return new SynchronizationResult(anyPluginUpdated);
  }

  private AnalyzerConfiguration synchronizeAnalyzerConfig(ServerApi serverApi, String projectKey, ProgressMonitor progressMonitor) {
    LOG.info("[SYNC] Synchronizing analyzer configuration for project '{}'", projectKey);
    Map<String, RuleSet> currentRuleSets;
    try {
      currentRuleSets = projectStorage.getAnalyzerConfiguration(projectKey).getRuleSetByLanguageKey();
    } catch (StorageException e) {
      currentRuleSets = Map.of();
    }
    var currentRuleSetsFinal = currentRuleSets;
    var settings = new Settings(serverApi.settings().getProjectSettings(projectKey));
    var ruleSetsByLanguageKey = serverApi.qualityProfile().getQualityProfiles(projectKey).stream()
      .filter(qualityProfile -> enabledLanguageKeys.contains(qualityProfile.getLanguage()))
      .collect(Collectors.toMap(QualityProfile::getLanguage, profile -> toRuleSet(serverApi, currentRuleSetsFinal, profile, progressMonitor)));
    return new AnalyzerConfiguration(settings, ruleSetsByLanguageKey);
  }

  private static RuleSet toRuleSet(ServerApi serverApi, Map<String, RuleSet> currentRuleSets, QualityProfile profile, ProgressMonitor progressMonitor) {
    var language = profile.getLanguage();
    if (!currentRuleSets.containsKey(language) || !currentRuleSets.get(language).getLastModified().equals(profile.getRulesUpdatedAt())) {
      var profileKey = profile.getKey();
      LOG.info("[SYNC] Fetching rule set for language '{}' from profile '{}'", language, profileKey);
      var profileActiveRules = serverApi.rules().getAllActiveRules(profileKey, progressMonitor);
      return new RuleSet(profileActiveRules, profile.getRulesUpdatedAt());
    } else {
      LOG.info("[SYNC] Active rules for '{}' are up-to-date", language);
      return currentRuleSets.get(language);
    }
  }

  private static ProjectBranches synchronizeProjectBranches(ServerApi serverApi, String projectKey) {
    LOG.info("[SYNC] Synchronizing project branches for project '{}'", projectKey);
    var allBranches = serverApi.branches().getAllBranches(projectKey);
    var mainBranch = allBranches.stream().filter(ServerBranch::isMain).findFirst().map(ServerBranch::getName)
      .orElseThrow(() -> new IllegalStateException("No main branch for project '" + projectKey + "'"));
    return new ProjectBranches(allBranches.stream().map(ServerBranch::getName).collect(toSet()), mainBranch);
  }
}
