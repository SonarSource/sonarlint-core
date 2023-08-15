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
package org.sonarsource.sonarlint.core.websocket;

import com.google.common.eventbus.Subscribe;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverconnection.events.EventDispatcher;
import org.sonarsource.sonarlint.core.smartnotifications.SmartNotifications;
import org.sonarsource.sonarlint.core.websocket.events.QualityGateChangedEvent;

import static java.util.Objects.requireNonNull;

public class WebSocketService {
  protected final Map<String, String> subscribedProjectKeysByConfigScopes = new HashMap<>();
  protected final Set<String> connectionIdsInterestedInNotifications = new HashSet<>();
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final ConfigurationRepository configurationRepository;
  private final ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider;
  protected SonarCloudWebSocket sonarCloudWebSocket;
  private final EventDispatcher eventRouter;
  private final SmartNotifications smartNotifications;

  public WebSocketService(SonarLintClient client, ConnectionConfigurationRepository connectionConfigurationRepository, ConfigurationRepository configurationRepository,
    ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider, SmartNotifications smartNotifications) {
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.configurationRepository = configurationRepository;
    this.connectionAwareHttpClientProvider = connectionAwareHttpClientProvider;
    this.eventRouter = new EventDispatcher()
      .dispatch(QualityGateChangedEvent.class, new ShowSmartNotificationOnQualityGateChangedEvent(client, configurationRepository));
    this.smartNotifications = smartNotifications;
  }

  protected void refreshConnectionIfNeeded() {
    var connectionId = connectionIdsInterestedInNotifications.stream().findFirst().orElse(null);
    if (this.sonarCloudWebSocket != null && connectionId != null) {
      // If connection already exists, close it and create new one before it expires on its own
      closeSocket();
      createConnectionIfNeeded(connectionId);
      resubscribeAll();
    }
  }

  @Subscribe
  public void handleEvent(BindingConfigChangedEvent bindingConfigChangedEvent) {
    var newProjectKey = bindingConfigChangedEvent.getNewConfig().getSonarProjectKey();
    var configScopeId = bindingConfigChangedEvent.getNewConfig().getConfigScopeId();
    String connectionId = bindingConfigChangedEvent.getNewConfig().getConnectionId();
    var connection = connectionId != null ? connectionConfigurationRepository.getConnectionById(connectionId) : null;
    var isSonarCloudConnection = connection != null && connection.getKind().equals(ConnectionKind.SONARCLOUD);
    var projectKey = subscribedProjectKeysByConfigScopes.remove(bindingConfigChangedEvent.getNewConfig().getConfigScopeId());
    if (projectKey != null && !subscribedProjectKeysByConfigScopes.containsValue(projectKey)) {
      unsubscribe(projectKey);
    }
    if (newProjectKey != null && isSonarCloudConnection && !connection.isDisableNotifications()) {
      createConnectionIfNeeded(connectionId);
      subscribe(configScopeId, newProjectKey);
    }
  }

  @Subscribe
  public void handleEvent(ConfigurationScopesAddedEvent configurationScopesAddedEvent) {
    subscribeAllBoundConfigurationScopes(configurationScopesAddedEvent.getAddedConfigurationScopeIds());
  }

  @Subscribe
  public void handleEvent(ConfigurationScopeRemovedEvent configurationScopeRemovedEvent) {
    var projectKey = subscribedProjectKeysByConfigScopes.remove(configurationScopeRemovedEvent.getRemovedConfigurationScopeId());
    if (!subscribedProjectKeysByConfigScopes.containsValue(projectKey)) {
      unsubscribe(projectKey);
    }
  }

  @Subscribe
  public void handleEvent(ConnectionConfigurationAddedEvent connectionConfigurationAddedEvent) {
    // This is only to handle the case where binding was invalid (connection did not exist) and became valid (matching connection was created)
    var configScopeIds = configurationRepository.getConfigScopesWithBindingConfiguredTo(connectionConfigurationAddedEvent.getAddedConnectionId())
      .stream().map(ConfigurationScope::getId)
      .collect(Collectors.toSet());
    subscribeAllBoundConfigurationScopes(configScopeIds);
  }

