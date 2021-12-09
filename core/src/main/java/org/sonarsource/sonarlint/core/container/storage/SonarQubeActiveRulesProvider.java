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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.picocontainer.injectors.ProviderAdapter;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.resources.Languages;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.container.analysis.SonarLintRules;
import org.sonarsource.sonarlint.core.container.global.DefaultActiveRules;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneActiveRuleAdapter;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRule;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerRules;
import org.sonarsource.sonarlint.core.storage.ProjectStorage;
import org.sonarsource.sonarlint.core.storage.RuleSet;

public class SonarQubeActiveRulesProvider extends ProviderAdapter {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private ActiveRules activeRules;

  public ActiveRules provide(SonarLintRules rules, RulesStore rulesStore, ConnectedAnalysisConfiguration analysisConfiguration,
    Languages languages, ConnectedGlobalConfiguration globalConfiguration, ProjectStorage projectStorage) {

    if (activeRules == null) {
      Collection<org.sonar.api.batch.rule.ActiveRule> activeRulesList = new ArrayList<>();
      // could be empty before the first sync
      var projectKey = analysisConfiguration.projectKey();
      if (projectKey == null) {
        // this should be forbidden by client side
        LOG.debug("No project key provided, no rules will be used for analysis");
        return new DefaultActiveRules(Collections.emptyList());
      }

      var rulesByKey = rulesStore.getAll().stream().collect(Collectors.toMap(ServerRules.Rule::getRuleKey, Function.identity()));
      for (Map.Entry<String, RuleSet> entry : projectStorage.getAnalyzerConfiguration(projectKey).getRuleSetByLanguageKey().entrySet()) {
        String languageKey = entry.getKey();
        var ruleSet = entry.getValue();
        if (languages.get(languageKey) == null) {
          continue;
        }

        LOG.debug("  * {}: '{}' ({} active rules)", languageKey, ruleSet.getProfileKey(), ruleSet.getRules().size());
        for (ServerRules.ActiveRule activeRule : ruleSet.getRules()) {
          activeRulesList.add(createNewActiveRule(activeRule, rulesByKey.get(activeRule.getRuleKey())));
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

  private static org.sonar.api.batch.rule.ActiveRule createNewActiveRule(ServerRules.ActiveRule activeRule, ServerRules.Rule rule) {
    return new StorageActiveRuleAdapter(activeRule, rule);
  }

}
