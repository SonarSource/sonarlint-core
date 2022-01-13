/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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

import java.util.Optional;
import org.picocontainer.injectors.ProviderAdapter;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonarsource.sonarlint.core.client.api.common.AbstractGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.container.analysis.SonarLintRules;

public class StandaloneSonarLintRulesProvider extends ProviderAdapter {
  private SonarLintRules singleton = null;

  public SonarLintRules provide(StandaloneRuleDefinitionsLoader pluginRulesLoader, AbstractGlobalConfiguration config) {
    if (singleton == null) {
      singleton = createRules(pluginRulesLoader, config);
    }
    return singleton;
  }

  private static SonarLintRules createRules(StandaloneRuleDefinitionsLoader pluginRulesLoader, AbstractGlobalConfiguration config) {
    var rules = new SonarLintRules();

    for (RulesDefinition.Repository repoDef : pluginRulesLoader.getContext().repositories()) {
      Optional<Language> repoLanguage = Language.forKey(repoDef.language());
      if (!repoLanguage.isPresent() || !config.getEnabledLanguages().contains(repoLanguage.get())) {
        continue;
      }
      for (RulesDefinition.Rule ruleDef : repoDef.rules()) {
        if (ruleDef.type() == RuleType.SECURITY_HOTSPOT || ruleDef.template()) {
          continue;
        }
        rules.add(new StandaloneRule(ruleDef));
      }
    }

    return rules;
  }
}
