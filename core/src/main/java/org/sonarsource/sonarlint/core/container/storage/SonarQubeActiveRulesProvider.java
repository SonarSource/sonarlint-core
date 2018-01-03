/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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

import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.picocontainer.injectors.ProviderAdapter;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.Rule;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.rule.internal.NewActiveRule;
import org.sonar.api.resources.Languages;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.exceptions.MessageException;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules.ActiveRule;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles.QProfile;

public class SonarQubeActiveRulesProvider extends ProviderAdapter {

  private static final Logger LOG = Loggers.get(SonarQubeActiveRulesProvider.class);

  private ActiveRules activeRules;

  public ActiveRules provide(Sonarlint.Rules storageRules, Sonarlint.QProfiles qProfiles, StorageReader storageReader, Rules rules,
    ConnectedAnalysisConfiguration analysisConfiguration, Languages languages) {
    if (activeRules == null) {

      Map<String, String> qProfilesByLanguage = loadQualityProfilesFromStorage(qProfiles, storageReader, analysisConfiguration);

      ActiveRulesBuilder builder = new ActiveRulesBuilder();
      for (Map.Entry<String, String> entry : qProfilesByLanguage.entrySet()) {
        String language = entry.getKey();
        if (languages.get(language) == null) {
          continue;
        }

        String qProfileKey = entry.getValue();
        QProfile qProfile = qProfiles.getQprofilesByKeyOrThrow(qProfileKey);

        if (qProfile.getActiveRuleCount() == 0) {
          LOG.debug("  * {}: {} (0 rules)", language, qProfileKey);
          continue;
        }

        Sonarlint.ActiveRules activeRulesFromStorage = storageReader.readActiveRules(qProfileKey);

        LOG.debug("  * {}: {} ({} rules)", language, qProfileKey, activeRulesFromStorage.getActiveRulesByKeyMap().size());

        for (ActiveRule activeRule : activeRulesFromStorage.getActiveRulesByKeyMap().values()) {
          createNewActiveRule(builder, activeRule, storageRules, language, rules);
        }
      }

      activeRules = builder.build();
    }
    return activeRules;
  }

  private static void createNewActiveRule(ActiveRulesBuilder builder, ActiveRule activeRule, Sonarlint.Rules storageRules, String language, Rules rules) {
    RuleKey ruleKey = RuleKey.of(activeRule.getRepo(), activeRule.getKey());
    Rule rule = rules.find(ruleKey);
    Sonarlint.Rules.Rule storageRule;
    try {
      storageRule = storageRules.getRulesByKeyOrThrow(ruleKey.toString());
    } catch (IllegalArgumentException e) {
      throw new MessageException("Unknown active rule in the quality profile of the project. Please update the SonarQube server binding.");
    }

    NewActiveRule newActiveRule = builder.create(ruleKey)
      .setLanguage(language)
      .setName(rule.name())
      .setInternalKey(rule.internalKey())
      .setSeverity(activeRule.getSeverity());

    if (!StringUtils.isEmpty(storageRule.getTemplateKey())) {
      RuleKey templateRuleKey = RuleKey.parse(storageRule.getTemplateKey());
      newActiveRule.setTemplateRuleKey(templateRuleKey.rule());
    }

    for (Map.Entry<String, String> param : activeRule.getParamsMap().entrySet()) {
      newActiveRule.setParam(param.getKey(), param.getValue());
    }
    newActiveRule.activate();
  }

  private static Map<String, String> loadQualityProfilesFromStorage(Sonarlint.QProfiles qProfiles, StorageReader storageReader,
    ConnectedAnalysisConfiguration analysisConfiguration) {
    Map<String, String> qProfilesByLanguage;
    if (analysisConfiguration.moduleKey() == null) {
      LOG.debug("Use default quality profiles:");
      qProfilesByLanguage = qProfiles.getDefaultQProfilesByLanguageMap();
    } else {
      LOG.debug("Quality profiles:");
      qProfilesByLanguage = storageReader.readModuleConfig(analysisConfiguration.moduleKey()).getQprofilePerLanguageMap();
    }
    return qProfilesByLanguage;
  }

}
