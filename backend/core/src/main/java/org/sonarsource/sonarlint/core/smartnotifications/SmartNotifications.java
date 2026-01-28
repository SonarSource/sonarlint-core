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
package org.sonarsource.sonarlint.core.smartnotifications;

import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ExecutorServiceShutdownWatchable;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.util.FailSafeExecutors;
import org.sonarsource.sonarlint.core.event.SonarServerEventReceivedEvent;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.developers.SearchEventsResponseDto;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.websocket.WebSocketService;
import org.sonarsource.sonarlint.core.websocket.events.SmartNotificationEvent;
import org.springframework.context.event.EventListener;

import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.SMART_NOTIFICATIONS;

public class SmartNotifications {

  private final SonarLintLogger logger = SonarLintLogger.get();

  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionRepository;
  private final SonarQubeClientManager sonarQubeClientManager;
  private final SonarLintRpcClient client;
  private final TelemetryService telemetryService;
  private final WebSocketService webSocketService;
  private final InitializeParams params;
  private final LastEventPolling lastEventPollingService;
  private ExecutorServiceShutdownWatchable<ScheduledExecutorService> smartNotificationsPolling;

  public SmartNotifications(ConfigurationRepository configurationRepository, ConnectionConfigurationRepository connectionRepository, SonarQubeClientManager sonarQubeClientManager,
    SonarLintRpcClient client, StorageService storageService, TelemetryService telemetryService, WebSocketService webSocketService, InitializeParams params) {
    this.configurationRepository = configurationRepository;
    this.connectionRepository = connectionRepository;
    this.sonarQubeClientManager = sonarQubeClientManager;
    this.client = client;
    this.telemetryService = telemetryService;
    this.webSocketService = webSocketService;
    this.params = params;
    lastEventPollingService = new LastEventPolling(storageService);
  }

  @PostConstruct
  public void initialize() {
    if (!params.getBackendCapabilities().contains(SMART_NOTIFICATIONS)) {
      return;
    }
    smartNotificationsPolling = new ExecutorServiceShutdownWatchable<>(FailSafeExecutors.newSingleThreadScheduledExecutor("Smart Notifications Polling"));
    var cancelMonitor = new SonarLintCancelMonitor();
    cancelMonitor.watchForShutdown(smartNotificationsPolling);
    smartNotificationsPolling.getWrapped().scheduleAtFixedRate(() -> this.poll(cancelMonitor), 1, 60, TimeUnit.SECONDS);
  }

  private void poll(SonarLintCancelMonitor cancelMonitor) {
    var boundScopeByConnectionAndSonarProject = configurationRepository.getBoundScopeByConnectionAndSonarProject();
    boundScopeByConnectionAndSonarProject.forEach((connectionId, boundScopesByProject) -> {
      var connection = connectionRepository.getConnectionById(connectionId);
      if (connection != null && !connection.isDisableNotifications() && !shouldSkipPolling(connection)) {
        sonarQubeClientManager.withActiveClient(connectionId,
          serverApi -> manageNotificationsForConnection(serverApi, boundScopesByProject, connection, cancelMonitor));
      }
    });
  }

  private void manageNotificationsForConnection(ServerApi serverApi, Map<String, Collection<BoundScope>> boundScopesPerProjectKey,
    AbstractConnectionConfiguration connection, SonarLintCancelMonitor cancelMonitor) {
    var developersApi = serverApi.developers();
    var connectionId = connection.getConnectionId();
    var projectKeysByLastEventPolling = boundScopesPerProjectKey.keySet().stream()
      .collect(Collectors.toMap(Function.identity(),
        p -> getLastNotificationTime(lastEventPollingService.getLastEventPolling(connectionId, p))));

    List<SearchEventsResponseDto.Event> notifications;
    try {
      notifications = developersApi.searchEvents(projectKeysByLastEventPolling, cancelMonitor).events();
    } catch (Exception e) {
      logger.error("Failed to get notifications", e);
      notifications = List.of();
    }

    for (var n : notifications) {
      var scopeIds = boundScopesPerProjectKey.get(n.project()).stream().map(BoundScope::getConfigScopeId).collect(Collectors.toSet());
      var smartNotification = new ShowSmartNotificationParams(n.message(), n.link(), scopeIds,
        n.category(), connectionId);
      client.showSmartNotification(smartNotification);
      telemetryService.smartNotificationsReceived(n.category());
    }

    projectKeysByLastEventPolling.keySet()
      .forEach(projectKey -> lastEventPollingService.setLastEventPolling(ZonedDateTime.now(), connectionId, projectKey));
  }

  private boolean shouldSkipPolling(AbstractConnectionConfiguration connection) {
    if (connection.getKind() == ConnectionKind.SONARCLOUD) {
      var region = ((SonarCloudConnectionConfiguration) connection).getRegion();
      return webSocketService.hasOpenConnection(region);
    }
    return false;
  }

  @PreDestroy
  public void shutdown() {
    if (smartNotificationsPolling != null && !MoreExecutors.shutdownAndAwaitTermination(smartNotificationsPolling, 5, TimeUnit.SECONDS)) {
      logger.warn("Unable to stop smart notifications executor service in a timely manner");
    }
  }

  private static ZonedDateTime getLastNotificationTime(ZonedDateTime lastTime) {
    var oneDayAgo = ZonedDateTime.now().minusDays(1);
    return lastTime.isAfter(oneDayAgo) ? lastTime : oneDayAgo;
  }

  @EventListener
  public void onServerEventReceived(SonarServerEventReceivedEvent eventReceived) {
    var serverEvent = eventReceived.getEvent();
    if (serverEvent instanceof SmartNotificationEvent smartNotificationEvent) {
      notifyClient(eventReceived.getConnectionId(), smartNotificationEvent);
    }
  }

  private void notifyClient(String connectionId, SmartNotificationEvent event) {
    var projectKey = event.project();
    var boundScopes = configurationRepository.getBoundScopesToConnectionAndSonarProject(connectionId, projectKey);
    client.showSmartNotification(new ShowSmartNotificationParams(event.message(), event.link(),
      boundScopes.stream().map(BoundScope::getConfigScopeId).collect(Collectors.toSet()), event.category(), connectionId));
    telemetryService.smartNotificationsReceived(event.category());
  }

}
