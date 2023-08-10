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

import java.net.http.WebSocket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.websocket.WebSocketService.WEBSOCKET_DEV_URL;

class WebSocketServiceTest {
  private static ConnectionConfigurationRepository connectionConfigurationRepository;
  private static ConfigurationRepository configurationRepository;
  private static ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider;
  private static HttpClient httpClient;
  private static WebSocket webSocket;
  private static WebSocketService webSocketService;

  @BeforeEach
  public void setup() {
    connectionConfigurationRepository = mock(ConnectionConfigurationRepository.class);
    configurationRepository = mock(ConfigurationRepository.class);
    connectionAwareHttpClientProvider = mock(ConnectionAwareHttpClientProvider.class);
    httpClient = mock(HttpClient.class);
    webSocket = mock(WebSocket.class);
    webSocketService = new WebSocketService(connectionConfigurationRepository, configurationRepository, connectionAwareHttpClientProvider);
  }

  @Nested
  class HandleBindingConfigChangeEvent {
    @Test
    void bindingUpdateShouldCreateConnectionAndSubscribeToEvents() {
      var previousConfig = new BindingConfigChangedEvent.BindingConfig("configScope", "connectionId1", "projectKey", false);
      var newConfig = new BindingConfigChangedEvent.BindingConfig("configScope", "connectionId2", "projectKey", false);
      var bindingConfigChangedEvent = new BindingConfigChangedEvent(previousConfig, newConfig);
      var connection = new SonarCloudConnectionConfiguration("connectionId1", "myOrg", false);

      when(connectionConfigurationRepository.getConnectionById(newConfig.getConnectionId())).thenReturn(connection);
      when(connectionAwareHttpClientProvider.getHttpClient(newConfig.getConnectionId())).thenReturn(httpClient);
      when(httpClient.createWebSocketConnection(WEBSOCKET_DEV_URL)).thenReturn(webSocket);

      webSocketService.handleEvent(bindingConfigChangedEvent);

      verify(httpClient).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey\"}", true);
    }

