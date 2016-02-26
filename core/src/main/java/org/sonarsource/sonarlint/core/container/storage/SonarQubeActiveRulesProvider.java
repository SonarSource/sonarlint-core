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
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.Rule;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.rule.internal.NewActiveRule;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules.ActiveRule;

public class SonarQubeActiveRulesProvider extends ProviderAdapter {

  private ActiveRules activeRules;

  public ActiveRules provide(Sonarlint.Rules storageRules, StorageManager storageManager, Rules rules) {
    if (activeRules == null) {
      ActiveRulesBuilder builder = new ActiveRulesBuilder();

      for (Map.Entry<String, String> entry : storageRules.getDefaultQProfilesByLanguage().entrySet()) {
        org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules activeRulesFromStorage = ProtobufUtil.readFile(storageManager.getActiveRulesPath(entry.getValue()),
          Sonarlint.ActiveRules.parser());
        for (Map.Entry<String, ActiveRule> arEntry : activeRulesFromStorage.getActiveRulesByKey().entrySet()) {
          ActiveRule activeRule = arEntry.getValue();
          RuleKey ruleKey = RuleKey.of(activeRule.getRepo(), activeRule.getKey());
          Rule rule = rules.find(ruleKey);
          Sonarlint.Rules.Rule storageRule = storageRules.getRulesByKey().get(ruleKey.toString());
          NewActiveRule newActiveRule = builder.create(ruleKey)
            .setLanguage(entry.getKey())
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
