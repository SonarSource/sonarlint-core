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
package org.sonarsource.sonarlint.core;

import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ExecutorServiceShutdownWatchable;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.util.FailSafeExecutors;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedWithBindingEvent;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.serverconnection.VersionUtils;
import org.sonarsource.sonarlint.core.sync.SynchronizationService;
import org.springframework.context.event.EventListener;

public class VersionSoonUnsupportedHelper {

  private static final String UNSUPPORTED_NOTIFICATION_ID = "sonarlint.unsupported.%s.%s.id";
  private static final String NOTIFICATION_MESSAGE = "The version '%s' used by the current connection '%s' will be soon unsupported. " +
    "Please consider upgrading to the latest %s LTS version to ensure continued support and access to the latest features.";
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final SonarLintRpcClient client;
  private final ConfigurationRepository configRepository;
  private final ConnectionConfigurationRepository connectionRepository;
  private final SonarQubeClientManager sonarQubeClientManager;
  private final SynchronizationService synchronizationService;
  private final Map<String, Version> cacheConnectionIdPerVersion = new ConcurrentHashMap<>();
  private final ExecutorServiceShutdownWatchable<?> executorService;

  public VersionSoonUnsupportedHelper(SonarLintRpcClient client, ConfigurationRepository configRepository, SonarQubeClientManager sonarQubeClientManager,
    ConnectionConfigurationRepository connectionRepository, SynchronizationService synchronizationService) {
    this.client = client;
    this.configRepository = configRepository;
    this.connectionRepository = connectionRepository;
    this.sonarQubeClientManager = sonarQubeClientManager;
    this.synchronizationService = synchronizationService;
    this.executorService = new ExecutorServiceShutdownWatchable<>(FailSafeExecutors.newSingleThreadExecutor("Version Soon Unsupported Helper"));
  }

  @EventListener
  public void configurationScopesAdded(ConfigurationScopesAddedWithBindingEvent event) {
    var configScopeIds = event.getConfigScopeIds();
    checkIfSoonUnsupportedOncePerConnection(configScopeIds);
  }

  @EventListener
  public void bindingConfigChanged(BindingConfigChangedEvent event) {
    var configScopeId = event.configScopeId();
    var connectionId = event.newConfig().connectionId();
    if (connectionId != null) {
      queueCheckIfSoonUnsupported(connectionId, configScopeId);
    }
  }

  private void checkIfSoonUnsupportedOncePerConnection(Set<String> configScopeIds) {
    // We will check once per connection, and send the notification for the first config scope associated to this connection
    var oneConfigScopeIdPerConnection = new HashMap<String, String>();
    configScopeIds.forEach(configScopeId -> {
      var effectiveBinding = configRepository.getEffectiveBinding(configScopeId);
      if (effectiveBinding.isPresent()) {
        var connectionId = effectiveBinding.get().connectionId();
        oneConfigScopeIdPerConnection.putIfAbsent(connectionId, configScopeId);
      }
    });
    oneConfigScopeIdPerConnection.forEach(this::queueCheckIfSoonUnsupported);
  }

  private void queueCheckIfSoonUnsupported(String connectionId, String configScopeId) {
    var cancelMonitor = new SonarLintCancelMonitor();
    cancelMonitor.watchForShutdown(executorService);
    executorService.execute(() -> {
      try {
        var connection = connectionRepository.getConnectionById(connectionId);
        if (connection != null && connection.getKind() == ConnectionKind.SONARQUBE) {
          sonarQubeClientManager.withActiveClient(connectionId, serverApi -> {
            var version = synchronizationService.readOrSynchronizeServerVersion(connectionId, serverApi, cancelMonitor);
            var isCached = cacheConnectionIdPerVersion.containsKey(connectionId) && cacheConnectionIdPerVersion.get(connectionId).compareTo(version) == 0;
            if (!isCached && VersionUtils.isVersionSupportedDuringGracePeriod(version)) {
              client.showSoonUnsupportedMessage(
                new ShowSoonUnsupportedMessageParams(
                  String.format(UNSUPPORTED_NOTIFICATION_ID, connectionId, version.getName()),
                  configScopeId,
                  String.format(NOTIFICATION_MESSAGE, version.getName(), connectionId, VersionUtils.getCurrentLts())));
              LOG.debug(String.format("Connection '%s' with version '%s' is detected to be soon unsupported",
                connection.getConnectionId(), version.getName()));
            }
            cacheConnectionIdPerVersion.put(connectionId, version);
          });
        }
      } catch (Exception e) {
        LOG.error("Error while checking if soon unsupported", e);
      }
    });
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop version soon unsupported executor service in a timely manner");
    }
  }

}
