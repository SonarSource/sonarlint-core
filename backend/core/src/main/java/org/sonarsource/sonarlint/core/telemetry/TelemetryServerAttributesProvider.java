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
package org.sonarsource.sonarlint.core.telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.CheckForNull;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.commons.lang3.SystemUtils;
import org.sonarsource.sonarlint.core.analysis.NodeJsService;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.rules.RulesService;

@Named
@Singleton
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

  public TelemetryServerConstantAttributes getTelemetryServerConstantAttributes() {
    var architecture = SystemUtils.OS_ARCH;
    var platform = SystemUtils.OS_NAME;
    return new TelemetryServerConstantAttributes(platform, architecture);
  }

  public TelemetryServerLiveAttributes getTelemetryServerLiveAttributes() {
    var allBindings = configurationRepository.getAllBoundScopes();

    var usesConnectedMode = !allBindings.isEmpty();

    var usesSonarCloud = allBindings.stream().anyMatch(isSonarCloudConnectionConfiguration());

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

    return new TelemetryServerLiveAttributes(usesConnectedMode, usesSonarCloud, devNotificationsDisabled, nonDefaultEnabledRules,
      defaultDisabledRules, nodeJsVersion);
  }

  @CheckForNull
  private String getNodeJsVersion() {
    var nodeJsVersion = nodeJsService.getNodeJsVersion();
    return nodeJsVersion == null ? null : nodeJsVersion.toString();
  }

  private boolean hasDisableNotifications(BoundScope binding) {
    return Objects.requireNonNull(connectionConfigurationRepository.getConnectionById(binding.getConnectionId())).isDisableNotifications();
  }

  private Predicate<BoundScope> isSonarCloudConnectionConfiguration() {
    return binding -> connectionConfigurationRepository.getConnectionById(binding.getConnectionId()) instanceof SonarCloudConnectionConfiguration;
  }
}
