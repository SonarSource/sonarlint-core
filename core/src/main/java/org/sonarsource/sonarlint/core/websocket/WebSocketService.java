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
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;

import static java.util.Objects.requireNonNull;

public class WebSocketService {
  private static final String WEBSOCKET_URL = "wss://squad-5-events-api.sc-dev.io/";
  private final Map<String, String> subscribedProjectKeysByConfigScopes = new HashMap<>();
  private final Set<String> connectionIdsInterestedInNotifications = new HashSet<>();
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final ConfigurationRepository configurationRepository;
  private final ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider;
  private WebSocket ws;

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
    for (var configurationScopeId : configurationScopesAddedEvent.getAddedConfigurationScopeIds()) {
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
    var configScopes = configurationRepository.getConfigScopesWithBindingConfiguredTo(connectionConfigurationAddedEvent.getAddedConnectionId());
    for (var configurationScope : configScopes) {
      var bindingConfiguration = configurationRepository.getBindingConfiguration(configurationScope.getId());
      if (bindingConfiguration != null && bindingConfiguration.isBound()) {
        var connection = connectionConfigurationRepository.getConnectionById(requireNonNull(bindingConfiguration.getConnectionId()));
        if (connection != null && connection.getKind().equals(ConnectionKind.SONARCLOUD) &&
          !connection.isDisableNotifications()) {
          createConnectionIfNeeded(bindingConfiguration.getConnectionId());
          subscribe(configurationScope.getId(), bindingConfiguration.getSonarProjectKey());
        }
      }
    }
  }

  @Subscribe
  public void handleEvent(ConnectionConfigurationUpdatedEvent connectionConfigurationUpdatedEvent) {
    var updatedConnectionId = connectionConfigurationUpdatedEvent.getUpdatedConnectionId();
    var connection = connectionConfigurationRepository.getConnectionById(updatedConnectionId);
    if (connection != null && connection.getKind().equals(ConnectionKind.SONARCLOUD)) { // TODO what if connection type changed from SQ to SC or vice versa?
      closeConnection(updatedConnectionId);
      if (!connection.isDisableNotifications() || !connectionIdsInterestedInNotifications.isEmpty()) {
        createConnectionIfNeeded(connection.getConnectionId());
        resubscribeAll();
      }
    }
  }

  @Subscribe
  public void handleEvent(ConnectionConfigurationRemovedEvent connectionConfigurationRemovedEvent) {
    String removedConnectionId = connectionConfigurationRemovedEvent.getRemovedConnectionId();
    if (connectionIdsInterestedInNotifications.size() == 1 && connectionIdsInterestedInNotifications.contains(removedConnectionId)) {
      closeConnection(removedConnectionId);
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

    SonarLintLogger.get().debug("unsubscribed for events");

    this.ws.sendText(jsonString, true);
  }

  private void createConnectionIfNeeded(String connectionId) {
    connectionIdsInterestedInNotifications.add(connectionId);
    if (this.ws == null) {
      this.ws = connectionAwareHttpClientProvider.getHttpClient(connectionId).createWebSocketConnection(WEBSOCKET_URL);
    }
  }

  private void closeConnection(String connectionId) {
    connectionIdsInterestedInNotifications.remove(connectionId);
    closeSocket();
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
