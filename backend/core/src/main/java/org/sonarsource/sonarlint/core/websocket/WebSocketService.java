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
package org.sonarsource.sonarlint.core.websocket;

import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.SonarCloudActiveEnvironment;
import org.sonarsource.sonarlint.core.SonarCloudRegion;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.util.FailSafeExecutors;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedWithBindingEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionCredentialsChangedEvent;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import static java.util.Objects.requireNonNull;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.SERVER_SENT_EVENTS;

public class WebSocketService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final boolean shouldEnableWebSockets;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final ConfigurationRepository configurationRepository;
  private final Map<SonarCloudRegion, WebSocketManager> webSocketsByRegion;
  private final ExecutorService executorService = FailSafeExecutors.newSingleThreadExecutor("sonarlint-websocket-subscriber");

  public WebSocketService(ConnectionConfigurationRepository connectionConfigurationRepository, ConfigurationRepository configurationRepository,
    ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider, InitializeParams params, SonarCloudActiveEnvironment sonarCloudActiveEnvironment,
    ApplicationEventPublisher eventPublisher) {
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.configurationRepository = configurationRepository;
    this.shouldEnableWebSockets = params.getBackendCapabilities().contains(SERVER_SENT_EVENTS);
    this.webSocketsByRegion = Map.of(
      SonarCloudRegion.US,
      new WebSocketManager(eventPublisher, connectionAwareHttpClientProvider, configurationRepository, sonarCloudActiveEnvironment.getWebSocketsEndpointUri(SonarCloudRegion.US)),
      SonarCloudRegion.EU,
      new WebSocketManager(eventPublisher, connectionAwareHttpClientProvider, configurationRepository, sonarCloudActiveEnvironment.getWebSocketsEndpointUri(SonarCloudRegion.EU))
    );
  }

  @EventListener
  public void handleEvent(BindingConfigChangedEvent bindingConfigChangedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    executorService.execute(() -> {
      considerScope(bindingConfigChangedEvent.configScopeId());
      // possible change of region for the binding; need to unsubscribe from the old region (subscription to the new one will be done in considerScope)
      if (didChangeRegion(bindingConfigChangedEvent.previousConfig(), bindingConfigChangedEvent.newConfig())) {
        // will only enter this block if previous connection (and connectionId) existed
        var previousRegion = ((SonarCloudConnectionConfiguration) connectionConfigurationRepository
          .getConnectionById(bindingConfigChangedEvent.previousConfig().connectionId())).getRegion();
        webSocketsByRegion.get(previousRegion).forget(bindingConfigChangedEvent.configScopeId());
        webSocketsByRegion.get(previousRegion).closeSocketIfNoMoreNeeded();
      }
    });
  }

  @EventListener
  public void handleEvent(ConfigurationScopesAddedWithBindingEvent configurationScopesAddedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    executorService.execute(() -> considerAllBoundConfigurationScopes(configurationScopesAddedEvent.getConfigScopeIds()));
  }

  @EventListener
  public void handleEvent(ConfigurationScopeRemovedEvent configurationScopeRemovedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    var removedConfigurationScopeId = configurationScopeRemovedEvent.getRemovedConfigurationScopeId();
    executorService.execute(() ->
      webSocketsByRegion.forEach((region, webSocketManager) -> {
        webSocketManager.forget(removedConfigurationScopeId);
        webSocketManager.closeSocketIfNoMoreNeeded();
      })
    );
  }

  @EventListener
  public void handleEvent(ConnectionConfigurationAddedEvent connectionConfigurationAddedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    // This is only to handle the case where binding was invalid (connection did not exist) and became valid (matching connection was created)
    executorService.execute(() -> considerConnection(connectionConfigurationAddedEvent.addedConnectionId()));
  }

  @EventListener
  public void handleEvent(ConnectionConfigurationUpdatedEvent connectionConfigurationUpdatedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    var updatedConnectionId = connectionConfigurationUpdatedEvent.updatedConnectionId();
    executorService.execute(() -> {
      if (didDisableNotifications(updatedConnectionId)) {
        webSocketsByRegion.forEach((region, webSocketManager) ->
          webSocketManager.forgetConnection(updatedConnectionId, "Notifications were disabled")
        );
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
    executorService.execute(() ->
      webSocketsByRegion.forEach((region, webSocketManager) ->
        webSocketManager.forgetConnection(removedConnectionId, "Connection was removed")
      )
    );
  }

  @EventListener
  public void handleEvent(ConnectionCredentialsChangedEvent connectionCredentialsChangedEvent) {
    if (!shouldEnableWebSockets) {
      return;
    }
    var connectionId = connectionCredentialsChangedEvent.getConnectionId();
    executorService.execute(() -> {
      if (isEligibleConnection(connectionId) && isInterestedInNotifications(connectionId)) {
        var region = ((SonarCloudConnectionConfiguration) connectionConfigurationRepository.getConnectionById(connectionId)).getRegion();
        webSocketsByRegion.get(region).reopenConnection(connectionId, "Credentials have changed");
      }
    });
  }

  private void considerConnection(String connectionId) {
    var configScopeIds = configurationRepository.getBoundScopesToConnection(connectionId)
      .stream().map(BoundScope::getConfigScopeId)
      .collect(Collectors.toSet());
    considerAllBoundConfigurationScopes(configScopeIds);
  }

  private void considerAllBoundConfigurationScopes(Set<String> configScopeIds) {
    for (String scopeId : configScopeIds) {
      considerScope(scopeId);
    }
  }

  private void considerScope(String scopeId) {
    var binding = getCurrentBinding(scopeId);
    if (binding != null && isEligibleConnection(binding.connectionId())) {
      var connection = requireNonNull(connectionConfigurationRepository.getConnectionById(binding.connectionId()));
      var region = ((SonarCloudConnectionConfiguration) connection).getRegion();
      webSocketsByRegion.get(region).subscribe(scopeId, binding);
    } else if (isSubscribedToAProject(scopeId)) {
      // no binding or binding is not eligible, unsubscribe from all regions if it was subscribed to a project
      webSocketsByRegion.forEach((region, webSocketManager) -> {
        webSocketManager.forget(scopeId);
        webSocketManager.closeSocketIfNoMoreNeeded();
      });
    }
  }

  private boolean isInterestedInNotifications(String connectionId) {
    return webSocketsByRegion.values().stream().anyMatch(webSocketManager -> webSocketManager.isInterestedInNotifications(connectionId));
  }

  private boolean isEligibleConnection(String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    return connection != null && connection.getKind().equals(ConnectionKind.SONARCLOUD) && !connection.isDisableNotifications();
  }

  private boolean didChangeRegion(BindingConfiguration previousBindingConfiguration, BindingConfiguration newBindingConfiguration) {
    var previousConnectionId = previousBindingConfiguration.connectionId();
    var previousConnection = previousConnectionId != null ? connectionConfigurationRepository.getConnectionById(previousConnectionId) : null;
    var newConnectionId = newBindingConfiguration.connectionId();
    var newConnection = newConnectionId != null ? connectionConfigurationRepository.getConnectionById(newConnectionId) : null;
    if (newConnection == null || previousConnection == null) {
      // nothing to do
      return false;
    } else if (previousConnection instanceof SonarCloudConnectionConfiguration previousConn &&
      newConnection instanceof SonarCloudConnectionConfiguration newConn) {
      // was SonarCloud connection and still is - check if region changed
      return previousConn.getRegion() != newConn.getRegion();
    }
    return false;
  }

  @CheckForNull
  private Binding getCurrentBinding(String configScopeId) {
    var bindingConfiguration = configurationRepository.getBindingConfiguration(configScopeId);
    if (bindingConfiguration != null && bindingConfiguration.isBound()) {
      return new Binding(requireNonNull(bindingConfiguration.connectionId()), requireNonNull(bindingConfiguration.sonarProjectKey()));
    }
    return null;
  }

  private boolean didDisableNotifications(String connectionId) {
    if (isInterestedInNotifications(connectionId)) {
      var connection = connectionConfigurationRepository.getConnectionById(connectionId);
      return connection != null && connection.getKind().equals(ConnectionKind.SONARCLOUD) && connection.isDisableNotifications();
    }
    return false;
  }

  private boolean didEnableNotifications(String connectionId) {
    return isEligibleConnection(connectionId) && !isInterestedInNotifications(connectionId);
  }

  private boolean isSubscribedToAProject(String configScopeId) {
    for (var webSocketManager : webSocketsByRegion.values()) {
      var subscribedProjectKey = webSocketManager.getSubscribedProjectKeysByConfigScopes().get(configScopeId);
      if (subscribedProjectKey != null) {
        // we are interested if it was subscribed to a project in any region
        return true;
      }
    }
    return false;
  }

  public boolean hasOpenConnection(SonarCloudRegion region) {
    return webSocketsByRegion.get(region).hasOpenConnection();
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop websockets subscriber service in a timely manner");
    }
    webSocketsByRegion.forEach((region, webSocketManager) -> {
      webSocketManager.closeSocket("Backend is shutting down");
      webSocketManager.getSubscribedProjectKeysByConfigScopes().clear();
      webSocketManager.getConnectionIdsInterestedInNotifications().clear();
    });
  }
}
