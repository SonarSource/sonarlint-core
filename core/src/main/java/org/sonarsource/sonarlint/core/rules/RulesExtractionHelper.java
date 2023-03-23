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

import java.util.List;
import java.util.Set;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.plugin.PluginsServiceImpl;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractor;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;

public class RulesExtractionHelper {

  private final PluginsServiceImpl pluginsService;
  private final RulesDefinitionExtractor ruleExtractor = new RulesDefinitionExtractor();
  private Set<Language> enabledLanguages;
  private Set<Language> enabledLanguagesInConnectedMode;
  private boolean enableSecurityHotspots;

  public RulesExtractionHelper(PluginsServiceImpl pluginsService) {
    this.pluginsService = pluginsService;
  }

  public void initialize(Set<Language> enabledLanguages, Set<Language> enabledLanguagesInConnectedMode, boolean enableSecurityHotspots) {
    this.enabledLanguages = enabledLanguages;
    this.enabledLanguagesInConnectedMode = enabledLanguagesInConnectedMode;
    this.enableSecurityHotspots = enableSecurityHotspots;
  }

  public List<SonarLintRuleDefinition> extractEmbeddedRules() {
    return ruleExtractor.extractRules(pluginsService.getEmbeddedPlugins().getPluginInstancesByKeys(), enabledLanguages, false, false);
  }

  public List<SonarLintRuleDefinition> extractRulesForConnection(String connectionId) {
    return ruleExtractor.extractRules(pluginsService.getPlugins(connectionId).getPluginInstancesByKeys(), enabledLanguagesInConnectedMode, true, enableSecurityHotspots);
  }

}
