/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.rules;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.plugin.PluginsServiceImpl;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractor;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;

public class RulesServiceImpl {
  private final PluginsServiceImpl pluginsService;
  private final RulesRepository rulesRepository;
  private final RulesDefinitionExtractor ruleExtractor = new RulesDefinitionExtractor();
  private Set<Language> enabledLanguages;
  private Set<Language> enabledLanguagesInConnectedMode;
  private boolean enableSecurityHotspots;

  public RulesServiceImpl(PluginsServiceImpl pluginsService, RulesRepository rulesRepository) {
    this.pluginsService = pluginsService;
    this.rulesRepository = rulesRepository;
  }

  public void initialize(Set<Language> enabledLanguages, Set<Language> enabledLanguagesInConnectedMode, boolean enableSecurityHotspots) {
    this.enabledLanguages = enabledLanguages;
    this.enabledLanguagesInConnectedMode = enabledLanguagesInConnectedMode;
    this.enableSecurityHotspots = enableSecurityHotspots;
  }

  public Collection<SonarLintRuleDefinition> getEmbeddedRules() {
    return ensureEmbeddedRulesExtracted();
  }

  public Optional<SonarLintRuleDefinition> getRule(String connectionId, String ruleKey) {
    ensureRulesExtracted(connectionId);
    return rulesRepository.getRule(connectionId, ruleKey);
  }

  public Optional<SonarLintRuleDefinition> getEmbeddedRule(String ruleKey) {
    ensureEmbeddedRulesExtracted();
    return rulesRepository.getEmbeddedRule(ruleKey);
  }

  private Collection<SonarLintRuleDefinition> ensureEmbeddedRulesExtracted() {
    var embeddedRules = rulesRepository.getEmbeddedRules();
    if (embeddedRules == null) {
      embeddedRules = ruleExtractor.extractRules(pluginsService.getEmbeddedPlugins().getPluginInstancesByKeys(), enabledLanguages, false, false);
      rulesRepository.setEmbeddedRules(embeddedRules);
    }
    return embeddedRules;
  }

  private void ensureRulesExtracted(String connectionId) {
    var rules = rulesRepository.getRules(connectionId);
    if (rules == null) {
      rules = ruleExtractor.extractRules(pluginsService.getPlugins(connectionId).getPluginInstancesByKeys(), enabledLanguagesInConnectedMode, true, enableSecurityHotspots);
      rulesRepository.setRules(connectionId, rules);
    }
  }
}
