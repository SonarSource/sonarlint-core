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
package mediumtest.websockets;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidChangeCredentialsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import testutils.websockets.WebSocketConnection;
import testutils.websockets.WebSocketServer;

import static java.util.Collections.emptyList;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

class WebSocketMediumTests {
  private WebSocketServer webSocketServer;
  private String oldSonarCloudWebSocketUrl;
  private SonarLintTestBackend backend;

  @BeforeEach
  void prepare() {
    oldSonarCloudWebSocketUrl = System.getProperty("sonarlint.internal.sonarcloud.websocket.url");
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
    if (webSocketServer != null) {
      webSocketServer.stop();
    }
    if (oldSonarCloudWebSocketUrl == null) {
      System.clearProperty("sonarlint.internal.sonarcloud.websocket.url");
    } else {
      System.setProperty("sonarlint.internal.sonarcloud.websocket.url", oldSonarCloudWebSocketUrl);
    }
  }

  @Nested
  class WhenScopeBound {
    @Test
    void should_create_connection_and_subscribe_to_events() {
      startWebSocketServer();
      var client = newFakeClient().withToken("connectionId", "token").build();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withUnboundConfigScope("configScope")
        .build(client);

      bind("configScope", "connectionId", "projectKey");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections())
        .extracting(WebSocketConnection::getAuthorizationHeader, WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple("Bearer token", true, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}"))));
    }

    @Test
    void should_not_create_websocket_connection_and_subscribe_when_bound_to_sonarqube() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarQubeConnection("connectionId")
        .withUnboundConfigScope("configScope")
        .build();

