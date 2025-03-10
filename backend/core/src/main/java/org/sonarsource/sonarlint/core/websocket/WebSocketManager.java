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
package org.sonarsource.sonarlint.core.websocket;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.sonarsource.sonarlint.core.SonarCloudActiveEnvironment;
import org.sonarsource.sonarlint.core.SonarCloudRegion;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.util.FailSafeExecutors;
import org.sonarsource.sonarlint.core.event.SonarServerEventReceivedEvent;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.push.SonarServerEvent;
import org.springframework.context.ApplicationEventPublisher;

public class WebSocketManager {
  private final SonarCloudRegion region;
  private SonarCloudWebSocket sonarCloudWebSocket;
  private final Set<String> connectionIdsInterestedInNotifications = new HashSet<>();
  private String connectionIdUsedToCreateConnection;
  private final Map<String, String> subscribedProjectKeysByConfigScopes = new HashMap<>();
  private final ExecutorService executorService = FailSafeExecutors.newSingleThreadExecutor("sonarlint-websocket-subscriber");
  private final ApplicationEventPublisher eventPublisher;
  private final SonarCloudActiveEnvironment sonarCloudActiveEnvironment;
  private final ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider;
  private final ConfigurationRepository configurationRepository;
  public WebSocketManager(SonarCloudRegion region, ApplicationEventPublisher eventPublisher, SonarCloudActiveEnvironment sonarCloudActiveEnvironment,
   ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider, ConfigurationRepository configurationRepository) {
    this.region = region;
    this.eventPublisher = eventPublisher;
    this.sonarCloudActiveEnvironment = sonarCloudActiveEnvironment;
    this.connectionAwareHttpClientProvider = connectionAwareHttpClientProvider;
    this.configurationRepository = configurationRepository;
  }

  private void handleSonarServerEvent(SonarServerEvent event) {
    connectionIdsInterestedInNotifications.forEach(id -> eventPublisher.publishEvent(new SonarServerEventReceivedEvent(id, event)));
  }

  public void forgetConnection(String connectionId, String reason) {
    var previouslyInterestedInNotifications = connectionIdsInterestedInNotifications.remove(connectionId);
    if (!previouslyInterestedInNotifications) {
      return;
    }
    if (connectionIdsInterestedInNotifications.isEmpty()) {
      closeSocket(reason);
      subscribedProjectKeysByConfigScopes.clear();
    } else if (this.connectionIdUsedToCreateConnection.equals(connectionId)) {
      // stop using the credentials, switch to another connection
      var otherConnectionId = connectionIdsInterestedInNotifications.stream().findAny().orElseThrow();
      removeProjectsFromSubscriptionListForConnection(connectionId);
      this.reopenConnection(otherConnectionId, reason + ", reopening for other SC connection");
    } else {
      configurationRepository.getBoundScopesToConnection(connectionId)
      .forEach(configScope -> forget(configScope.getConfigScopeId()));
    }
  }

  private void removeProjectsFromSubscriptionListForConnection(String updatedConnectionId) {
    var configurationScopesToUnsubscribe = configurationRepository.getBoundScopesToConnection(updatedConnectionId);
    for (var configScope : configurationScopesToUnsubscribe) {
      subscribedProjectKeysByConfigScopes.remove(configScope.getConfigScopeId());
    }
  }

  public void createConnectionIfNeeded(String connectionId) {
    connectionIdsInterestedInNotifications.add(connectionId);
    if (!hasOpenConnection()) {
      this.sonarCloudWebSocket = SonarCloudWebSocket.create(sonarCloudActiveEnvironment.getWebSocketsEndpointUri(this.region),
        connectionAwareHttpClientProvider.getWebSocketClient(connectionId),
        this::handleSonarServerEvent, this::reopenConnectionOnClose);
      this.connectionIdUsedToCreateConnection = connectionId;
    }
  }

  public void reopenConnection(String connectionId, String reason) {
    closeSocket(reason);
    createConnectionIfNeeded(connectionId);
    resubscribeAll();
  }

  protected void reopenConnectionOnClose() {
    executorService.execute(() -> {
      var connectionId = connectionIdsInterestedInNotifications.stream().findFirst().orElse(null);
      if (this.sonarCloudWebSocket != null && connectionId != null) {
        // If connection already exists, close it and create new one before it expires on its own
        this.reopenConnection(connectionId, "WebSocket was closed by server or reached EOL");
      }
    });
  }

  public void closeSocketIfNoMoreNeeded() {
    if (subscribedProjectKeysByConfigScopes.isEmpty()) {
      closeSocket("No more bound project");
    }
  }

  public void subscribe(String configScopeId, Binding binding) {
    this.createConnectionIfNeeded(binding.connectionId());
    var projectKey = binding.sonarProjectKey();
    if (subscribedProjectKeysByConfigScopes.containsKey(configScopeId) && !subscribedProjectKeysByConfigScopes.get(configScopeId).equals(projectKey)) {
      this.forget(configScopeId);
    }
    if (!subscribedProjectKeysByConfigScopes.containsValue(projectKey)) {
      this.sonarCloudWebSocket.subscribe(projectKey);
    }
    subscribedProjectKeysByConfigScopes.put(configScopeId, projectKey);
  }

  private void resubscribeAll() {
    var uniqueProjectKeys = new HashSet<>(subscribedProjectKeysByConfigScopes.values());
    uniqueProjectKeys.forEach(projectKey -> sonarCloudWebSocket.subscribe(projectKey));
  }

  public void closeSocket(String reason) {
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

  public void forget(String configScopeId) {
    var projectKey = subscribedProjectKeysByConfigScopes.remove(configScopeId);
    if (projectKey != null && !subscribedProjectKeysByConfigScopes.containsValue(projectKey) && sonarCloudWebSocket != null) {
      sonarCloudWebSocket.unsubscribe(projectKey);
    }
  }

  public SonarCloudRegion getRegion() {
    return region;
  }

  public SonarCloudWebSocket getSonarCloudWebSocket() {
    return sonarCloudWebSocket;
  }

  public Map<String, String> getSubscribedProjectKeysByConfigScopes() {
    return subscribedProjectKeysByConfigScopes;
  }

  public boolean isInterestedInNotifications(String connectionId) {
    return connectionIdsInterestedInNotifications.contains(connectionId);
  }

  public Set<String> getConnectionIdsInterestedInNotifications() {
    return connectionIdsInterestedInNotifications;
  }
}