    @Test
    void bindingUpdateShouldNotDoAnythingIfBindingRemoved() {
      var previousConfig = new BindingConfigChangedEvent.BindingConfig("configScope", "connectionId1", "projectKey", false);
      var newConfig = new BindingConfigChangedEvent.BindingConfig("configScope", null, null, false);
      var bindingConfigChangedEvent = new BindingConfigChangedEvent(previousConfig, newConfig);

      when(connectionConfigurationRepository.getConnectionById(newConfig.getConnectionId())).thenReturn(null);
      when(connectionAwareHttpClientProvider.getHttpClient(newConfig.getConnectionId())).thenReturn(httpClient);

      webSocketService.handleEvent(bindingConfigChangedEvent);

      verify(httpClient, times(0)).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket, times(0)).sendText(any(String.class), any(Boolean.class));
    }

    @Test
    void bindingUpdateShouldNotDoAnythingIfSonarQubeConnection() {
      var previousConfig = new BindingConfigChangedEvent.BindingConfig("configScope", "connectionId1", "projectKey", false);
      var newConfig = new BindingConfigChangedEvent.BindingConfig("configScope", "connectionId2", "projectKey", false);
      var bindingConfigChangedEvent = new BindingConfigChangedEvent(previousConfig, newConfig);
      var connection = new SonarQubeConnectionConfiguration("connectionId1", "http://localhost:9000", false);

      when(connectionConfigurationRepository.getConnectionById(newConfig.getConnectionId())).thenReturn(connection);
      when(connectionAwareHttpClientProvider.getHttpClient(newConfig.getConnectionId())).thenReturn(httpClient);
      when(httpClient.createWebSocketConnection(WEBSOCKET_DEV_URL)).thenReturn(webSocket);

      webSocketService.handleEvent(bindingConfigChangedEvent);

      verify(httpClient, times(0)).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket, times(0)).sendText(any(String.class), any(Boolean.class));
    }

    @Test
    void bindingUpdateShouldUnsubscribeFromOldProjectAndSubscribeToNewProject() {
      // there was already subscription for the project
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope", "projectKey");
      webSocketService.ws = webSocket;

      var previousConfig = new BindingConfigChangedEvent.BindingConfig("configScope", "connectionId", "projectKey", false);
      var newConfig = new BindingConfigChangedEvent.BindingConfig("configScope", "connectionId", "projectKey2", false);
      var bindingConfigChangedEvent = new BindingConfigChangedEvent(previousConfig, newConfig);
      var connection = new SonarCloudConnectionConfiguration("connectionId", "myOrg", false);

      when(connectionConfigurationRepository.getConnectionById(newConfig.getConnectionId())).thenReturn(connection);
      when(connectionAwareHttpClientProvider.getHttpClient(newConfig.getConnectionId())).thenReturn(httpClient);
      when(httpClient.createWebSocketConnection(WEBSOCKET_DEV_URL)).thenReturn(webSocket);

      webSocketService.handleEvent(bindingConfigChangedEvent);

      verify(webSocket).sendText("{\"action\":\"unsubscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey\"}", true);
      verify(webSocket).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey2\"}", true);
    }

    @Test
    void bindingUpdateShouldNotUnsubscribeFromOldProjectIfSomeoneElseStillInterested() {
      // there was already subscription for the project
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope", "projectKey");
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope2", "projectKey");
      webSocketService.ws = webSocket;

      var previousConfig = new BindingConfigChangedEvent.BindingConfig("configScope", "connectionId", "projectKey", false);
      var newConfig = new BindingConfigChangedEvent.BindingConfig("configScope", "connectionId", "projectKey2", false);
      var bindingConfigChangedEvent = new BindingConfigChangedEvent(previousConfig, newConfig);
      var connection = new SonarCloudConnectionConfiguration("connectionId", "myOrg", false);

      when(connectionConfigurationRepository.getConnectionById(newConfig.getConnectionId())).thenReturn(connection);
      when(connectionAwareHttpClientProvider.getHttpClient(newConfig.getConnectionId())).thenReturn(httpClient);
      when(httpClient.createWebSocketConnection(WEBSOCKET_DEV_URL)).thenReturn(webSocket);

      webSocketService.handleEvent(bindingConfigChangedEvent);

      verify(webSocket, times(0)).sendText("{\"action\":\"unsubscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey\"}", true);
    }

    @Test
    void bindingUpdateShouldDoNothingIfNotificationsDisabled() {
      var previousConfig = new BindingConfigChangedEvent.BindingConfig("configScope", "connectionId1", "projectKey", false);
      var newConfig = new BindingConfigChangedEvent.BindingConfig("configScope", "connectionId2", "projectKey", false);
      var bindingConfigChangedEvent = new BindingConfigChangedEvent(previousConfig, newConfig);
      var connection = new SonarCloudConnectionConfiguration("connectionId1", "myOrg", true);

      when(connectionConfigurationRepository.getConnectionById(newConfig.getConnectionId())).thenReturn(connection);
      when(connectionAwareHttpClientProvider.getHttpClient(newConfig.getConnectionId())).thenReturn(httpClient);
      when(httpClient.createWebSocketConnection(WEBSOCKET_DEV_URL)).thenReturn(webSocket);

      webSocketService.handleEvent(bindingConfigChangedEvent);

      verify(httpClient, times(0)).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket, times(0)).sendText(any(String.class), any(Boolean.class));
    }
  }

  @Nested
  class HandleConfigurationScopeAddedEvent {

    @Test
    void shouldSubscribeOnConfigScopeAddedEvent() {
      var configurationScopesAddedEvent = new ConfigurationScopesAddedEvent(Set.of("configScope1", "configScope2"));
      var bindingConfiguration = new BindingConfiguration("connectionId", "projectKey", false);
      var connection = new SonarCloudConnectionConfiguration("connectionId", "myOrg", false);

      when(configurationRepository.getBindingConfiguration("configScope1")).thenReturn(bindingConfiguration);
      when(connectionConfigurationRepository.getConnectionById("connectionId")).thenReturn(connection);
      when(connectionAwareHttpClientProvider.getHttpClient("connectionId")).thenReturn(httpClient);
      when(httpClient.createWebSocketConnection(WEBSOCKET_DEV_URL)).thenReturn(webSocket);

      webSocketService.handleEvent(configurationScopesAddedEvent);

      verify(httpClient).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey\"}", true);
    }

    @Test
    void shouldNotSubscribeOnConfigScopeAddedEventIfNotBound() {
      var configurationScopesAddedEvent = new ConfigurationScopesAddedEvent(Set.of("configScope1"));

      webSocketService.handleEvent(configurationScopesAddedEvent);

      verify(httpClient, times(0)).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket, times(0)).sendText(any(String.class), any(Boolean.class));
    }

    @Test
    void shouldNotSubscribeOnConfigScopeAddedEventIfBoundToSonarQube() {
      var configurationScopesAddedEvent = new ConfigurationScopesAddedEvent(Set.of("configScope1"));
      var bindingConfiguration = new BindingConfiguration("connectionId", "projectKey", false);
      var connection = new SonarQubeConnectionConfiguration("connectionId", "http://localhost:9000", false);

      when(configurationRepository.getBindingConfiguration("configScope1")).thenReturn(bindingConfiguration);
      when(connectionConfigurationRepository.getConnectionById("connectionId")).thenReturn(connection);
      when(connectionAwareHttpClientProvider.getHttpClient("connectionId")).thenReturn(httpClient);
      when(httpClient.createWebSocketConnection(WEBSOCKET_DEV_URL)).thenReturn(webSocket);

      webSocketService.handleEvent(configurationScopesAddedEvent);

      verify(httpClient, times(0)).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket, times(0)).sendText(any(String.class), any(Boolean.class));
    }

    @Test
    void shouldNotSubscribeOnConfigScopeAddedEventIfSonarCloudButNotifsDisabled() {
      var configurationScopesAddedEvent = new ConfigurationScopesAddedEvent(Set.of("configScope1"));
      var bindingConfiguration = new BindingConfiguration("connectionId", "projectKey", false);
      var connection = new SonarCloudConnectionConfiguration("connectionId", "myOrg", true);

      when(configurationRepository.getBindingConfiguration("configScope1")).thenReturn(bindingConfiguration);
      when(connectionConfigurationRepository.getConnectionById("connectionId")).thenReturn(connection);
      when(connectionAwareHttpClientProvider.getHttpClient("connectionId")).thenReturn(httpClient);
      when(httpClient.createWebSocketConnection(WEBSOCKET_DEV_URL)).thenReturn(webSocket);

      webSocketService.handleEvent(configurationScopesAddedEvent);

      verify(httpClient, times(0)).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket, times(0)).sendText(any(String.class), any(Boolean.class));
    }
  }

  @Nested
  class HandleConfigurationScopeRemovedEvent {
    @Test
    void shouldUnsubscribeFromProjectIfLastInterestedConfigScopeWasClosed() {
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope1", "projectKey");
      webSocketService.ws = webSocket;
      var configurationScopeRemovedEvent = new ConfigurationScopeRemovedEvent("configScope1");

      webSocketService.handleEvent(configurationScopeRemovedEvent);

      verify(webSocket).sendText("{\"action\":\"unsubscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey\"}", true);
    }

    @Test
    void shouldNotUnsubscribeFromProjectIfSomeoneStillInterested() {
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope1", "projectKey");
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope2", "projectKey");
      webSocketService.ws = webSocket;
      var configurationScopeRemovedEvent = new ConfigurationScopeRemovedEvent("configScope1");

      webSocketService.handleEvent(configurationScopeRemovedEvent);

      verify(webSocket, times(0)).sendText("{\"action\":\"unsubscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey\"}", true);
      assertEquals(1, webSocketService.subscribedProjectKeysByConfigScopes.size());
    }
  }

  @Nested
  class HandleConnectionConfigurationAddedEvent {
    @Test
    void shouldResubscribeAllProjectsBoundToAddedConnection() {
      var connectionConfigurationAddedEvent = new ConnectionConfigurationAddedEvent("connectionId");
      var bindingConfiguration1 = new BindingConfiguration("connectionId", "projectKey1", false);
      var bindingConfiguration2 = new BindingConfiguration("connectionId", "projectKey2", false);
      var connection = new SonarCloudConnectionConfiguration("connectionId", "myOrg", false);

      when(configurationRepository.getConfigScopesWithBindingConfiguredTo("connectionId")).thenReturn(
        List.of(new ConfigurationScope("configScope1", null, true, "config scope 1"),
          new ConfigurationScope("configScope2", null, true, "config scope 2")));
      when(configurationRepository.getBindingConfiguration("configScope1")).thenReturn(bindingConfiguration1);
      when(configurationRepository.getBindingConfiguration("configScope2")).thenReturn(bindingConfiguration2);
      when(connectionConfigurationRepository.getConnectionById("connectionId")).thenReturn(connection);
      when(connectionAwareHttpClientProvider.getHttpClient("connectionId")).thenReturn(httpClient);
      when(httpClient.createWebSocketConnection(WEBSOCKET_DEV_URL)).thenReturn(webSocket);

      webSocketService.handleEvent(connectionConfigurationAddedEvent);

      verify(httpClient, times(1)).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey1\"}", true);
      verify(webSocket).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey2\"}", true);
    }
  }

  @Nested
  class HandleConnectionConfigurationUpdateEvent {
    @Test
    void shouldCloseAndReopenConnectionOnConnectionUpdatedEvent() {
      webSocketService.connectionIdsInterestedInNotifications.add("connectionId");
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope1", "projectKey");
      webSocketService.ws = webSocket;

      var connectionConfigurationUpdatedEvent = new ConnectionConfigurationUpdatedEvent("connectionId");
      var connection = new SonarCloudConnectionConfiguration("connectionId", "myOrg", false);

      when(connectionConfigurationRepository.getConnectionById("connectionId")).thenReturn(connection);
      when(connectionAwareHttpClientProvider.getHttpClient("connectionId")).thenReturn(httpClient);
      when(httpClient.createWebSocketConnection(WEBSOCKET_DEV_URL)).thenReturn(webSocket);

      webSocketService.handleEvent(connectionConfigurationUpdatedEvent);

      assertEquals(1, webSocketService.connectionIdsInterestedInNotifications.size());
      verify(httpClient).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey\"}", true);
    }

    @Test
    void shouldDoNothingForSonarQubeOnConnectionOnConnectionUpdatedEvent() {
      var connectionConfigurationUpdatedEvent = new ConnectionConfigurationUpdatedEvent("connectionId");
      var connection = new SonarQubeConnectionConfiguration("connectionId", "http://localhost:9000", false);

      when(connectionConfigurationRepository.getConnectionById("connectionId")).thenReturn(connection);

      webSocketService.handleEvent(connectionConfigurationUpdatedEvent);

      verify(httpClient, times(0)).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket, times(0)).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey\"}", true);
    }

    @Test
    void shouldCloseWebSocketOnConnectionUpdatedEventWhenNotificationsAreDisabled() {
      webSocketService.connectionIdsInterestedInNotifications.add("connectionId");
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope1", "projectKey");
      webSocketService.ws = webSocket;

      var connectionConfigurationUpdatedEvent = new ConnectionConfigurationUpdatedEvent("connectionId");
      var connection = new SonarCloudConnectionConfiguration("connectionId", "myOrg", true);

      when(connectionConfigurationRepository.getConnectionById("connectionId")).thenReturn(connection);

      webSocketService.handleEvent(connectionConfigurationUpdatedEvent);

      assertEquals(0, webSocketService.connectionIdsInterestedInNotifications.size());
      verify(httpClient, times(0)).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket, times(0)).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey\"}", true);
      assertNull(webSocketService.ws);
    }

    @Test
    void shouldCloseAndReopenWebSocketOnConnectionUpdatedEventWhenNotificationsAreDisabled() {
      webSocketService.connectionIdsInterestedInNotifications.add("connectionId1");
      webSocketService.connectionIdsInterestedInNotifications.add("connectionId2");
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope1", "projectKey1");
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope2", "projectKey2");
      webSocketService.ws = webSocket;

      var connectionConfigurationUpdatedEvent = new ConnectionConfigurationUpdatedEvent("connectionId1");
      var connection = new SonarCloudConnectionConfiguration("connectionId1", "myOrg", true);

      when(connectionConfigurationRepository.getConnectionById("connectionId1")).thenReturn(connection);
      when(connectionAwareHttpClientProvider.getHttpClient("connectionId2")).thenReturn(httpClient);
      when(configurationRepository.getConfigScopesWithBindingConfiguredTo("connectionId1"))
        .thenReturn(List.of(new ConfigurationScope("configScope1", null, true, "Config scope 1")));
      when(httpClient.createWebSocketConnection(WEBSOCKET_DEV_URL)).thenReturn(webSocket);

      webSocketService.handleEvent(connectionConfigurationUpdatedEvent);

      assertEquals(1, webSocketService.connectionIdsInterestedInNotifications.size());
      verify(httpClient).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey2\"}", true);
      verify(webSocket, times(0)).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey1\"}", true);
    }
  }
}