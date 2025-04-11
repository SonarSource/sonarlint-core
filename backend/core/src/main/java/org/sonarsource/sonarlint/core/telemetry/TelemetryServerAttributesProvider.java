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
package org.sonarsource.sonarlint.core.telemetry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.SonarCloudRegion;
import org.sonarsource.sonarlint.core.analysis.NodeJsService;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.rules.RulesService;

public class TelemetryServerAttributesProvider {

  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final RulesService rulesService;
  private final RulesRepository rulesRepository;
  private final NodeJsService nodeJsService;

  public TelemetryServerAttributesProvider(ConfigurationRepository configurationRepository,
    ConnectionConfigurationRepository connectionConfigurationRepository,
    RulesService rulesService, RulesRepository rulesRepository, NodeJsService nodeJsService) {
    this.configurationRepository = configurationRepository;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.rulesService = rulesService;
    this.rulesRepository = rulesRepository;
    this.nodeJsService = nodeJsService;
  }

  public TelemetryServerAttributes getTelemetryServerLiveAttributes() {
    var allBindings = configurationRepository.getAllBoundScopes();

    var usesConnectedMode = !allBindings.isEmpty();

    var usesSonarCloud = allBindings.stream().anyMatch(isSonarCloudConnectionConfiguration());

    var sonarQubeServerBindingCount = countSonarQubeServerBindings(allBindings);

    var sonarQubeCloudEUBindingCount = countSonarQubeCloudBindings(allBindings, SonarCloudRegion.EU);

    var sonarQubeCloudUSBindingCount = countSonarQubeCloudBindings(allBindings, SonarCloudRegion.US);

    var devNotificationsDisabled = allBindings.stream().anyMatch(this::hasDisableNotifications);

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

    var nodeJsVersion = getNodeJsVersion();

    return new TelemetryServerAttributes(usesConnectedMode, usesSonarCloud, sonarQubeServerBindingCount,
      sonarQubeCloudEUBindingCount, sonarQubeCloudUSBindingCount, devNotificationsDisabled, nonDefaultEnabledRules,
      defaultDisabledRules, nodeJsVersion);
  }

  private int countSonarQubeCloudBindings(Collection<BoundScope> allBindings, SonarCloudRegion region) {
    return (int) allBindings.stream()
      .filter(binding -> {
        if (connectionConfigurationRepository.getConnectionById(binding.getConnectionId()) instanceof SonarCloudConnectionConfiguration scBinding) {
          return region.equals(scBinding.getRegion());
        }
        return false;
      }).count();
  }

  private int countSonarQubeServerBindings(Collection<BoundScope> allBindings) {
    return (int) allBindings.stream()
      .filter(binding -> connectionConfigurationRepository.getConnectionById(binding.getConnectionId()) instanceof SonarQubeConnectionConfiguration)
      .count();
  }

  @CheckForNull
  private String getNodeJsVersion() {
    return nodeJsService.getActiveNodeJsVersion().map(Objects::toString).orElse(null);
  }

  private boolean hasDisableNotifications(BoundScope binding) {
    return Objects.requireNonNull(connectionConfigurationRepository.getConnectionById(binding.getConnectionId())).isDisableNotifications();
  }

  private Predicate<BoundScope> isSonarCloudConnectionConfiguration() {
    return binding -> connectionConfigurationRepository.getConnectionById(binding.getConnectionId()) instanceof SonarCloudConnectionConfiguration;
  }
}
