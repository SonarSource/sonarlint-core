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
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractor;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;

@Named
@Singleton
public class RulesExtractionHelper {

  private final SonarLintLogger logger = SonarLintLogger.get();

  private final PluginsService pluginsService;
  private final LanguageSupportRepository languageSupportRepository;
  private final RulesDefinitionExtractor ruleExtractor = new RulesDefinitionExtractor();
  private final boolean enableSecurityHotspots;

  public RulesExtractionHelper(PluginsService pluginsService, LanguageSupportRepository languageSupportRepository, InitializeParams params) {
    this.pluginsService = pluginsService;
    this.languageSupportRepository = languageSupportRepository;
    this.enableSecurityHotspots = params.getFeatureFlags().isEnableSecurityHotspots();
  }

  public List<SonarLintRuleDefinition> extractEmbeddedRules() {
    logger.debug("Extracting standalone rules metadata");
    return ruleExtractor.extractRules(pluginsService.getEmbeddedPlugins().getPluginInstancesByKeys(), languageSupportRepository.getEnabledLanguagesInStandaloneMode(), false,
      false);
  }

  public List<SonarLintRuleDefinition> extractRulesForConnection(String connectionId) {
    logger.debug("Extracting rules metadata for connection '{}'", connectionId);
    return ruleExtractor.extractRules(pluginsService.getPlugins(connectionId).getPluginInstancesByKeys(), languageSupportRepository.getEnabledLanguagesInConnectedMode(), true,
      enableSecurityHotspots);
  }

}
