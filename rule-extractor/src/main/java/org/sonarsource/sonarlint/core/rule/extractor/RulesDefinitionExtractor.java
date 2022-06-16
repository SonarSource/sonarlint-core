/*
 * SonarLint Core - Rule Extractor
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
package org.sonarsource.sonarlint.core.rule.extractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Context;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository;

public class RulesDefinitionExtractor {

  public List<SonarLintRuleDefinition> extractRules(PluginInstancesRepository pluginInstancesRepository, Set<Language> enabledLanguages, boolean includeTemplateRules) {
    Context context;
    try {
      var container = new RulesDefinitionExtractorContainer(pluginInstancesRepository);
      container.execute();
      context = container.getRulesDefinitionContext();
    } catch (Exception e) {
      throw new IllegalStateException("Unable to extract rules metadata", e);
    }

    List<SonarLintRuleDefinition> rules = new ArrayList<>();

    for (RulesDefinition.Repository repoDef : context.repositories()) {
      if (repoDef.isExternal()) {
        continue;
      }
      var repoLanguage = Language.forKey(repoDef.language());
      if (repoLanguage.isEmpty() || !enabledLanguages.contains(repoLanguage.get())) {
        continue;
      }
      for (RulesDefinition.Rule ruleDef : repoDef.rules()) {
        if (ruleDef.type() == RuleType.SECURITY_HOTSPOT || (ruleDef.template() && !includeTemplateRules)) {
          continue;
        }
        rules.add(new SonarLintRuleDefinition(ruleDef));
      }
    }

    return rules;

  }

}
