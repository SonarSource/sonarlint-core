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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;

import static org.mockito.ArgumentMatchers.any;
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
}