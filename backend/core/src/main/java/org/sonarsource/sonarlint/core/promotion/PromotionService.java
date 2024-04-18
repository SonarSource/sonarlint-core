/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.promotion;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.analysis.AnalysisFinishedEvent;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.promotion.PromoteExtraEnabledLanguagesInConnectedModeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.springframework.context.event.EventListener;

@Named
@Singleton
public class PromotionService {
  private final ConfigurationRepository configurationRepository;
  private final Set<Language> extraEnabledLanguagesInConnectedMode;
  private final SonarLintRpcClient client;

  public PromotionService(ConfigurationRepository configurationRepository, InitializeParams initializeParams, SonarLintRpcClient client) {
    this.configurationRepository = configurationRepository;
    this.extraEnabledLanguagesInConnectedMode = initializeParams.getExtraEnabledLanguagesInConnectedMode();
    this.client = client;
  }

  @EventListener
  public void onAnalysisFinished(AnalysisFinishedEvent event) {
    var configurationScopeId = event.getConfigurationScopeId();
    if (isStandalone(configurationScopeId)) {
      promoteExtraEnabledLanguagesInConnectedMode(configurationScopeId, event.getDetectedLanguages());
    }
  }

  private boolean isStandalone(String configurationScopeId) {
    return configurationRepository.getEffectiveBinding(configurationScopeId).isEmpty();
  }

  private void promoteExtraEnabledLanguagesInConnectedMode(String configurationScopeId, Set<SonarLanguage> detectedLanguages) {
    var languagesToPromote = EnumSet.copyOf(detectedLanguages.stream().map(sonarLanguage -> Language.valueOf(sonarLanguage.name())).collect(Collectors.toSet()));
    languagesToPromote.retainAll(extraEnabledLanguagesInConnectedMode);
    if (!languagesToPromote.isEmpty()) {
      client.promoteExtraEnabledLanguagesInConnectedMode(new PromoteExtraEnabledLanguagesInConnectedModeParams(configurationScopeId, languagesToPromote));
    }
  }
}
