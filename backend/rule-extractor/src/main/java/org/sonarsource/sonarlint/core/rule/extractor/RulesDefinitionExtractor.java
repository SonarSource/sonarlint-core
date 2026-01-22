/*
 * SonarLint Core - Rule Extractor
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class RulesDefinitionExtractor {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public List<SonarLintRuleDefinition> extractRules(Map<String, Plugin> pluginInstancesByKeys, Set<SonarLanguage> enabledLanguages,
    boolean includeTemplateRules, boolean includeSecurityHotspots, RuleSettings settings) {
    Context context;
    try {
      var container = new RulesDefinitionExtractorContainer(pluginInstancesByKeys, settings);
      container.execute(null);
      context = container.getRulesDefinitionContext();
    } catch (Exception e) {
      throw new IllegalStateException("Unable to extract rules metadata", e);
    }

    var allRepositories = context.repositories();
    LOG.debug("Found {} rule repositories from plugins", allRepositories.size());

    List<SonarLintRuleDefinition> rules = new ArrayList<>();
    var skippedExternalRepos = 0;
    var skippedLanguageRepos = 0;

    for (var repoDef : allRepositories) {
      if (repoDef.isExternal()) {
        skippedExternalRepos++;
        LOG.debug("Skipping external rule repository '{}' (language: {})", repoDef.key(), repoDef.language());
        continue;
      }
      var repoLanguage = SonarLanguage.forKey(repoDef.language());
      if (repoLanguage.isEmpty()) {
        skippedLanguageRepos++;
        LOG.debug("Skipping rule repository '{}' with unknown language '{}'", repoDef.key(), repoDef.language());
        continue;
      }
      if (!enabledLanguages.contains(repoLanguage.get())) {
        skippedLanguageRepos++;
        LOG.debug("Skipping rule repository '{}' because language '{}' is not enabled", repoDef.key(), repoDef.language());
        continue;
      }

      var repoRulesCount = 0;
      var skippedHotspots = 0;
      var skippedTemplates = 0;
      for (RulesDefinition.Rule ruleDef : repoDef.rules()) {
        if (shouldIgnoreAsHotspot(includeSecurityHotspots, ruleDef)) {
          skippedHotspots++;
          continue;
        }
        if (shouldIgnoreAsTemplate(includeTemplateRules, ruleDef)) {
          skippedTemplates++;
          continue;
        }
        rules.add(new SonarLintRuleDefinition(ruleDef));
        repoRulesCount++;
      }
      LOG.debug("Repository '{}' (language: {}): {} rules extracted, {} hotspots skipped, {} templates skipped",
        repoDef.key(), repoDef.language(), repoRulesCount, skippedHotspots, skippedTemplates);
    }

    LOG.debug("Rule extraction summary: {} rules extracted, {} external repos skipped, {} repos with disabled/unknown language skipped",
      rules.size(), skippedExternalRepos, skippedLanguageRepos);

    return rules;

  }

  private static boolean shouldIgnoreAsTemplate(boolean includeTemplateRules, RulesDefinition.Rule ruleDef) {
    return ruleDef.template() && !includeTemplateRules;
  }

  private static boolean shouldIgnoreAsHotspot(boolean hotspotsEnabled, RulesDefinition.Rule ruleDef) {
    return ruleDef.type() == RuleType.SECURITY_HOTSPOT && !hotspotsEnabled;
  }

}
