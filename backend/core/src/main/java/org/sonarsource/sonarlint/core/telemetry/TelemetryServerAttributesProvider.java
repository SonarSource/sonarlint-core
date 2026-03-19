/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.telemetry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.payload.IDESupportedLanguageViewedPayload.ConnectionType;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.SonarCloudRegion;
import org.sonarsource.sonarlint.core.active.rules.ActiveRulesService;
import org.sonarsource.sonarlint.core.analysis.NodeJsService;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.serverconnection.Organization;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.storage.StorageService;

public class TelemetryServerAttributesProvider {

  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final ActiveRulesService activeRulesService;
  private final RulesRepository rulesRepository;
  private final NodeJsService nodeJsService;
  private final StorageService storageService;

  public TelemetryServerAttributesProvider(ConfigurationRepository configurationRepository,
    ConnectionConfigurationRepository connectionConfigurationRepository, ActiveRulesService activeRulesService, RulesRepository rulesRepository,
    NodeJsService nodeJsService, StorageService storageService) {
    this.configurationRepository = configurationRepository;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.activeRulesService = activeRulesService;
    this.rulesRepository = rulesRepository;
    this.nodeJsService = nodeJsService;
    this.storageService = storageService;
  }

  public TelemetryServerAttributes getTelemetryServerLiveAttributes() {
    var allBindings = configurationRepository.getAllBoundScopes();

    var usesConnectedMode = !allBindings.isEmpty();
    var usesSonarCloud = allBindings.stream().anyMatch(isSonarCloudConnectionConfiguration());

    var childBindingCount = countChildBindings();
    var sonarQubeServerBindingCount = countSonarQubeServerBindings(allBindings);
    var sonarQubeCloudEUBindingCount = countSonarQubeCloudBindings(allBindings, SonarCloudRegion.EU);
    var sonarQubeCloudUSBindingCount = countSonarQubeCloudBindings(allBindings, SonarCloudRegion.US);

    var devNotificationsDisabled = allBindings.stream().anyMatch(this::hasDisableNotifications);

    var nonDefaultEnabledRules = new ArrayList<String>();
    var defaultDisabledRules = new ArrayList<String>();

    activeRulesService.getStandaloneRuleConfig().forEach((ruleKey, standaloneRuleConfigDto) -> {
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

    var connectionsAttributes = connectionConfigurationRepository.getConnectionsById().keySet().stream()
      .map(storageService::connection)
      .map(c -> {
        var userId = c.user().read().orElse(null);
        var serverId = c.serverInfo().read().map(StoredServerInfo::serverId).orElse(null);
        var orgId = c.organization().read().map(Organization::id).orElse(null);

        if (userId == null && serverId == null && orgId == null) {
          return null;
        }

        return new TelemetryConnectionAttributes(userId, serverId, orgId);
      })
      .filter(Objects::nonNull)
      .toList();

    return new TelemetryServerAttributes(usesConnectedMode, usesSonarCloud, childBindingCount, sonarQubeServerBindingCount,
      sonarQubeCloudEUBindingCount, sonarQubeCloudUSBindingCount, devNotificationsDisabled, nonDefaultEnabledRules,
      defaultDisabledRules, nodeJsVersion, connectionsAttributes);
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

  // We are looking for leaf config scope IDs that are bound to a different project key than their parents
  private int countChildBindings() {
    return (int) configurationRepository.getLeafConfigScopeIds().stream()
      .filter(scopeId -> {
        var configScope = configurationRepository.getConfigurationScope(scopeId);
        if (configScope != null && configScope.parentId() != null) {
          var parentBindingConfig = configurationRepository.getBindingConfiguration(configScope.parentId());
          var leafBindingConfig = configurationRepository.getBindingConfiguration(scopeId);
          if (parentBindingConfig != null && leafBindingConfig != null) {
            var parentProjectKey = parentBindingConfig.sonarProjectKey();
            var leafProjectKey = leafBindingConfig.sonarProjectKey();
            return parentProjectKey != null && leafProjectKey != null && !parentProjectKey.equals(leafProjectKey);
          }
        }
        return false;
      })
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

  /**
   * Returns connection-related user information for Gessie events, resolved from the effective binding
   * of the given config scope ID. Returns empty if the scope is not bound.
   */
  public Optional<GessieConnectionInfo> getGessieConnectionInfo(String configScopeId) {
    return configurationRepository.getEffectiveBinding(configScopeId).flatMap(binding -> {
      var connectionId = binding.connectionId();
      var connection = connectionConfigurationRepository.getConnectionById(connectionId);
      if (connection == null) {
        return Optional.empty();
      }
      var storage = storageService.connection(connectionId);
      var userUuid = nullIfEmpty(storage.user().read().orElse(null));
      if (connection instanceof SonarCloudConnectionConfiguration) {
        var organizationUuidV4 = storage.organization().read().map(Organization::uuidV4).map(Object::toString).orElse(null);
        return Optional.of(new GessieConnectionInfo(ConnectionType.SQC, userUuid, organizationUuidV4, null));
      } else if (connection instanceof SonarQubeConnectionConfiguration) {
        var sqsInstallationId = nullIfEmpty(storage.serverInfo().read().map(StoredServerInfo::serverId).orElse(null));
        return Optional.of(new GessieConnectionInfo(ConnectionType.SQS, userUuid, null, sqsInstallationId));
      }
      return Optional.empty();
    });
  }

  @CheckForNull
  private static String nullIfEmpty(@Nullable String value) {
    return (value == null || value.isEmpty()) ? null : value;
  }

}
