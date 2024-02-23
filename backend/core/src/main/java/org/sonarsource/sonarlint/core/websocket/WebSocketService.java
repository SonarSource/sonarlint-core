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

import com.google.common.util.concurrent.MoreExecutors;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.PreDestroy;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.push.SonarServerEvent;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionCredentialsChangedEvent;
import org.sonarsource.sonarlint.core.event.SonarServerEventReceivedEvent;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import static java.util.Objects.requireNonNull;

public class WebSocketService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final Map<String, String> subscribedProjectKeysByConfigScopes = new HashMap<>();
  private final Set<String> connectionIdsInterestedInNotifications = new HashSet<>();
  private final boolean shouldEnableWebSockets;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final ConfigurationRepository configurationRepository;
  private final ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider;
  private final ApplicationEventPublisher eventPublisher;
  protected SonarCloudWebSocket sonarCloudWebSocket;
  private String connectionIdUsedToCreateConnection;
  private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "sonarlint-websocket-subscriber"));

  public WebSocketService(ConnectionConfigurationRepository connectionConfigurationRepository, ConfigurationRepository configurationRepository,
    ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider, InitializeParams params,
    ApplicationEventPublisher eventPublisher) {
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.configurationRepository = configurationRepository;
    this.connectionAwareHttpClientProvider = connectionAwareHttpClientProvider;
    this.shouldEnableWebSockets = params.getFeatureFlags().shouldManageServerSentEvents();
    this.eventPublisher = eventPublisher;
  }

  protected void reopenConnectionOnClose() {
    executorService.submit(() -> {
      var connectionId = connectionIdsInterestedInNotifications.stream().findFirst().orElse(null);
      if (this.sonarCloudWebSocket != null && connectionId != null) {
        // If connection already exists, close it and create new one before it expires on its own
        reopenConnection(connectionId, "WebSocket was closed by server or reached EOL");
      }
    });
  }

  @EventListener
  public void handleEvent(BindingConfigChangedEvent bindingConfigChangedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    executorService.submit(() -> considerScope(bindingConfigChangedEvent.getConfigScopeId()));
  }

  @EventListener
  public void handleEvent(ConfigurationScopesAddedEvent configurationScopesAddedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    executorService.submit(() -> considerAllBoundConfigurationScopes(configurationScopesAddedEvent.getAddedConfigurationScopeIds()));
  }

  @EventListener
  public void handleEvent(ConfigurationScopeRemovedEvent configurationScopeRemovedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    var removedConfigurationScopeId = configurationScopeRemovedEvent.getRemovedConfigurationScopeId();
    executorService.submit(() -> {
      forget(removedConfigurationScopeId);
      closeSocketIfNoMoreNeeded();
    });
  }

  @EventListener
  public void handleEvent(ConnectionConfigurationAddedEvent connectionConfigurationAddedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    // This is only to handle the case where binding was invalid (connection did not exist) and became valid (matching connection was created)
    executorService.submit(() -> considerConnection(connectionConfigurationAddedEvent.getAddedConnectionId()));
  }

  @EventListener
  public void handleEvent(ConnectionConfigurationUpdatedEvent connectionConfigurationUpdatedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    var updatedConnectionId = connectionConfigurationUpdatedEvent.getUpdatedConnectionId();
    executorService.submit(() -> {
      if (didDisableNotifications(updatedConnectionId)) {
        forgetConnection(updatedConnectionId, "Notifications were disabled");
      } else if (didEnableNotifications(updatedConnectionId)) {
        considerConnection(updatedConnectionId);
      }
    });
  }

  @EventListener
  public void handleEvent(ConnectionConfigurationRemovedEvent connectionConfigurationRemovedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    String removedConnectionId = connectionConfigurationRemovedEvent.getRemovedConnectionId();
    executorService.submit(() -> forgetConnection(removedConnectionId, "Connection was removed"));
  }

  @EventListener
  public void handleEvent(ConnectionCredentialsChangedEvent connectionCredentialsChangedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    var connectionId = connectionCredentialsChangedEvent.getConnectionId();
    executorService.submit(() -> {
      if (isEligibleConnection(connectionId) && connectionIdsInterestedInNotifications.contains(connectionId)) {
        reopenConnection(connectionId, "Credentials have changed");
      }
    });
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

  @CheckForNull
  private Binding getCurrentBinding(String configScopeId) {
    var bindingConfiguration = configurationRepository.getBindingConfiguration(configScopeId);
    if (bindingConfiguration != null && bindingConfiguration.isBound()) {
      return new Binding(requireNonNull(bindingConfiguration.getConnectionId()), requireNonNull(bindingConfiguration.getSonarProjectKey()));
    }
    return null;
  }

  private void closeSocketIfNoMoreNeeded() {
    if (subscribedProjectKeysByConfigScopes.isEmpty()) {
      closeSocket("No more bound project");
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
    var configScopeIds = configurationRepository.getBoundScopesToConnection(connectionId)
      .stream().map(BoundScope::getConfigScopeId)
      .collect(Collectors.toSet());
    considerAllBoundConfigurationScopes(configScopeIds);
  }

  private void forgetConnection(String connectionId, String reason) {
    var previouslyInterestedInNotifications = connectionIdsInterestedInNotifications.remove(connectionId);
    if (!previouslyInterestedInNotifications) {
      return;
    }
    if (connectionIdsInterestedInNotifications.isEmpty()) {
      closeSocket(reason);
      subscribedProjectKeysByConfigScopes.clear();
    } else if (connectionIdUsedToCreateConnection.equals(connectionId)) {
      // stop using the credentials, switch to another connection
      var otherConnectionId = connectionIdsInterestedInNotifications.stream().findAny().orElseThrow();
      removeProjectsFromSubscriptionListForConnection(connectionId);
      reopenConnection(otherConnectionId, reason + ", reopening for other SC connection");
    } else {
      configurationRepository.getBoundScopesToConnection(connectionId)
        .forEach(configScope -> forget(configScope.getConfigScopeId()));
    }
  }

  private void reopenConnection(String connectionId, String reason) {
    closeSocket(reason);
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
    var configurationScopesToUnsubscribe = configurationRepository.getBoundScopesToConnection(updatedConnectionId);
    for (var configScope : configurationScopesToUnsubscribe) {
      subscribedProjectKeysByConfigScopes.remove(configScope.getConfigScopeId());
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
    if (projectKey != null && !subscribedProjectKeysByConfigScopes.containsValue(projectKey) && sonarCloudWebSocket != null) {
      sonarCloudWebSocket.unsubscribe(projectKey);
    }
  }

  private void resubscribeAll() {
    var uniqueProjectKeys = new HashSet<>(subscribedProjectKeysByConfigScopes.values());
    uniqueProjectKeys.forEach(projectKey -> sonarCloudWebSocket.subscribe(projectKey));
  }

  private void createConnectionIfNeeded(String connectionId) {
    connectionIdsInterestedInNotifications.add(connectionId);
    if (this.sonarCloudWebSocket == null) {
      this.sonarCloudWebSocket = SonarCloudWebSocket.create(connectionAwareHttpClientProvider.getWebSocketClient(connectionId),
        this::handleSonarServerEvent, this::reopenConnectionOnClose);
      this.connectionIdUsedToCreateConnection = connectionId;
    }
  }

  private void handleSonarServerEvent(SonarServerEvent event) {
    connectionIdsInterestedInNotifications.forEach(id -> eventPublisher.publishEvent(new SonarServerEventReceivedEvent(id, event)));
  }

  private void closeSocket(String reason) {
    if (this.sonarCloudWebSocket != null) {
      var socket = this.sonarCloudWebSocket;
      this.sonarCloudWebSocket = null;
      this.connectionIdUsedToCreateConnection = null;
      socket.close(reason);
    }
  }

  public boolean hasOpenConnection() {
    return sonarCloudWebSocket != null && sonarCloudWebSocket.isOpen();
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop websockets subscriber service in a timely manner");
    }
    connectionIdsInterestedInNotifications.clear();
    subscribedProjectKeysByConfigScopes.clear();
    closeSocket("Backend is shutting down");
  }
}
