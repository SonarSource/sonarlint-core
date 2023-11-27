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
package org.sonarsource.sonarlint.core.smartnotifications;

import com.google.common.util.concurrent.MoreExecutors;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.SonarServerEventReceivedEvent;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.developers.DevelopersApi;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.websocket.WebSocketService;
import org.sonarsource.sonarlint.core.websocket.events.SmartNotificationEvent;
import org.springframework.context.event.EventListener;

@Named
@Singleton
public class SmartNotifications {

  private final SonarLintLogger logger = SonarLintLogger.get();

  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionRepository;
  private final ServerApiProvider serverApiProvider;
  private final SonarLintRpcClient client;
  private final TelemetryService telemetryService;
  private final WebSocketService webSocketService;
  private final InitializeParams params;
  private final Map<String, Boolean> isConnectionIdSupported;
  private final LastEventPolling lastEventPollingService;
  private ScheduledExecutorService smartNotificationsPolling;

  public SmartNotifications(ConfigurationRepository configurationRepository, ConnectionConfigurationRepository connectionRepository, ServerApiProvider serverApiProvider,
    SonarLintRpcClient client, StorageService storageService, TelemetryService telemetryService, WebSocketService webSocketService, InitializeParams params) {
    this.configurationRepository = configurationRepository;
    this.connectionRepository = connectionRepository;
    this.serverApiProvider = serverApiProvider;
    this.client = client;
    this.telemetryService = telemetryService;
    this.webSocketService = webSocketService;
    this.params = params;
    isConnectionIdSupported = new HashMap<>();
    lastEventPollingService = new LastEventPolling(storageService);
  }

  @PostConstruct
  public void initialize() {
    if (!params.getFeatureFlags().shouldManageSmartNotifications()) {
      return;
    }
    smartNotificationsPolling = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Smart Notifications Polling"));
    smartNotificationsPolling.scheduleAtFixedRate(this::poll, 1, 60, TimeUnit.SECONDS);
  }

  private void poll() {
    var keysAndScopeIdsPerConnectionId = configurationRepository.getBoundScopeByConnectionAndSonarProject();

    for (var keysAndScopeIds : keysAndScopeIdsPerConnectionId.entrySet()) {
      var connectionId = keysAndScopeIds.getKey();
      var connection = connectionRepository.getConnectionById(connectionId);
      var httpClient = serverApiProvider.getServerApi(connectionId);

      if (connection != null && !connection.isDisableNotifications() && httpClient.isPresent() && !shouldSkipPolling(connection)) {
        manageNotificationsForConnection(httpClient.get(), keysAndScopeIds.getValue(), connection);
      }
    }
  }

  private void manageNotificationsForConnection(ServerApi serverApi, Map<String, Set<BoundScope>> scopeIdsPerProjectKey,
    AbstractConnectionConfiguration connection) {
    var developersApi = serverApi.developers();
    var connectionId = connection.getConnectionId();
    var isSupported = isConnectionIdSupported.computeIfAbsent(connectionId, v -> developersApi.isSupported());
    if (Boolean.TRUE.equals(isSupported)) {
      var projectKeysByLastEventPolling = scopeIdsPerProjectKey.keySet().stream()
        .collect(Collectors.toMap(Function.identity(),
          p -> getLastNotificationTime(lastEventPollingService.getLastEventPolling(connectionId, p))));

      var notifications = retrieveServerNotifications(developersApi, projectKeysByLastEventPolling);

      for (var n : notifications) {
        var smartNotification = new ShowSmartNotificationParams(n.message(), n.link(),
          scopeIdsPerProjectKey.get(n.projectKey()).stream().map(BoundScope::getConfigScopeId).collect(Collectors.toSet()),
          n.category(), connectionId);
        client.showSmartNotification(smartNotification);
        telemetryService.smartNotificationsReceived(n.category());
      }

      projectKeysByLastEventPolling.keySet()
        .forEach(projectKey -> lastEventPollingService.setLastEventPolling(ZonedDateTime.now(), connectionId, projectKey));
    }
  }

  private boolean shouldSkipPolling(AbstractConnectionConfiguration connection) {
    return connection.getKind() == ConnectionKind.SONARCLOUD && webSocketService.hasOpenConnection();
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

  private static List<ServerNotification> retrieveServerNotifications(DevelopersApi developersApi,
    Map<String, ZonedDateTime> projectKeysByLastEventPolling) {
    return developersApi.getEvents(projectKeysByLastEventPolling)
      .stream().map(e -> new ServerNotification(
        e.getCategory(),
        e.getMessage(),
        e.getLink(),
        e.getProjectKey(),
        e.getTime()))
      .collect(Collectors.toList());
  }

  @EventListener
  public void onServerEventReceived(SonarServerEventReceivedEvent eventReceived) {
    var serverEvent = eventReceived.getEvent();
    if (serverEvent instanceof SmartNotificationEvent) {
      notifyClient(eventReceived.getConnectionId(), (SmartNotificationEvent) serverEvent);
    }
  }

  private void notifyClient(String connectionId, SmartNotificationEvent event) {
    var projectKey = event.getProject();
    var boundScopes = configurationRepository.getBoundScopesToConnectionAndSonarProject(connectionId, projectKey);
    client.showSmartNotification(new ShowSmartNotificationParams(event.getMessage(), event.getLink(),
      boundScopes.stream().map(BoundScope::getConfigScopeId).collect(Collectors.toSet()), event.getCategory(), connectionId));
    telemetryService.smartNotificationsReceived(event.getCategory());
  }

}
