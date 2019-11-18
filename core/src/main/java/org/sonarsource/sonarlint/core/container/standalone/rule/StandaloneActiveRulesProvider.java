/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.standalone.rule;

import java.util.HashMap;
import java.util.Map;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.rule.internal.NewActiveRule;
import org.sonar.api.batch.rule.internal.NewActiveRule.Builder;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition.Param;
import org.sonar.api.server.rule.RulesDefinition.Repository;
import org.sonar.api.server.rule.RulesDefinition.Rule;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.container.model.DefaultRuleDetails;

/**
 * Loads the rules that are activated on the Quality profiles
 * used by the current project and builds {@link org.sonar.api.batch.rule.ActiveRules}.
 */
public class StandaloneActiveRulesProvider {
  private StandaloneActiveRules singleton = null;
  private final StandaloneRuleDefinitionsLoader ruleDefsLoader;

  public StandaloneActiveRulesProvider(StandaloneRuleDefinitionsLoader ruleDefsLoader) {
    this.ruleDefsLoader = ruleDefsLoader;
  }

  public StandaloneActiveRules provide() {
    if (singleton == null) {
      singleton = createActiveRules();
    }
    return singleton;
  }

  private StandaloneActiveRules createActiveRules() {
    ActiveRulesBuilder activeBuilder = new ActiveRulesBuilder();
    ActiveRulesBuilder inactiveBuilder = new ActiveRulesBuilder();

    Map<String, RuleDetails> ruleDetailsMap = new HashMap<>();

    for (Repository repo : ruleDefsLoader.getContext().repositories()) {
      for (Rule rule : repo.rules()) {
        if (rule.type() == RuleType.SECURITY_HOTSPOT || rule.template()) {
          continue;
        }
        ActiveRulesBuilder builder = rule.activatedByDefault() ? activeBuilder : inactiveBuilder;
        RuleKey ruleKey = RuleKey.of(repo.key(), rule.key());
        Builder newAr = new NewActiveRule.Builder()
          .setRuleKey(ruleKey)
          .setLanguage(repo.language())
          .setName(rule.name())
          .setSeverity(rule.severity())
          .setInternalKey(rule.internalKey());
        for (Param param : rule.params()) {
          newAr.setParam(param.key(), param.defaultValue());
        }
        builder.addRule(newAr.build());

        DefaultRuleDetails ruleDetails = new DefaultRuleDetails(ruleKey.toString(), rule.name(), rule.htmlDescription(),
          rule.severity(), rule.type().name(), repo.language(), rule.tags(), "",
          rule.activatedByDefault());
        ruleDetailsMap.put(ruleKey.toString(), ruleDetails);
      }
    }
    return new StandaloneActiveRules(activeBuilder.build(), inactiveBuilder.build(), ruleDetailsMap);
  }

}
