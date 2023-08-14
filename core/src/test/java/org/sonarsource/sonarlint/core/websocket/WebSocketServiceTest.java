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
import org.checkerframework.checker.units.qual.C;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void should_create_connection_and_subscribe_to_events() {
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
    void should_not_do_anything_if_binding_removed() {
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
    void should_not_do_anything_if_sonar_qube_connection() {
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
    void should_unsubscribe_from_old_project_and_subscribe_to_new_project() {
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
    void should_not_unsubscribe_from_old_project_if_someone_else_still_interested() {
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
    void should_do_nothing_if_notifications_disabled() {
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
    void should_subscribe_on_config_scope_added_event() {
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
    void should_not_subscribe_on_config_scope_added_event_if_not_bound() {
      var configurationScopesAddedEvent = new ConfigurationScopesAddedEvent(Set.of("configScope1"));

      webSocketService.handleEvent(configurationScopesAddedEvent);

      verify(httpClient, times(0)).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket, times(0)).sendText(any(String.class), any(Boolean.class));
    }

    @Test
    void should_not_subscribe_on_config_scope_added_event_if_bound_to_sonar_qube() {
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
    void should_not_subscribe_on_config_scope_added_event_if_sonar_cloud_but_notifs_disabled() {
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
    void should_unsubscribe_from_project_if_last_interested_config_scope_was_closed() {
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope1", "projectKey");
      webSocketService.ws = webSocket;
      var configurationScopeRemovedEvent = new ConfigurationScopeRemovedEvent("configScope1");

      webSocketService.handleEvent(configurationScopeRemovedEvent);

      verify(webSocket).sendText("{\"action\":\"unsubscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey\"}", true);
    }

    @Test
    void should_not_unsubscribe_from_project_if_someone_still_interested() {
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
    void should_resubscribe_all_projects_bound_to_added_connection() {
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
    void should_close_and_reopen_connection_on_connection_updated_event() {
      webSocketService.connectionIdsInterestedInNotifications.add("connectionId");
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope1", "projectKey");
      webSocketService.ws = webSocket;

      var connectionConfigurationUpdatedEvent = new ConnectionConfigurationUpdatedEvent("connectionId");
      var connection = new SonarCloudConnectionConfiguration("connectionId", "myOrg", false);
      var bindingConfiguration = new BindingConfiguration("connectionId", "projectKey", false);

      when(connectionConfigurationRepository.getConnectionById("connectionId")).thenReturn(connection);
      when(configurationRepository.getBindingConfiguration("configScope1")).thenReturn(bindingConfiguration);
      when(connectionAwareHttpClientProvider.getHttpClient("connectionId")).thenReturn(httpClient);
      when(configurationRepository.getConfigScopeIds())
        .thenReturn(Set.of("configScope1"));
      when(httpClient.createWebSocketConnection(WEBSOCKET_DEV_URL)).thenReturn(webSocket);

      webSocketService.handleEvent(connectionConfigurationUpdatedEvent);

      assertEquals(1, webSocketService.connectionIdsInterestedInNotifications.size());
      verify(httpClient).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey\"}", true);
    }

    @Test
    void should_do_nothing_for_sonarqube_on_connection_updated_event() {
      var connectionConfigurationUpdatedEvent = new ConnectionConfigurationUpdatedEvent("connectionId");
      var connection = new SonarQubeConnectionConfiguration("connectionId", "http://localhost:9000", false);

      when(connectionConfigurationRepository.getConnectionById("connectionId")).thenReturn(connection);

      webSocketService.handleEvent(connectionConfigurationUpdatedEvent);

      verify(httpClient, times(0)).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket, times(0)).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey\"}", true);
    }

    @Test
    void should_close_websocket_and_remove_subscriptions_on_connection_updated_event_when_notifications_are_disabled() {
      webSocketService.connectionIdsInterestedInNotifications.add("connectionId");
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope1", "projectKey");
      webSocketService.ws = webSocket;

      var connectionConfigurationUpdatedEvent = new ConnectionConfigurationUpdatedEvent("connectionId");
      var connection = new SonarCloudConnectionConfiguration("connectionId", "myOrg", true);

      when(connectionConfigurationRepository.getConnectionById("connectionId")).thenReturn(connection);
      when(configurationRepository.getConfigScopesWithBindingConfiguredTo("connectionId"))
        .thenReturn(List.of(new ConfigurationScope("configScope1", null, true, "config scope 1")));

      webSocketService.handleEvent(connectionConfigurationUpdatedEvent);

      assertEquals(0, webSocketService.connectionIdsInterestedInNotifications.size());
      assertEquals(0, webSocketService.subscribedProjectKeysByConfigScopes.size());
      verify(httpClient, times(0)).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket, times(0)).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey\"}", true);
      assertNull(webSocketService.ws);
    }

    @Test
    void should_close_and_reopen_websocket_on_connection_updated_event_when_notifications_are_disabled() {
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

    @Test
    void should_close_and_reopen_websocket_on_connection_updated_event_when_notifications_settings_did_not_change() {
      webSocketService.connectionIdsInterestedInNotifications.add("connectionId2");
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope2", "projectKey2");
      webSocketService.ws = webSocket;

      var connectionConfigurationUpdatedEvent = new ConnectionConfigurationUpdatedEvent("connectionId1");
      var connection = new SonarCloudConnectionConfiguration("connectionId1", "myOrg", true);

      when(connectionConfigurationRepository.getConnectionById("connectionId1")).thenReturn(connection);
      when(connectionAwareHttpClientProvider.getHttpClient("connectionId2")).thenReturn(httpClient);
      when(httpClient.createWebSocketConnection(WEBSOCKET_DEV_URL)).thenReturn(webSocket);

      webSocketService.handleEvent(connectionConfigurationUpdatedEvent);

      assertEquals(1, webSocketService.connectionIdsInterestedInNotifications.size());
      assertEquals(1, webSocketService.subscribedProjectKeysByConfigScopes.size());
      verify(httpClient).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey2\"}", true);
      verify(webSocket, times(0)).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey1\"}", true);
    }

    @Test
    void should_open_websocket_and_subscribe_to_all_bound_projects_if_opted_in_for_notifications() {
      webSocketService.ws = webSocket;

      var connectionConfigurationUpdatedEvent = new ConnectionConfigurationUpdatedEvent("connectionId");
      var connection = new SonarCloudConnectionConfiguration("connectionId", "myOrg", false);
      var bindingConfiguration = new BindingConfiguration("connectionId", "projectKey", false);

      when(connectionConfigurationRepository.getConnectionById("connectionId")).thenReturn(connection);
      when(configurationRepository.getConfigScopeIds())
        .thenReturn(Set.of("configScope1"));
      when(connectionAwareHttpClientProvider.getHttpClient("connectionId")).thenReturn(httpClient);
      when(httpClient.createWebSocketConnection(WEBSOCKET_DEV_URL)).thenReturn(webSocket);
      when(configurationRepository.getBindingConfiguration("configScope1")).thenReturn(bindingConfiguration);

      webSocketService.handleEvent(connectionConfigurationUpdatedEvent);

      assertEquals(1, webSocketService.connectionIdsInterestedInNotifications.size());
      assertEquals(1, webSocketService.subscribedProjectKeysByConfigScopes.size());
      verify(httpClient).createWebSocketConnection(WEBSOCKET_DEV_URL);
      verify(webSocket).sendText("{\"action\":\"subscribe\",\"eventTypes\":\"QualityGateChanged\",\"project\":\"projectKey\"}", true);
    }
  }

  @Nested
  class HandleConnectionConfigurationRemovedEvent {
    @Test
    void should_close_connection_if_connection_removed_and_nobody_left() {
      webSocketService.connectionIdsInterestedInNotifications.add("connectionId1");
      webSocketService.ws = webSocket;

      var connectionConfigurationRemovedEvent = new ConnectionConfigurationRemovedEvent("connectionId1");

      webSocketService.handleEvent(connectionConfigurationRemovedEvent);

      verify(webSocket).sendClose(WebSocket.NORMAL_CLOSURE, "");
      assertNull(webSocketService.ws);
      assertEquals(0, webSocketService.connectionIdsInterestedInNotifications.size());
    }

    @Test
    void should_not_close_connection_if_connection_removed_and_somebody_left() {
      webSocketService.connectionIdsInterestedInNotifications.add("connectionId1");
      webSocketService.connectionIdsInterestedInNotifications.add("connectionId2");
      webSocketService.ws = webSocket;

      var connectionConfigurationRemovedEvent = new ConnectionConfigurationRemovedEvent("connectionId1");

      webSocketService.handleEvent(connectionConfigurationRemovedEvent);

      verify(webSocket, times(0)).sendClose(WebSocket.NORMAL_CLOSURE, "");
      assertNotNull(webSocketService.ws);
      assertEquals(1, webSocketService.connectionIdsInterestedInNotifications.size());
    }

    @Test
    void should_remove_projects_from_subscriptions_when_connection_removed() {
      webSocketService.connectionIdsInterestedInNotifications.add("connectionId1");
      webSocketService.connectionIdsInterestedInNotifications.add("connectionId2");
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope1", "projectKey1");
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope2", "projectKey2");
      webSocketService.subscribedProjectKeysByConfigScopes.put("configScope3", "projectKey2");
      webSocketService.ws = webSocket;
      var connectionConfigurationRemovedEvent = new ConnectionConfigurationRemovedEvent("connectionId1");

      when(configurationRepository.getConfigScopesWithBindingConfiguredTo("connectionId1"))
        .thenReturn(List.of(new ConfigurationScope("configScope1", null, true, "config scope 1"),
          new ConfigurationScope("configScope2", null, true, "config scope 2")));

      webSocketService.handleEvent(connectionConfigurationRemovedEvent);

      verify(webSocket, times(0)).sendClose(WebSocket.NORMAL_CLOSURE, "");
      assertNotNull(webSocketService.ws);
      assertEquals(1, webSocketService.connectionIdsInterestedInNotifications.size());
      assertEquals(1, webSocketService.subscribedProjectKeysByConfigScopes.size());
      assertNull(webSocketService.subscribedProjectKeysByConfigScopes.get("configScope2"));
    }
  }
}