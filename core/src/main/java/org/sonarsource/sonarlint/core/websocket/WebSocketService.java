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
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionCredentialsChangedEvent;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverconnection.events.EventDispatcher;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;
import org.sonarsource.sonarlint.core.websocket.events.QualityGateChangedEvent;

import static java.util.Objects.requireNonNull;

public class WebSocketService {
  private final Map<String, String> subscribedProjectKeysByConfigScopes = new HashMap<>();
  private final Set<String> connectionIdsInterestedInNotifications = new HashSet<>();
  private final boolean shouldEnableWebSockets;
  private String connectionIdUsedToCreateConnection;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final ConfigurationRepository configurationRepository;
  private final ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider;
  protected SonarCloudWebSocket sonarCloudWebSocket;
  private final EventDispatcher eventRouter;

  public WebSocketService(SonarLintClient client, ConnectionConfigurationRepository connectionConfigurationRepository, ConfigurationRepository configurationRepository,
    ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider, TelemetryServiceImpl telemetryService, InitializeParams params) {
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.configurationRepository = configurationRepository;
    this.connectionAwareHttpClientProvider = connectionAwareHttpClientProvider;
    this.shouldEnableWebSockets = params.getFeatureFlags().shouldManageSmartNotifications();
    this.eventRouter = new EventDispatcher()
      .dispatch(QualityGateChangedEvent.class, new ShowSmartNotificationOnQualityGateChangedEvent(client, configurationRepository, telemetryService));
  }

  protected void reopenConnectionOnClose() {
    var connectionId = connectionIdsInterestedInNotifications.stream().findFirst().orElse(null);
    if (this.sonarCloudWebSocket != null && connectionId != null) {
      // If connection already exists, close it and create new one before it expires on its own
      reopenConnection(connectionId);
    }
  }

  @Subscribe
  public void handleEvent(BindingConfigChangedEvent bindingConfigChangedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    considerScope(bindingConfigChangedEvent.getConfigScopeId());
  }

  @Subscribe
  public void handleEvent(ConfigurationScopesAddedEvent configurationScopesAddedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    considerAllBoundConfigurationScopes(configurationScopesAddedEvent.getAddedConfigurationScopeIds());
  }

  @Subscribe
  public void handleEvent(ConfigurationScopeRemovedEvent configurationScopeRemovedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    var removedConfigurationScopeId = configurationScopeRemovedEvent.getRemovedConfigurationScopeId();
    forget(removedConfigurationScopeId);
    closeSocketIfNoMoreNeeded();
  }

  @Subscribe
  public void handleEvent(ConnectionConfigurationAddedEvent connectionConfigurationAddedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    // This is only to handle the case where binding was invalid (connection did not exist) and became valid (matching connection was created)
    considerConnection(connectionConfigurationAddedEvent.getAddedConnectionId());
  }

  @Subscribe
  public void handleEvent(ConnectionConfigurationUpdatedEvent connectionConfigurationUpdatedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    var updatedConnectionId = connectionConfigurationUpdatedEvent.getUpdatedConnectionId();
    if (didDisableNotifications(updatedConnectionId)) {
      forgetConnection(updatedConnectionId);
    } else if (didEnableNotifications(updatedConnectionId)) {
      considerConnection(updatedConnectionId);
    }
  }

  @Subscribe
  public void handleEvent(ConnectionConfigurationRemovedEvent connectionConfigurationRemovedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    String removedConnectionId = connectionConfigurationRemovedEvent.getRemovedConnectionId();
    forgetConnection(removedConnectionId);
  }

  @Subscribe
  public void handleEvent(ConnectionCredentialsChangedEvent connectionCredentialsChangedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    var connectionId = connectionCredentialsChangedEvent.getConnectionId();
    if (isEligibleConnection(connectionId) && connectionIdsInterestedInNotifications.contains(connectionId)) {
      reopenConnection(connectionId);
    }
  }

  private boolean isEligibleConnection(String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    return connection != null && connection.getKind().equals(ConnectionKind.SONARCLOUD) && !connection.isDisableNotifications();
  }

  private String getBoundProjectKey(String configScopeId) {
    var bindingConfiguration = configurationRepository.getBindingConfiguration(configScopeId);
    if (bindingConfiguration != null && bindingConfiguration.isBound()) {
      return bindingConfiguration.getSonarProjectKey();
    }
    return null;
  }

  private Binding getCurrentBinding(String configScopeId) {
    var bindingConfiguration = configurationRepository.getBindingConfiguration(configScopeId);
    if (bindingConfiguration != null && bindingConfiguration.isBound()) {
      return new Binding(requireNonNull(bindingConfiguration.getConnectionId()), requireNonNull(bindingConfiguration.getSonarProjectKey()));
    }
    return null;
  }

  private void closeSocketIfNoMoreNeeded() {
    if (subscribedProjectKeysByConfigScopes.isEmpty()) {
      closeSocket();
    }
  }

