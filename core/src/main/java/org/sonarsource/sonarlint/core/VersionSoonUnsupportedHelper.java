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

import com.google.common.eventbus.Subscribe;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverconnection.VersionUtils;
import org.sonarsource.sonarlint.core.sync.SynchronizationServiceImpl;

@Named
@Singleton
public class VersionSoonUnsupportedHelper {

  private static final String UNSUPPORTED_NOTIFICATION_ID = "sonarlint.unsupported.%s.%s.id";
  private static final String NOTIFICATION_MESSAGE = "The version '%s' used by the current connection '%s' will be soon unsupported. " +
    "Please consider upgrading to the latest %s LTS version to ensure continued support and access to the latest features.";
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final SonarLintClient client;
  private final ConfigurationRepository configRepository;
  private final ConnectionConfigurationRepository connectionRepository;
  private final ServerApiProvider serverApiProvider;
  private final SynchronizationServiceImpl synchronizationService;
  private final Map<String, Version> cacheConnectionIdPerVersion;

  public VersionSoonUnsupportedHelper(SonarLintClient client, ConfigurationRepository configRepository, ServerApiProvider serverApiProvider,
    ConnectionConfigurationRepository connectionRepository, SynchronizationServiceImpl synchronizationService) {
    this.client = client;
    this.configRepository = configRepository;
    this.connectionRepository = connectionRepository;
    this.serverApiProvider = serverApiProvider;
    this.synchronizationService = synchronizationService;
    cacheConnectionIdPerVersion = new HashMap<>();
  }

  @Subscribe
  public void configurationScopesAdded(ConfigurationScopesAddedEvent event) {
    var configScopeIds = event.getAddedConfigurationScopeIds();
    checkIfSoonUnsupported(configScopeIds);
  }

  @Subscribe
  public void bindingConfigChanged(BindingConfigChangedEvent event) {
    var configScopeId = event.getConfigScopeId();
    var connectionId = event.getNewConfig().getConnectionId();
    if (connectionId != null) {
      checkIfSoonUnsupported(configScopeId, connectionId);
    }
  }

  private void checkIfSoonUnsupported(Set<String> configScopeIds) {
    var connectionsPerConfigScopeId = new HashMap<String, String>();
    configScopeIds.forEach(configScopeId -> {
      var effectiveBinding = configRepository.getEffectiveBinding(configScopeId);
      if (effectiveBinding.isPresent()) {
        var connectionId = effectiveBinding.get().getConnectionId();
        connectionsPerConfigScopeId.putIfAbsent(connectionId, configScopeId);
      }
    });
    connectionsPerConfigScopeId.forEach((key, value) -> checkIfSoonUnsupported(value, key));
  }

  private void checkIfSoonUnsupported(String configScopeId, String connectionId) {
    var connection = connectionRepository.getConnectionById(connectionId);
    if (connection != null && connection.getKind() == ConnectionKind.SONARQUBE) {
      var serverInfo = serverApiProvider.getServerApi(connectionId);
      if (serverInfo.isPresent()) {
        var version = synchronizationService.getServerConnection(connectionId, serverInfo.get()).readOrSynchronizeServerVersion(serverInfo.get());
        var isCached = cacheConnectionIdPerVersion.containsKey(connectionId) && cacheConnectionIdPerVersion.get(connectionId).compareTo(version) == 0;
        if (!isCached && VersionUtils.isVersionSupportedDuringGracePeriod(version)) {
          client.showSoonUnsupportedMessage(
            new ShowSoonUnsupportedMessageParams(
              String.format(UNSUPPORTED_NOTIFICATION_ID, connectionId, version.getName()),
              configScopeId,
              String.format(NOTIFICATION_MESSAGE, version.getName(), connectionId, VersionUtils.getCurrentLts())
            )
          );
          LOG.debug(String.format("Connection ID '%s' with version '%s' is detected to be soon unsupported",
            connection.getConnectionId(), version.getName()));
        }
        cacheConnectionIdPerVersion.put(connectionId, version);
      }
    }
  }

}
