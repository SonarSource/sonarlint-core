/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
import java.util.Map;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rule.extractor.RuleSettings;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractor;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;

import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.SECURITY_HOTSPOTS;

public class RulesExtractionHelper {

  private final SonarLintLogger logger = SonarLintLogger.get();

  private final PluginsService pluginsService;
  private final LanguageSupportRepository languageSupportRepository;
  private final RulesDefinitionExtractor ruleExtractor = new RulesDefinitionExtractor();
  private final boolean enableSecurityHotspots;

  public RulesExtractionHelper(PluginsService pluginsService, LanguageSupportRepository languageSupportRepository, InitializeParams params) {
    this.pluginsService = pluginsService;
    this.languageSupportRepository = languageSupportRepository;
    this.enableSecurityHotspots = params.getBackendCapabilities().contains(SECURITY_HOTSPOTS);
  }

  public List<SonarLintRuleDefinition> extractEmbeddedRules() {
    logger.debug("Extracting standalone rules metadata");
    return ruleExtractor.extractRules(pluginsService.getEmbeddedPlugins().getAllPluginInstancesByKeys(),
      languageSupportRepository.getEnabledLanguagesInStandaloneMode(), false, false, new RuleSettings(Map.of()));
  }

  public List<SonarLintRuleDefinition> extractRulesForConnection(String connectionId, Map<String, String> globalSettings) {
    logger.debug("Extracting rules metadata for connection '{}'", connectionId);
    var settings = new RuleSettings(globalSettings);
    return ruleExtractor.extractRules(pluginsService.getPlugins(connectionId).getAllPluginInstancesByKeys(),
      languageSupportRepository.getEnabledLanguagesInConnectedMode(), true, enableSecurityHotspots, settings);
  }

}
