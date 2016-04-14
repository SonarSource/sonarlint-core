/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
import org.picocontainer.injectors.ProviderAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.Rule;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.rule.internal.NewActiveRule;
import org.sonar.api.resources.Languages;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules.ActiveRule;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles.QProfile;

public class SonarQubeActiveRulesProvider extends ProviderAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(SonarQubeActiveRulesProvider.class);

  private ActiveRules activeRules;

  public ActiveRules provide(Sonarlint.Rules storageRules, Sonarlint.QProfiles qProfiles, StorageManager storageManager, Rules rules,
    ConnectedAnalysisConfiguration analysisConfiguration, Languages languages) {
    if (activeRules == null) {
      ActiveRulesBuilder builder = new ActiveRulesBuilder();

      Map<String, String> qProfilesByLanguage;

      if (analysisConfiguration.moduleKey() == null) {
        LOG.debug("Use default quality profiles:");
        qProfilesByLanguage = qProfiles.getDefaultQProfilesByLanguage();
      } else {
        LOG.debug("Quality profiles:");
        qProfilesByLanguage = storageManager.readModuleConfigFromStorage(analysisConfiguration.moduleKey()).getQprofilePerLanguage();
      }

      for (Map.Entry<String, String> entry : qProfilesByLanguage.entrySet()) {
        String language = entry.getKey();
        if (languages.get(language) == null) {
          continue;
        }

        String qProfileKey = entry.getValue();
        QProfile qProfile = qProfiles.getQprofilesByKey().get(qProfileKey);
        LOG.debug("  * " + language + ": " + qProfileKey + " (" + qProfile.getActiveRuleCount() + " rules)");

        if (qProfile.getActiveRuleCount() == 0) {
          continue;
        }

        org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules activeRulesFromStorage = ProtobufUtil.readFile(storageManager.getActiveRulesPath(qProfileKey),
          Sonarlint.ActiveRules.parser());
        for (Map.Entry<String, ActiveRule> arEntry : activeRulesFromStorage.getActiveRulesByKey().entrySet()) {
          ActiveRule activeRule = arEntry.getValue();
          RuleKey ruleKey = RuleKey.of(activeRule.getRepo(), activeRule.getKey());
          Rule rule = rules.find(ruleKey);
          Sonarlint.Rules.Rule storageRule = storageRules.getRulesByKey().get(ruleKey.toString());
          NewActiveRule newActiveRule = builder.create(ruleKey)
            .setLanguage(language)
            .setName(rule.name())
            .setInternalKey(rule.internalKey())
            .setTemplateRuleKey(storageRule.getTemplateKey())
            .setSeverity(activeRule.getSeverity());
          for (Map.Entry<String, String> param : activeRule.getParams().entrySet()) {
            newActiveRule.setParam(param.getKey(), param.getValue());
          }
          newActiveRule.activate();
        }
      }

      activeRules = builder.build();
    }
    return activeRules;
  }

}