  private boolean didDisableNotifications(String connectionId) {
    if (connectionIdsInterestedInNotifications.contains(connectionId)) {
      var connection = connectionConfigurationRepository.getConnectionById(connectionId);
      return connection != null && connection.getKind().equals(ConnectionKind.SONARCLOUD) && connection.isDisableNotifications();
    }
    return false;
  }

  private boolean didEnableNotifications(String connectionId) {
    return !connectionIdsInterestedInNotifications.contains(connectionId) && isEligibleConnection(connectionId);
  }

  private void considerConnection(String connectionId) {
    var configScopeIds = configurationRepository.getConfigScopesWithBindingConfiguredTo(connectionId)
      .stream().map(ConfigurationScope::getId)
      .collect(Collectors.toSet());
    considerAllBoundConfigurationScopes(configScopeIds);
  }

  private void forgetConnection(String connectionId) {
    var previouslyInterestedInNotifications = connectionIdsInterestedInNotifications.remove(connectionId);
    if (!previouslyInterestedInNotifications) {
      return;
    }
    if (connectionIdsInterestedInNotifications.isEmpty()) {
      closeSocket();
    } else if (connectionIdUsedToCreateConnection.equals(connectionId)) {
      // stop using the credentials, switch to another connection
      var otherConnectionId = connectionIdsInterestedInNotifications.stream().findAny().orElseThrow();
      removeProjectsFromSubscriptionListForConnection(connectionId);
      reopenConnection(otherConnectionId);
    } else {
      configurationRepository.getConfigScopesWithBindingConfiguredTo(connectionId)
        .forEach(configScope -> forget(configScope.getId()));
    }
  }

  private void reopenConnection(String connectionId) {
    closeSocket();
    createConnectionIfNeeded(connectionId);
    resubscribeAll();
  }

  private void considerAllBoundConfigurationScopes(Set<String> configScopeIds) {
    for (String scopeId : configScopeIds) {
      considerScope(scopeId);
    }
  }

  private void considerScope(String scopeId) {
    var binding = getCurrentBinding(scopeId);
    if (binding != null && isEligibleConnection(binding.getConnectionId())) {
      subscribe(scopeId, binding);
    } else if (isSubscribedWithProjectKeyDifferentThanCurrentBinding(scopeId)) {
      forget(scopeId);
      closeSocketIfNoMoreNeeded();
    }
  }

  private boolean isSubscribedWithProjectKeyDifferentThanCurrentBinding(String configScopeId) {
    var previousSubscribedProjectKeyForScope = subscribedProjectKeysByConfigScopes.get(configScopeId);
    return previousSubscribedProjectKeyForScope != null && !previousSubscribedProjectKeyForScope.equals(getBoundProjectKey(configScopeId));
  }

  private void removeProjectsFromSubscriptionListForConnection(String updatedConnectionId) {
    var configurationScopesToUnsubscribe = configurationRepository.getConfigScopesWithBindingConfiguredTo(updatedConnectionId);
    for (var configScope : configurationScopesToUnsubscribe) {
      subscribedProjectKeysByConfigScopes.remove(configScope.getId());
    }
  }

  private void subscribe(String configScopeId, Binding binding) {
    createConnectionIfNeeded(binding.getConnectionId());
    var projectKey = binding.getSonarProjectKey();
    if (subscribedProjectKeysByConfigScopes.containsKey(configScopeId) && !subscribedProjectKeysByConfigScopes.get(configScopeId).equals(projectKey)) {
      forget(configScopeId);
    }
    if (!subscribedProjectKeysByConfigScopes.containsValue(projectKey)) {
      sonarCloudWebSocket.subscribe(projectKey);
    }
    subscribedProjectKeysByConfigScopes.put(configScopeId, projectKey);
  }

  private void forget(String configScopeId) {
    var projectKey = subscribedProjectKeysByConfigScopes.remove(configScopeId);
    if (projectKey != null && !subscribedProjectKeysByConfigScopes.containsValue(projectKey)) {
      sonarCloudWebSocket.unsubscribe(projectKey);
    }
  }

  private void resubscribeAll() {
    subscribedProjectKeysByConfigScopes.forEach((configScope, projectKey) -> sonarCloudWebSocket.subscribe(projectKey));
  }

  private void createConnectionIfNeeded(String connectionId) {
    connectionIdsInterestedInNotifications.add(connectionId);
    if (this.sonarCloudWebSocket == null) {
      this.sonarCloudWebSocket = SonarCloudWebSocket.create(connectionAwareHttpClientProvider.getHttpClient(connectionId), eventRouter::handle, this::reopenConnectionOnClose);
      this.connectionIdUsedToCreateConnection = connectionId;
    }
  }

  private void closeSocket() {
    if (this.sonarCloudWebSocket != null) {
      this.sonarCloudWebSocket.close();
      this.sonarCloudWebSocket = null;
      this.connectionIdUsedToCreateConnection = null;
    }
  }

  public boolean hasOpenConnection() {
    return sonarCloudWebSocket != null && sonarCloudWebSocket.isOpen();
  }

  @PreDestroy
  public void shutdown() {
    closeSocket();
    connectionIdsInterestedInNotifications.clear();
    subscribedProjectKeysByConfigScopes.clear();
  }
}
