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
package org.sonarsource.sonarlint.core.storage;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.qualityprofile.QualityProfile;

public class LocalStorageSynchronizer {
  private static final Logger LOG = Loggers.get(LocalStorageSynchronizer.class);

  private final Set<String> enabledLanguageKeys;
  private final PluginsSynchronizer pluginsSynchronizer;
  private final ProjectStorage projectStorage;

  public LocalStorageSynchronizer(Set<Language> enabledLanguages, Set<String> embeddedPluginKeys, PluginsStorage pluginsStorage, ProjectStorage projectStorage) {
    this.enabledLanguageKeys = enabledLanguages.stream().map(Language::getLanguageKey).collect(Collectors.toSet());
    this.projectStorage = projectStorage;
    this.pluginsSynchronizer = new PluginsSynchronizer(enabledLanguages, pluginsStorage, embeddedPluginKeys);
  }

  public SynchronizationResult synchronize(ServerApi serverApi, Set<String> projectKeys, ProgressMonitor progressMonitor) {
    var serverStatus = serverApi.system().getStatusSync();
    if (!serverStatus.isUp()) {
      LOG.info("[SYNC] Cannot synchronize with server as it is not UP ({})", serverStatus.getStatus());
      return new SynchronizationResult(false);
    }
    var anyPluginUpdated = pluginsSynchronizer.synchronize(serverApi, progressMonitor);
    projectKeys.stream()
      .collect(Collectors.toMap(Function.identity(), projectKey -> synchronize(serverApi, projectKey, progressMonitor)))
      .forEach(projectStorage::store);
    return new SynchronizationResult(anyPluginUpdated);
  }

  private AnalyzerConfiguration synchronize(ServerApi serverApi, String projectKey, ProgressMonitor progressMonitor) {
    LOG.info("[SYNC] Synchronizing '{}'", projectKey);
    var currentRuleSets = projectStorage.getAnalyzerConfiguration(projectKey).getRuleSetByLanguageKey();
    var settings = new Settings(serverApi.settings().getProjectSettings(projectKey));
    var ruleSetsByLanguageKey = serverApi.qualityProfile().getQualityProfiles(projectKey).stream()
      .filter(qualityProfile -> enabledLanguageKeys.contains(qualityProfile.getLanguage()))
      .collect(Collectors.toMap(QualityProfile::getLanguage, profile -> toRuleSet(serverApi, currentRuleSets, profile, progressMonitor)));
    return new AnalyzerConfiguration(settings, ruleSetsByLanguageKey);
  }

  private static RuleSet toRuleSet(ServerApi serverApi, Map<String, RuleSet> currentRuleSets, QualityProfile profile, ProgressMonitor progressMonitor) {
    var language = profile.getLanguage();
    if (!currentRuleSets.containsKey(language) || !currentRuleSets.get(language).getLastModified().equals(profile.getRulesUpdatedAt())) {
      LOG.info("[SYNC] Fetching rule set for '{}'", language);
      var profileActiveRules = serverApi.rules().getAllActiveRules(profile.getKey(), progressMonitor);
      return new RuleSet(profile.getKey(), profileActiveRules, profile.getRulesUpdatedAt());
    } else {
      LOG.info("[SYNC] Active rules for '{}' are up-to-date", language);
      return currentRuleSets.get(language);
    }
  }
}
