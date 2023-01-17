/*
 * SonarLint Core - Rule Extractor
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
package org.sonarsource.sonarlint.core.rule.extractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sonar.api.Plugin;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Context;
import org.sonarsource.sonarlint.core.commons.Language;

public class RulesDefinitionExtractor {

  public List<SonarLintRuleDefinition> extractRules(Map<String, Plugin> pluginInstancesByKeys, Set<Language> enabledLanguages,
    boolean includeTemplateRules, boolean includeSecurityHotspots) {
    Context context;
    try {
      var container = new RulesDefinitionExtractorContainer(pluginInstancesByKeys);
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
        if (shouldIgnoreAsHotspot(includeSecurityHotspots, ruleDef) || shouldIgnoreAsTemplate(includeTemplateRules, ruleDef)) {
          continue;
        }
        rules.add(new SonarLintRuleDefinition(ruleDef));
      }
    }

    return rules;

  }

  private static boolean shouldIgnoreAsTemplate(boolean includeTemplateRules, RulesDefinition.Rule ruleDef) {
    return ruleDef.template() && !includeTemplateRules;
  }

  private static boolean shouldIgnoreAsHotspot(boolean hotspotsEnabled, RulesDefinition.Rule ruleDef) {
    return ruleDef.type() == RuleType.SECURITY_HOTSPOT && !hotspotsEnabled;
  }

}