  @Subscribe
  public void handleEvent(ConnectionConfigurationUpdatedEvent connectionConfigurationUpdatedEvent) {
    var updatedConnectionId = connectionConfigurationUpdatedEvent.getUpdatedConnectionId();
    var connection = connectionConfigurationRepository.getConnectionById(updatedConnectionId);
    if (connection != null && connection.getKind().equals(ConnectionKind.SONARCLOUD)) {
      // TODO what if connection type changed from SQ to SC or vice versa?
      closeSocket();
      boolean notificationsGotDisabledForConnection = connectionIdsInterestedInNotifications.contains(updatedConnectionId) && connection.isDisableNotifications();
      if (notificationsGotDisabledForConnection) {
        connectionIdsInterestedInNotifications.remove(updatedConnectionId);
        removeProjectsFromSubscriptionListForConnection(updatedConnectionId);
      }
      if (!connection.isDisableNotifications()) {
        createConnectionIfNeeded(updatedConnectionId);
        subscribeAllBoundConfigurationScopes(configurationRepository.getConfigScopeIds());
      } else if (!connectionIdsInterestedInNotifications.isEmpty()) {
        connectionIdsInterestedInNotifications.remove(updatedConnectionId);
        removeProjectsFromSubscriptionListForConnection(updatedConnectionId);
        // Some other connection needs WebSocket
        createConnectionIfNeeded(connectionIdsInterestedInNotifications.stream().findFirst().orElse(null));
        resubscribeAll();
      }
    }
  }

  @Subscribe
  public void handleEvent(ConnectionConfigurationRemovedEvent connectionConfigurationRemovedEvent) {
    String removedConnectionId = connectionConfigurationRemovedEvent.getRemovedConnectionId();
    connectionIdsInterestedInNotifications.remove(removedConnectionId);
    removeProjectsFromSubscriptionListForConnection(removedConnectionId);
    if (connectionIdsInterestedInNotifications.isEmpty()) {
      closeSocket();
    }
  }

  private void subscribeAllBoundConfigurationScopes(Set<String> configScopeIds) {
    for (var configurationScopeId : configScopeIds) {
      var bindingConfiguration = configurationRepository.getBindingConfiguration(configurationScopeId);
      if (bindingConfiguration != null && bindingConfiguration.isBound()) {
        var connection = connectionConfigurationRepository.getConnectionById(requireNonNull(bindingConfiguration.getConnectionId()));
        if (connection != null && connection.getKind().equals(ConnectionKind.SONARCLOUD) &&
          !connection.isDisableNotifications()) {
          createConnectionIfNeeded(bindingConfiguration.getConnectionId());
          subscribe(configurationScopeId, bindingConfiguration.getSonarProjectKey());
        }
      }
    }
  }

  private void removeProjectsFromSubscriptionListForConnection(String updatedConnectionId) {
    var configurationScopesToUnsubscribe = configurationRepository.getConfigScopesWithBindingConfiguredTo(updatedConnectionId);
    for (var configScope : configurationScopesToUnsubscribe) {
      subscribedProjectKeysByConfigScopes.remove(configScope.getId());
    }
  }

  private void subscribe(String configScopeId, String projectKey) {
    sonarCloudWebSocket.subscribe(projectKey);
    subscribedProjectKeysByConfigScopes.put(configScopeId, projectKey);
  }

  private void unsubscribe(String projectKey) {
    sonarCloudWebSocket.unsubscribe(projectKey);
  }

  private void resubscribeAll() {
    subscribedProjectKeysByConfigScopes.forEach((configScope, projectKey) -> sonarCloudWebSocket.subscribe(projectKey));
  }

  private void createConnectionIfNeeded(String connectionId) {
    connectionIdsInterestedInNotifications.add(connectionId);
    if (this.sonarCloudWebSocket == null) {
      this.sonarCloudWebSocket = SonarCloudWebSocket.create(connectionAwareHttpClientProvider.getHttpClient(connectionId), eventRouter::handle, this::refreshConnectionIfNeeded);
      if (this.sonarCloudWebSocket.isConnectionSuccessful()) {
        this.smartNotifications.addEventsToIgnore(this.sonarCloudWebSocket.getSupportedEventTypes());
      }
    }
  }

  private void closeSocket() {
    if (this.sonarCloudWebSocket != null) {
      this.sonarCloudWebSocket.close();
      this.sonarCloudWebSocket = null;
    }
  }

  @PreDestroy
  public void shutdown() {
    closeSocket();
    connectionIdsInterestedInNotifications.clear();
    subscribedProjectKeysByConfigScopes.clear();
  }
}