      bind("configScope", "connectionId", "projectKey");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections()).isEmpty());
    }

    @Test
    void should_unsubscribe_from_old_project_and_subscribe_to_new_project_when_key_changed() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      bind("configScope", "connectionId", "newProjectKey");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(true, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}",
          "{\"action\":\"unsubscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}",
          "{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"newProjectKey\"}"))));
    }

    @Test
    void should_unsubscribe_from_old_project_and_not_subscribe_to_new_project_if_it_is_already_subscribed() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope1", "connectionId", "projectKey1")
        .withBoundConfigScope("configScope2", "connectionId", "projectKey2")
        .build();
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      bind("configScope1", "connectionId", "projectKey2");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(true, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey2\"}",
          "{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey1\"}",
          "{\"action\":\"unsubscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey1\"}"))));
    }

    @Test
    void should_not_open_connection_or_subscribe_if_notifications_disabled_on_connection() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnection("connectionId", "orgKey", true, null)
        .withUnboundConfigScope("configScope")
        .build();

      bind("configScope", "connectionId", "newProjectKey");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections()).isEmpty());
    }

    @Test
    void should_not_resubscribe_if_project_already_bound() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnection("connectionId", "orgKey", true, null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      bind("configScope", "connectionId", "newProjectKey");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections()).isEmpty());
    }

    private void bind(String configScopeId, String connectionId, String newProjectKey) {
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScopeId, new BindingConfigurationDto(connectionId, newProjectKey, true)));
    }
  }

  @Nested
  class WhenUnbindingScope {
    @Test
    void should_unsubscribe_bound_project() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      unbind("configScope");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
        assertThat(webSocketServer.getConnections())
          .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
          .containsExactly(tuple(false, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}",
            "{\"action\":\"unsubscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}")));
      });
    }

    @Test
    void should_not_unsubscribe_if_the_same_project_key_is_used_in_another_binding() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope1", "connectionId", "projectKey")
        .withBoundConfigScope("configScope2", "connectionId", "projectKey")
        .build();

      unbind("configScope1");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(true, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}"))));
    }

    @Test
    void should_not_unsubscribe_if_notifications_disabled_on_connection() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnection("connectionId", "orgKey", true, null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      unbind("configScope");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections()).isEmpty());
    }

    private void unbind(String configScope) {
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScope, new BindingConfigurationDto(null, null, true)));
    }
  }

  @Nested
  class WhenScopeAdded {
    @Test
    void should_subscribe_if_bound_to_sonarcloud() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
        assertThat(webSocketServer.getConnections())
          .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
          .containsExactly(tuple(true, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}")));
      });
    }

    @Test
    void should_not_subscribe_if_not_bound() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withUnboundConfigScope("configScope")
        .build();

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections()).isEmpty());
    }

    @Test
    void should_not_subscribe_if_bound_to_sonarqube() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarQubeConnection("connectionId")
        .withUnboundConfigScope("configScope")
        .build();

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections()).isEmpty());
    }

    @Test
    void should_not_subscribe_if_bound_to_sonarcloud_but_notifications_are_disabled() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnection("connectionId", "orgKey", true, null)
        .withUnboundConfigScope("configScope")
        .build();

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections()).isEmpty());
    }
  }

  @Nested
  class WhenScopeRemoved {
    @Test
    void should_unsubscribe_from_project() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams("configScope"));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
        assertThat(webSocketServer.getConnections())
          .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
          .containsExactly(tuple(false, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}",
            "{\"action\":\"unsubscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}")));
      });
    }

    @Test
    void should_not_unsubscribe_if_another_scope_is_bound_to_same_project() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId1", "orgKey1", null)
        .withSonarCloudConnectionAndNotifications("connectionId2", "orgKey2", null)
        .withBoundConfigScope("configScope1", "connectionId1", "projectKey")
        .withBoundConfigScope("configScope2", "connectionId2", "projectKey")
        .build();
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams("configScope1"));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(true, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}"))));
    }
  }

  @Nested
  class WhenConnectionCredentialsChanged {
    @Test
    void should_close_and_reopen_connection_for_sonarcloud_if_already_open() {
      startWebSocketServer();
      var client = newFakeClient().withToken("connectionId", "firstToken").build();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build(client);
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());
      client.setToken("connectionId", "secondToken");

      backend.getConnectionService().didChangeCredentials(new DidChangeCredentialsParams("connectionId"));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections())
        .extracting(WebSocketConnection::getAuthorizationHeader, WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple("Bearer firstToken", false, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}")),
          tuple("Bearer secondToken", true, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}"))));
    }

    @Test
    void should_do_nothing_for_sonarcloud_if_not_already_open() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .build();

      backend.getConnectionService().didChangeCredentials(new DidChangeCredentialsParams("connectionId"));

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections()).isEmpty());
    }

    @Test
    void should_do_nothing_for_sonarqube() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarQubeConnection("connectionId")
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      backend.getConnectionService().didChangeCredentials(new DidChangeCredentialsParams("connectionId"));

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections()).isEmpty());
    }
  }

  @Nested
  class WhenConnectionAdded {
    @Test
    void should_subscribe_all_projects_bound_to_added_connection() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(emptyList(), List.of(new SonarCloudConnectionConfigurationDto("connectionId", "orgKey", false))));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(true, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}"))));
    }
  }

  @Nested
  class WhenConnectionRemoved {
    @Test
    void should_close_connection() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(emptyList(), emptyList()));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(false, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}"))));
    }

    @Test
    void should_not_close_connection_if_another_sonarcloud_connection_is_active() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId1", "orgKey1", null)
        .withSonarCloudConnectionAndNotifications("connectionId2", "orgKey2", null)
        .withBoundConfigScope("configScope1", "connectionId1", "projectKey1")
        .withBoundConfigScope("configScope2", "connectionId2", "projectKey2")
        .build();
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(emptyList(), List.of(new SonarCloudConnectionConfigurationDto("connectionId2", "orgKey2", false))));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(true, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey2\"}",
          "{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey1\"}",
          "{\"action\":\"unsubscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey1\"}"))));
    }
  }

  @Nested
  class WhenConnectionUpdated {
    @Test
    void should_do_nothing_for_sonarqube() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarQubeConnection("connectionId")
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto("connectionid", "url", false)), emptyList()));

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections()).isEmpty());
    }

    @Test
    void should_do_nothing_when_no_project_bound_to_sonarcloud() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .build();

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(emptyList(), List.of(new SonarCloudConnectionConfigurationDto("connectionId", "orgKey2", false))));

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections()).isEmpty());
    }

    @Test
    void should_close_websocket_if_notifications_disabled() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(emptyList(), List.of(new SonarCloudConnectionConfigurationDto("connectionId", "orgKey", true))));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(false, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}"))));
    }

    @Test
    void should_close_and_reopen_websocket_if_notifications_are_disabled_but_other_connection_is_active() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId1", "orgKey1", null)
        .withSonarCloudConnectionAndNotifications("connectionId2", "orgKey2", null)
        .withBoundConfigScope("configScope1", "connectionId1", "projectKey1")
        .withBoundConfigScope("configScope2", "connectionId2", "projectKey2")
        .build();
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty() && webSocketServer.getConnections().get(0).getReceivedMessages().size() == 2);

      backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(emptyList(),
        List.of(new SonarCloudConnectionConfigurationDto("connectionId1", "orgKey1", false), new SonarCloudConnectionConfigurationDto("connectionId2", "orgKey2", true))));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(false, List.of(
          "{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey2\"}",
          "{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey1\"}")),
          tuple(true, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey1\"}"))));
    }

    @Test
    void should_open_websocket_and_subscribe_to_all_bound_projects_if_enabled_notifications() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnection("connectionId", "orgKey", true, null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(emptyList(), List.of(new SonarCloudConnectionConfigurationDto("connectionId", "orgKey", false))));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(true, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}"))));
    }
  }

  @Nested
  class WhenReceivingQualityGateChangedEvent {
    @Test
    void should_forward_to_client_as_smart_notifications() {
      startWebSocketServer();
      var client = newFakeClient().build();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build(client);
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      webSocketServer.getConnections().get(0).sendMessage(
        "{\"event\": \"QualityGateChanged\", \"data\": {\"message\": \"msg\", \"link\": \"lnk\", \"project\": \"projectKey\", \"date\": \"2023-07-19T15:08:01+0000\"}}");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow())
        .extracting(ShowSmartNotificationParams::getScopeIds, ShowSmartNotificationParams::getCategory, ShowSmartNotificationParams::getLink, ShowSmartNotificationParams::getText,
          ShowSmartNotificationParams::getConnectionId)
        .containsExactly(tuple(Set.of("configScope"), "QUALITY_GATE", "lnk", "msg", "connectionId")));
    }

    @Test
    void should_not_forward_to_client_if_the_event_data_is_malformed() {
      startWebSocketServer();
      var client = newFakeClient().build();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build(client);
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      webSocketServer.getConnections().get(0).sendMessage("{\"event\": [\"QualityGateChanged\"], \"data\": {\"message\": 0}}");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow()).isEmpty());
    }

    @Test
    void should_not_forward_to_client_if_the_message_is_missing() {
      startWebSocketServer();
      var client = newFakeClient().build();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build(client);
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      webSocketServer.getConnections().get(0)
        .sendMessage("{\"event\": [\"QualityGateChanged\"], \"data\": {\"link\": \"lnk\", \"project\": \"projectKey\", \"date\": \"2023-07-19T15:08:01+0000\"}}");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow()).isEmpty());
    }

    @Test
    void should_not_forward_to_client_if_the_link_is_missing() {
      startWebSocketServer();
      var client = newFakeClient().build();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build(client);
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      webSocketServer.getConnections().get(0)
        .sendMessage("{\"event\": [\"QualityGateChanged\"], \"data\": {\"message\": \"msg\", \"project\": \"projectKey\", \"date\": \"2023-07-19T15:08:01+0000\"}}");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow()).isEmpty());
    }

    @Test
    void should_not_forward_to_client_if_the_project_is_missing() {
      startWebSocketServer();
      var client = newFakeClient().build();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build(client);
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      webSocketServer.getConnections().get(0)
        .sendMessage("{\"event\": [\"QualityGateChanged\"], \"data\": {\"message\": \"msg\", \"link\": \"lnk\", \"date\": \"2023-07-19T15:08:01+0000\"}}");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow()).isEmpty());
    }

    @Test
    void should_not_forward_to_client_if_the_date_is_missing() {
      startWebSocketServer();
      var client = newFakeClient().build();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build(client);
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      webSocketServer.getConnections().get(0)
        .sendMessage("{\"event\": [\"QualityGateChanged\"], \"data\": {\"message\": \"msg\", \"link\": \"lnk\", \"project\": \"projectKey\"}}");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow()).isEmpty());
    }
  }

  @Nested
  class WhenReceivingUnexpectedEvents {
    @Test
    void should_ignore_if_the_event_type_is_unknown() {
      startWebSocketServer();
      var client = newFakeClient().build();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build(client);
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      webSocketServer.getConnections().get(0).sendMessage("{\"event\": \"UnknownEvent\", \"data\": {\"message\": \"msg\"}}");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow()).isEmpty());
    }

    @Test
    void should_ignore_if_the_event_is_malformed() {
      startWebSocketServer();
      var client = newFakeClient().build();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build(client);
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      webSocketServer.getConnections().get(0).sendMessage("{\"event\": \"Malformed");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow()).isEmpty());
    }

    @Test
    void should_not_forward_to_client_duplicated_event() {
      startWebSocketServer();
      var client = newFakeClient().build();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build(client);
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServer.getConnections().isEmpty());

      webSocketServer.getConnections().get(0).sendMessage(
        "{\"event\": \"QualityGateChanged\", \"data\": {\"message\": \"msg\", \"link\": \"lnk\", \"project\": \"projectKey\", \"date\": \"2023-07-19T15:08:01+0000\"}}");
      webSocketServer.getConnections().get(0).sendMessage(
        "{\"event\": \"QualityGateChanged\", \"data\": {\"message\": \"msg\", \"link\": \"lnk\", \"project\": \"projectKey\", \"date\": \"2023-07-19T15:08:01+0000\"}}");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow())
        .extracting(ShowSmartNotificationParams::getScopeIds, ShowSmartNotificationParams::getCategory, ShowSmartNotificationParams::getLink, ShowSmartNotificationParams::getText,
          ShowSmartNotificationParams::getConnectionId)
        .containsExactly(tuple(Set.of("configScope"), "QUALITY_GATE", "lnk", "msg", "connectionId")));
    }
  }

  @Nested
  class WhenWebSocketClosed {
    @Test
    void should_refresh_connection_if_closed_by_server() {
      startWebSocketServer();
      backend = newBackend()
        .withSmartNotifications()
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .build();
      await().atMost(Duration.ofSeconds(2)).until(() -> webSocketServer.getConnections().size() == 1 && webSocketServer.getConnections().get(0).getReceivedMessages().size() == 1);

      webSocketServer.getConnections().get(0).close();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServer.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(false, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}")),
          tuple(true, List.of("{\"action\":\"subscribe\",\"events\":[\"QualityGateChanged\"],\"filterType\":\"PROJECT\",\"project\":\"projectKey\"}"))));
    }
  }

  private void startWebSocketServer() {
    webSocketServer = new WebSocketServer();
    webSocketServer.start();
    System.setProperty("sonarlint.internal.sonarcloud.websocket.url", webSocketServer.getUrl());
  }

}
