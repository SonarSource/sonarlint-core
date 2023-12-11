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
package org.sonarsource.sonarlint.core.telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryServerLiveAttributesDto;
import org.sonarsource.sonarlint.core.rules.RulesService;

@Named
@Singleton
public class TelemetryServerLiveAttributesProvider {

  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final RulesService rulesService;
  private final RulesRepository rulesRepository;

  public TelemetryServerLiveAttributesProvider(ConfigurationRepository configurationRepository,
    ConnectionConfigurationRepository connectionConfigurationRepository,
    RulesService rulesService, RulesRepository rulesRepository) {
    this.configurationRepository = configurationRepository;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.rulesService = rulesService;
    this.rulesRepository = rulesRepository;
  }

  public TelemetryServerLiveAttributesDto getTelemetryServerLiveAttributes() {
    var allBindings = configurationRepository.getAllBoundScopes();

    var usesConnectedMode = !allBindings.isEmpty();

    var usesSonarCloud = allBindings.stream()
      .anyMatch(binding -> connectionConfigurationRepository.getConnectionById(binding.getConnectionId()) instanceof SonarCloudConnectionConfiguration);

    var devNotificationsDisabled = allBindings.stream()
      .anyMatch(binding -> Objects.requireNonNull(connectionConfigurationRepository.getConnectionById(binding.getConnectionId())).isDisableNotifications());

    List<String> nonDefaultEnabledRules = new ArrayList<>();
    List<String> defaultDisabledRules = new ArrayList<>();

    rulesService.getStandaloneRuleConfig().forEach((ruleKey, standaloneRuleConfigDto) -> {
      var optionalEmbeddedRule = rulesRepository.getEmbeddedRule(ruleKey);
      if (optionalEmbeddedRule.isEmpty()) {
        return;
      }
      var activeByDefault = optionalEmbeddedRule.get().isActiveByDefault();
      var isActive = standaloneRuleConfigDto.isActive();
      if (activeByDefault && !isActive) {
        defaultDisabledRules.add(ruleKey);
      } else if (!activeByDefault && isActive) {
        nonDefaultEnabledRules.add(ruleKey);
      }
    });

    return new TelemetryServerLiveAttributesDto(usesConnectedMode, usesSonarCloud, devNotificationsDisabled, nonDefaultEnabledRules,
      defaultDisabledRules);
  }
}
