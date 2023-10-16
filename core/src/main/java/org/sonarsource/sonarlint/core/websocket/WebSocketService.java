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
package org.sonarsource.sonarlint.core.websocket;

import com.google.common.eventbus.Subscribe;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
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
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotClosedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotRaisedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityClosedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.serverconnection.StorageFacade;
import org.sonarsource.sonarlint.core.serverconnection.events.EventDispatcher;
import org.sonarsource.sonarlint.core.serverconnection.events.hotspot.UpdateStorageOnSecurityHotspotChanged;
import org.sonarsource.sonarlint.core.serverconnection.events.hotspot.UpdateStorageOnSecurityHotspotClosed;
import org.sonarsource.sonarlint.core.serverconnection.events.hotspot.UpdateStorageOnSecurityHotspotRaised;
import org.sonarsource.sonarlint.core.serverconnection.events.issue.UpdateStorageOnIssueChanged;
import org.sonarsource.sonarlint.core.serverconnection.events.taint.UpdateStorageOnTaintVulnerabilityClosed;
import org.sonarsource.sonarlint.core.serverconnection.events.taint.UpdateStorageOnTaintVulnerabilityRaised;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.websocket.events.SmartNotificationEvent;

import static java.util.Objects.requireNonNull;

public class WebSocketService {
  private final Map<String, String> subscribedProjectKeysByConfigScopes = new HashMap<>();
  private final Set<String> connectionIdsInterestedInNotifications = new HashSet<>();
  private final boolean shouldEnableWebSockets;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final ConfigurationRepository configurationRepository;
  private final ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider;
  private final Map<String, EventDispatcher> eventRouterByConnectionId;
  private final StorageFacade storageFacade;
  private final SonarLintRpcClient client;
  private final TelemetryService telemetryService;
  protected SonarCloudWebSocket sonarCloudWebSocket;
  private String connectionIdUsedToCreateConnection;

  public WebSocketService(SonarLintRpcClient client, ConnectionConfigurationRepository connectionConfigurationRepository, ConfigurationRepository configurationRepository,
    ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider, TelemetryService telemetryService, StorageService storageService, InitializeParams params) {
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.configurationRepository = configurationRepository;
    this.connectionAwareHttpClientProvider = connectionAwareHttpClientProvider;
    this.shouldEnableWebSockets = params.getFeatureFlags().shouldManageServerSentEvents();
    this.storageFacade = storageService.getStorageFacade();
    this.eventRouterByConnectionId = new HashMap<>();
    this.client = client;
    this.telemetryService = telemetryService;
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
    this.eventRouterByConnectionId.remove(connectionId);
    var previouslyInterestedInNotifications = connectionIdsInterestedInNotifications.remove(connectionId);
    if (!previouslyInterestedInNotifications) {
      return;
    }
    if (connectionIdsInterestedInNotifications.isEmpty()) {
      closeSocket();
      subscribedProjectKeysByConfigScopes.clear();
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

  private void handleEventDispatcher(String connectionId) {
    var storage = storageFacade.connection(connectionId);
    this.eventRouterByConnectionId.putIfAbsent(connectionId,
      new EventDispatcher()
        .dispatch(SmartNotificationEvent.class, new ShowSmartNotificationOnSmartNotificationEvent(client, configurationRepository, telemetryService))
        .dispatch(IssueChangedEvent.class, new UpdateStorageOnIssueChanged(storage))
        .dispatch(SecurityHotspotClosedEvent.class, new UpdateStorageOnSecurityHotspotClosed(storage))
        .dispatch(SecurityHotspotRaisedEvent.class, new UpdateStorageOnSecurityHotspotRaised(storage))
        .dispatch(SecurityHotspotChangedEvent.class, new UpdateStorageOnSecurityHotspotChanged(storage))
        .dispatch(TaintVulnerabilityClosedEvent.class, new UpdateStorageOnTaintVulnerabilityClosed(storage))
        .dispatch(TaintVulnerabilityRaisedEvent.class, new UpdateStorageOnTaintVulnerabilityRaised(storage)));
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
    if (projectKey != null && !subscribedProjectKeysByConfigScopes.containsValue(projectKey) && sonarCloudWebSocket != null) {
      sonarCloudWebSocket.unsubscribe(projectKey);
    }
  }

  private void resubscribeAll() {
    subscribedProjectKeysByConfigScopes.forEach((configScope, projectKey) -> sonarCloudWebSocket.subscribe(projectKey));
  }

  private void createConnectionIfNeeded(String connectionId) {
    connectionIdsInterestedInNotifications.add(connectionId);
    handleEventDispatcher(connectionId);
    if (this.sonarCloudWebSocket == null) {
      this.sonarCloudWebSocket = SonarCloudWebSocket.create(connectionAwareHttpClientProvider.getWebSocketClient(connectionId),
        event -> {
          var eventRouter = this.eventRouterByConnectionId.get(connectionId);
          if (eventRouter != null) {
            eventRouter.handle(event);
          }
        },
        this::reopenConnectionOnClose);
      this.connectionIdUsedToCreateConnection = connectionId;
    }
  }

  private void closeSocket() {
    if (this.sonarCloudWebSocket != null) {
      var socket = this.sonarCloudWebSocket;
      this.sonarCloudWebSocket = null;
      this.connectionIdUsedToCreateConnection = null;
      socket.close();
    }
  }

  public boolean hasOpenConnection() {
    return sonarCloudWebSocket != null && sonarCloudWebSocket.isOpen();
  }

  @PreDestroy
  public void shutdown() {
    connectionIdsInterestedInNotifications.clear();
    subscribedProjectKeysByConfigScopes.clear();
    closeSocket();
  }
}
