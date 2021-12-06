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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.picocontainer.injectors.ProviderAdapter;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.resources.Languages;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.container.analysis.SonarLintRules;
import org.sonarsource.sonarlint.core.container.global.DefaultActiveRules;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneActiveRuleAdapter;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRule;
import org.sonarsource.sonarlint.core.serverapi.qualityprofile.QualityProfile;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerRules;

public class SonarQubeActiveRulesProvider extends ProviderAdapter {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private ActiveRules activeRules;

  public ActiveRules provide(RulesStore rulesStore, List<QualityProfile> qProfiles, StorageReader storageReader, SonarLintRules rules,
    ConnectedAnalysisConfiguration analysisConfiguration, Languages languages, ConnectedGlobalConfiguration globalConfiguration, ActiveRulesStore activeRulesStore) {
    if (activeRules == null) {

      Map<String, String> qProfilesByLanguage = loadQualityProfilesFromStorage(qProfiles, storageReader, analysisConfiguration);

      Collection<org.sonar.api.batch.rule.ActiveRule> activeRulesList = new ArrayList<>();
      for (Map.Entry<String, String> entry : qProfilesByLanguage.entrySet()) {
        String language = entry.getKey();
        if (languages.get(language) == null) {
          continue;
        }

        String qProfileKey = entry.getValue();
        QualityProfile qProfile = qProfiles.stream().filter(qp -> qp.getKey().equals(qProfileKey)).findFirst().orElseThrow();

        if (qProfile.getActiveRuleCount() == 0) {
          LOG.debug("  * {}: '{}' (0 rules)", language, qProfile.getName());
          continue;
        }

        List<ServerRules.ActiveRule> activeRulesFromStorage = activeRulesStore.getActiveRules(qProfileKey);

        LOG.debug("  * {}: '{}' ({} rules)", language, qProfile.getName(), activeRulesFromStorage.size());

        for (ServerRules.ActiveRule activeRule : activeRulesFromStorage) {
          activeRulesList.add(createNewActiveRule(activeRule, rulesStore));
        }
      }

      List<StandaloneActiveRuleAdapter> extraActiveRules = rules.findAll().stream().filter(StandaloneRule.class::isInstance).map(StandaloneRule.class::cast)
        .filter(rule -> isRuleFromExtraPlugin(rule.getLanguage(), globalConfiguration))
        .map(rule -> new StandaloneActiveRuleAdapter(rule, null))
        .collect(Collectors.toList());

      activeRulesList.addAll(extraActiveRules);

      activeRules = new DefaultActiveRules(activeRulesList);
    }
    return activeRules;
  }

  private static boolean isRuleFromExtraPlugin(Language ruleLanguage, ConnectedGlobalConfiguration config) {
    return config.getExtraPluginsUrlsByKey().keySet()
      .stream().anyMatch(extraPluginKey -> ruleLanguage.getLanguageKey().equals(extraPluginKey));
  }

  private static org.sonar.api.batch.rule.ActiveRule createNewActiveRule(ServerRules.ActiveRule activeRule, RulesStore rulesStore) {
    var storageRule = rulesStore.getRuleWithKey(activeRule.getRuleKey())
      .orElseThrow(() -> new StorageException("Unknown active rule in the quality profile of the project. Please update the SonarQube server binding."));

    return new StorageActiveRuleAdapter(activeRule, storageRule);
  }

  private static Map<String, String> loadQualityProfilesFromStorage(List<QualityProfile> qProfiles, StorageReader storageReader,
    ConnectedAnalysisConfiguration analysisConfiguration) {
    Map<String, String> qProfilesByLanguage;
    if (analysisConfiguration.projectKey() == null) {
      LOG.debug("Use default quality profiles:");
      qProfilesByLanguage = qProfiles.stream().filter(QualityProfile::isDefault)
        .collect(Collectors.toMap(QualityProfile::getLanguage, QualityProfile::getKey, (qp1, qp2) -> qp1));
    } else {
      LOG.debug("Quality profiles:");
      qProfilesByLanguage = storageReader.readProjectConfig(analysisConfiguration.projectKey()).getQprofilePerLanguageMap();
    }
    return qProfilesByLanguage;
  }

}
