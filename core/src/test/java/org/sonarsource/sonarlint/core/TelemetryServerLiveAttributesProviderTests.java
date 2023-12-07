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
package org.sonarsource.sonarlint.core;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.rules.RulesService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServerLiveAttributesProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelemetryServerLiveAttributesProviderTests {

  @Test
  void it_should_calculate_connectedMode_usesSC_notDisabledNotifications_telemetry_attrs() {
    var configurationScopeId = "scopeId";
    var connectionId = "connectionId";
    var projectKey = "projectKey";

    var configurationRepository = mock(ConfigurationRepository.class);
    when(configurationRepository.getConfigScopeIds()).thenReturn(Set.of(configurationScopeId));
    when(configurationRepository.getBindingConfiguration(configurationScopeId)).thenReturn(new BindingConfiguration(connectionId, projectKey, true));

    var connectionConfigurationRepository = mock(ConnectionConfigurationRepository.class);
    when(connectionConfigurationRepository.getConnectionById(connectionId)).thenReturn(new SonarCloudConnectionConfiguration(connectionId, "myTestOrg", false));
    var underTest = new TelemetryServerLiveAttributesProvider(configurationRepository, connectionConfigurationRepository, mock(RulesService.class), mock(RulesRepository.class));

    var telemetryLiveAttributes = underTest.getTelemetryServerLiveAttributes();
    assertThat(telemetryLiveAttributes.usesConnectedMode()).isTrue();
    assertThat(telemetryLiveAttributes.usesSonarCloud()).isTrue();
    assertThat(telemetryLiveAttributes.isDevNotificationsDisabled()).isFalse();
    assertThat(telemetryLiveAttributes.getNonDefaultEnabledRules()).isEmpty();
    assertThat(telemetryLiveAttributes.getDefaultDisabledRules()).isEmpty();
  }

  @Test
  void it_should_calculate_connectedMode_notUsesSC_disabledDevNotifications_telemetry_attrs() {
    var configurationScopeId_1 = "scopeId_1";
    var configurationScopeId_2 = "scopeId_2";
    var connectionId_1 = "connectionId_1";
    var connectionId_2 = "connectionId_2";
    var projectKey = "projectKey";

    var configurationRepository = mock(ConfigurationRepository.class);
    when(configurationRepository.getConfigScopeIds()).thenReturn(Set.of(configurationScopeId_1, configurationScopeId_2));
    when(configurationRepository.getBindingConfiguration(configurationScopeId_1)).thenReturn(new BindingConfiguration(connectionId_1, projectKey, true));
    when(configurationRepository.getBindingConfiguration(configurationScopeId_2)).thenReturn(new BindingConfiguration(connectionId_2, projectKey, true));

    var connectionConfigurationRepository = mock(ConnectionConfigurationRepository.class);
    when(connectionConfigurationRepository.getConnectionById(connectionId_1)).thenReturn(new SonarQubeConnectionConfiguration(connectionId_1, "www.squrl1.org", false));
    when(connectionConfigurationRepository.getConnectionById(connectionId_2)).thenReturn(new SonarQubeConnectionConfiguration(connectionId_2, "www.squrl2.org", true));
    var underTest = new TelemetryServerLiveAttributesProvider(configurationRepository, connectionConfigurationRepository, mock(RulesService.class), mock(RulesRepository.class));

    var telemetryLiveAttributes = underTest.getTelemetryServerLiveAttributes();
    assertThat(telemetryLiveAttributes.usesConnectedMode()).isTrue();
    assertThat(telemetryLiveAttributes.usesSonarCloud()).isFalse();
    assertThat(telemetryLiveAttributes.isDevNotificationsDisabled()).isTrue();
    assertThat(telemetryLiveAttributes.getNonDefaultEnabledRules()).isEmpty();
    assertThat(telemetryLiveAttributes.getDefaultDisabledRules()).isEmpty();
  }

  @Test
  void it_should_calculate_disabledRules_enabledRules_telemetry_attrs() {
    var rulesService = mock(RulesService.class);
    when(rulesService.getStandaloneRuleConfig()).thenReturn(
      Map.of("ruleKey_1", new StandaloneRuleConfigDto(true, Map.of()),
        "ruleKey_2", new StandaloneRuleConfigDto(true, Map.of()),
        "ruleKey_3", new StandaloneRuleConfigDto(false, Map.of()),
        "ruleKey_4", new StandaloneRuleConfigDto(false, Map.of())));

    var rulesRepository = mock(RulesRepository.class);
    var sonarLintRuleDefinition_1 = getSonarLintRuleDefinition(true);
    var sonarLintRuleDefinition_2 = getSonarLintRuleDefinition(false);
    var sonarLintRuleDefinition_3 = getSonarLintRuleDefinition(true);
    var sonarLintRuleDefinition_4 = getSonarLintRuleDefinition(false);
    when(rulesRepository.getEmbeddedRule("ruleKey_1")).thenReturn(sonarLintRuleDefinition_1);
    when(rulesRepository.getEmbeddedRule("ruleKey_2")).thenReturn(sonarLintRuleDefinition_2);
    when(rulesRepository.getEmbeddedRule("ruleKey_3")).thenReturn(sonarLintRuleDefinition_3);
    when(rulesRepository.getEmbeddedRule("ruleKey_4")).thenReturn(sonarLintRuleDefinition_4);

    var underTest = new TelemetryServerLiveAttributesProvider(mock(ConfigurationRepository.class), mock(ConnectionConfigurationRepository.class), rulesService, rulesRepository);
    var telemetryLiveAttributes = underTest.getTelemetryServerLiveAttributes();


    assertThat(telemetryLiveAttributes.getNonDefaultEnabledRules()).containsExactly("ruleKey_2");
    assertThat(telemetryLiveAttributes.getDefaultDisabledRules()).containsExactly("ruleKey_3");
    assertThat(telemetryLiveAttributes.usesConnectedMode()).isFalse();
    assertThat(telemetryLiveAttributes.usesSonarCloud()).isFalse();
    assertThat(telemetryLiveAttributes.isDevNotificationsDisabled()).isFalse();
  }

  @NotNull
  private static Optional<SonarLintRuleDefinition> getSonarLintRuleDefinition(boolean isActiveByDefault) {
    var sonarLintRuleDefinition = mock(SonarLintRuleDefinition.class);
    when(sonarLintRuleDefinition.isActiveByDefault()).thenReturn(isActiveByDefault);
    return Optional.of(sonarLintRuleDefinition);
  }
}
