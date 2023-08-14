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
import com.google.gson.Gson;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
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

import static java.util.Objects.requireNonNull;

public class WebSocketService {
  public static final String WEBSOCKET_DEV_URL = "wss://squad-5-events-api.sc-dev.io/";
  public static final String WEBSOCKET_URL = "wss://events-api.sonarcloud.io/";
  protected final Map<String, String> subscribedProjectKeysByConfigScopes = new HashMap<>();
  protected final Set<String> connectionIdsInterestedInNotifications = new HashSet<>();
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final ConfigurationRepository configurationRepository;
  private final ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider;
  protected WebSocket ws;

  public WebSocketService(ConnectionConfigurationRepository connectionConfigurationRepository, ConfigurationRepository configurationRepository,
    ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider) {
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.configurationRepository = configurationRepository;
    this.connectionAwareHttpClientProvider = connectionAwareHttpClientProvider;
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
    sendSubscriptionMessage(projectKey);

    subscribedProjectKeysByConfigScopes.put(configScopeId, projectKey);
  }

  private void resubscribeAll() {
    subscribedProjectKeysByConfigScopes.forEach((configScope, projectKey) -> sendSubscriptionMessage(projectKey));
  }

  private void sendSubscriptionMessage(String projectKey) {
    var subscribePayload = new WebSocketEventSubscribePayload("subscribe", "QualityGateChanged", projectKey);

    var gson = new Gson();
    var jsonString = gson.toJson(subscribePayload);

    SonarLintLogger.get().debug("subscribed for events");

    this.ws.sendText(jsonString, true);
  }

  private void unsubscribe(String projectKey) {
    var unsubscribePayload = new WebSocketEventSubscribePayload("unsubscribe", "QualityGateChanged", projectKey);

    var gson = new Gson();
    var jsonString = gson.toJson(unsubscribePayload);


    if(this.ws != null) {
      SonarLintLogger.get().debug("unsubscribed for events");
      this.ws.sendText(jsonString, true);
    }
  }

  private void createConnectionIfNeeded(String connectionId) {
    connectionIdsInterestedInNotifications.add(connectionId);
    if (this.ws == null) {
      this.ws = connectionAwareHttpClientProvider.getHttpClient(connectionId).createWebSocketConnection(WEBSOCKET_DEV_URL);
    }
  }

  private void closeSocket() {
    if (this.ws != null) {
      this.ws.sendClose(WebSocket.NORMAL_CLOSURE, "");
      this.ws = null;
    }
  }

  @PreDestroy
  public void shutdown() {
    closeSocket();
    connectionIdsInterestedInNotifications.clear();
    subscribedProjectKeysByConfigScopes.clear();
  }
}
