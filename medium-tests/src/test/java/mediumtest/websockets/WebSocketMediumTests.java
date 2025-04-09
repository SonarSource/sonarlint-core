/*
 * SonarLint Core - Medium Tests
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
package mediumtest.websockets;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidChangeCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.sonarsource.sonarlint.core.test.utils.server.websockets.WebSocketConnection;
import org.sonarsource.sonarlint.core.test.utils.server.websockets.WebSocketServer;

import static java.util.Collections.emptyList;
import static mediumtest.websockets.WebSocketMediumTests.WebSocketPayloadBuilder.webSocketPayloadBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture.USER_AGENT_FOR_TESTS;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerIssueFixtures.aServerIssue;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerSecurityHotspotFixture.aServerHotspot;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerTaintIssueFixtures.aServerTaintIssue;

class WebSocketMediumTests {

  // not used but useful to register a log output
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private WebSocketServer webSocketServerEU;
  private WebSocketServer webSocketServerUS;

  @BeforeEach
  void prepare() {
    webSocketServerEU = new WebSocketServer();
    webSocketServerEU.start();
    webSocketServerUS = new WebSocketServer(WebSocketServer.DEFAULT_PORT + 1);
    webSocketServerUS.start();
  }

  @AfterEach
  void tearDown() {
    if (webSocketServerEU != null) {
      webSocketServerEU.stop();
    }
    if (webSocketServerUS != null) {
      webSocketServerUS.stop();
    }
  }

  @Nested
  class WhenScopeBound {
    @SonarLintTest
    void should_create_connection_and_subscribe_to_events(SonarLintTestHarness harness) {
      var client = harness.newFakeClient().withToken("connectionId", "token").build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withUnboundConfigScope("configScope")
        .start(client);

      bind(backend, "configScope", "connectionId", "projectKey");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::getAuthorizationHeader, WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple("Bearer token", true, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").build())));
    }

    @SonarLintTest
    void should_set_user_agent(SonarLintTestHarness harness) {
      var client = harness.newFakeClient().withToken("connectionId", "token").build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withUnboundConfigScope("configScope")
        .start(client);

      bind(backend, "configScope", "connectionId", "projectKey");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::getUserAgent)
        .containsExactly(USER_AGENT_FOR_TESTS));
    }

    @SonarLintTest
    void should_not_create_websocket_connection_and_subscribe_when_bound_to_sonarqube(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarQubeConnection("connectionId")
        .withUnboundConfigScope("configScope")
        .start(client);

      bind(backend, "configScope", "connectionId", "projectKey");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections()).isEmpty());
    }

    @SonarLintTest
    void should_unsubscribe_from_old_project_and_subscribe_to_new_project_when_key_changed(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      bind(backend, "configScope", "connectionId", "newProjectKey");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(true, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").unsubscribeWithProjectKey(
          "projectKey").subscribeWithProjectKey("newProjectKey").build())));
    }

    @SonarLintTest
    void should_unsubscribe_from_old_project_and_not_subscribe_to_new_project_if_it_is_already_subscribed(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope1", "connectionId", "projectKey1")
        .withBoundConfigScope("configScope2", "connectionId", "projectKey2")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey2", "projectKey1");

      bind(backend, "configScope1", "connectionId", "projectKey2");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(true,
          webSocketPayloadBuilder().subscribeWithProjectKey("projectKey2", "projectKey1").unsubscribeWithProjectKey("projectKey1").build())));
    }

    @SonarLintTest
    void should_unsubscribe_from_old_region_and_subscribe_to_new_when_connection_and_region_changed(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .withToken("connectionIdUS", "token")
        .build();

      // backend with two sonarqube cloud connections - one EU and one US; bound initially to EU
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withSonarCloudConnection("connectionIdUS", "orgKey", false, null, SonarCloudRegion.US)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      // Change binding to US connection
      bind(backend, "configScope", "connectionIdUS", "projectKey");

      // assert unsubscribed and closed connection to EU region; subscribed to US region.
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
        assertThat(webSocketServerEU.getConnections())
          .hasSize(1)
          .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
          .containsExactly(tuple(false, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").unsubscribeWithProjectKey(
              "projectKey").build()));
        assertThat(webSocketServerUS.getConnections())
          .hasSize(1)
          .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
          .containsExactly(tuple(true, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").build()));
      });
    }

    @SonarLintTest
    void should_not_open_connection_or_subscribe_if_notifications_disabled_on_connection(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnection("connectionId", "orgKey", true, null)
        .withUnboundConfigScope("configScope")
        .start(client);

      bind(backend, "configScope", "connectionId", "newProjectKey");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections()).isEmpty());
    }

    @SonarLintTest
    void should_not_resubscribe_if_project_already_bound(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnection("connectionId", "orgKey", true, null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);

      bind(backend, "configScope", "connectionId", "newProjectKey");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections()).isEmpty());
    }

    private void bind(SonarLintTestRpcServer backend, String configScopeId, String connectionId, String newProjectKey) {
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScopeId,
        new BindingConfigurationDto(connectionId, newProjectKey, true)));
    }
  }

  @Nested
  class WhenUnbindingScope {
    @SonarLintTest
    void should_unsubscribe_bound_project(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      unbind(backend, "configScope");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
        assertThat(webSocketServerEU.getConnections())
          .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
          .containsExactly(tuple(false, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").unsubscribeWithProjectKey(
            "projectKey").build()));
      });
    }

    @SonarLintTest
    void should_not_unsubscribe_if_the_same_project_key_is_used_in_another_binding(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope1", "connectionId", "projectKey")
        .withBoundConfigScope("configScope2", "connectionId", "projectKey")
        .start(client);

      unbind(backend, "configScope1");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(true, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").build())));
    }

    @SonarLintTest
    void should_not_unsubscribe_if_notifications_disabled_on_connection(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnection("connectionId", "orgKey", true, null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);

      unbind(backend, "configScope");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections()).isEmpty());
    }

    private void unbind(SonarLintTestRpcServer backend, String configScope) {
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScope, new BindingConfigurationDto(null, null,
        true)));
    }
  }

  @Nested
  class WhenScopeAdded {
    @SonarLintTest
    void should_subscribe_if_bound_to_sonarcloud(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
        assertThat(webSocketServerEU.getConnections())
          .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
          .containsExactly(tuple(true, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").build()));
      });
    }

    @SonarLintTest
    void should_not_subscribe_if_not_bound(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withUnboundConfigScope("configScope")
        .start(client);

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections()).isEmpty());
    }

    @SonarLintTest
    void should_not_subscribe_if_bound_to_sonarqube(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      newBackendWithWebSockets(harness)
        .withSonarQubeConnection("connectionId")
        .withUnboundConfigScope("configScope")
        .start(client);

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections()).isEmpty());
    }

    @SonarLintTest
    void should_not_subscribe_if_bound_to_sonarcloud_but_notifications_are_disabled(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      newBackendWithWebSockets(harness)
        .withSonarCloudConnection("connectionId", "orgKey", true, null)
        .withUnboundConfigScope("configScope")
        .start(client);

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections()).isEmpty());
    }
  }

  @Nested
  class WhenScopeRemoved {
    @SonarLintTest
    void should_unsubscribe_from_project(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams("configScope"));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
        assertThat(webSocketServerEU.getConnections())
          .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
          .containsExactly(tuple(false, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").unsubscribeWithProjectKey(
            "projectKey").build()));
      });
    }

    @SonarLintTest
    void should_not_unsubscribe_if_another_scope_is_bound_to_same_project(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId1", "token1")
        .withToken("connectionId2", "token2")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId1", "orgKey1", null)
        .withSonarCloudConnectionAndNotifications("connectionId2", "orgKey2", null)
        .withBoundConfigScope("configScope1", "connectionId1", "projectKey")
        .withBoundConfigScope("configScope2", "connectionId2", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams("configScope1"));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(true, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").build())));
    }

    @SonarLintTest
    void should_not_unsubscribe_if_connection_was_already_closed(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");
      backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(emptyList(),
        List.of(new SonarCloudConnectionConfigurationDto("connectionId", "orgKey", SonarCloudRegion.EU, true))));
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections()).extracting(WebSocketConnection::isOpened).containsExactly(false));

      backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams("configScope"));

      await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::isOpened)
        .containsExactly(false));
    }
  }

  @Nested
  class WhenConnectionCredentialsChanged {
    @SonarLintTest
    void should_close_and_reopen_connection_for_sonarcloud_if_already_open(SonarLintTestHarness harness) {
      var client = harness.newFakeClient().withToken("connectionId", "firstToken").build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");
      client.setToken("connectionId", "secondToken");

      backend.getConnectionService().didChangeCredentials(new DidChangeCredentialsParams("connectionId"));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::getAuthorizationHeader, WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple("Bearer firstToken", false, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").build()),
          tuple("Bearer secondToken", true, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").build())));
    }

    @SonarLintTest
    void should_do_nothing_for_sonarcloud_if_not_already_open(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .start(client);

      backend.getConnectionService().didChangeCredentials(new DidChangeCredentialsParams("connectionId"));

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections()).isEmpty());
    }

    @SonarLintTest
    void should_do_nothing_for_sonarqube(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarQubeConnection("connectionId")
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);

      backend.getConnectionService().didChangeCredentials(new DidChangeCredentialsParams("connectionId"));

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections()).isEmpty());
    }
  }

  @Nested
  class WhenConnectionAdded {

    @SonarLintTest
    void should_subscribe_all_projects_bound_to_added_connection(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(emptyList(), List.of(new SonarCloudConnectionConfigurationDto("connectionId", "orgKey", SonarCloudRegion.EU, false))));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(true, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").build())));
    }

    @SonarLintTest
    void should_log_failure_and_reconnect_later_if_server_unavailable(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      webSocketServerEU.stop();
      var backend = newBackendWithWebSockets(harness)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(emptyList(), List.of(new SonarCloudConnectionConfigurationDto("connectionId", "orgKey", SonarCloudRegion.EU, false))));

      await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Error while trying to create websocket connection for ws://localhost:54321/endpoint"));

      webSocketServerEU.start();
      // Emulate a change on the connection to force websocket service to reconnect
      backend.getConnectionService().didChangeCredentials(new DidChangeCredentialsParams("connectionId"));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(true, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").build())));
    }
  }

  @Nested
  class WhenConnectionRemoved {
    @SonarLintTest
    void should_close_connection(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(emptyList(), emptyList()));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::isOpened)
        .containsExactly(false));
    }

    @SonarLintTest
    void should_not_close_connection_if_another_sonarcloud_connection_is_active(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId1", "token1")
        .withToken("connectionId2", "token2")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId1", "orgKey1", null)
        .withSonarCloudConnectionAndNotifications("connectionId2", "orgKey2", null)
        .withBoundConfigScope("configScope1", "connectionId1", "projectKey1")
        .withBoundConfigScope("configScope2", "connectionId2", "projectKey2")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey2", "projectKey1");

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(emptyList(), List.of(new SonarCloudConnectionConfigurationDto("connectionId2", "orgKey2", SonarCloudRegion.EU, false))));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(true,
          webSocketPayloadBuilder().subscribeWithProjectKey("projectKey2", "projectKey1").unsubscribeWithProjectKey("projectKey1").build())));
    }
  }

  @Nested
  class WhenConnectionUpdated {
    @SonarLintTest
    void should_do_nothing_for_sonarqube(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarQubeConnection("connectionId")
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto("connectionid", "url",
          false)), emptyList()));

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections()).isEmpty());
    }

    @SonarLintTest
    void should_do_nothing_when_no_project_bound_to_sonarcloud(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .start(client);

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(emptyList(), List.of(new SonarCloudConnectionConfigurationDto("connectionId", "orgKey2", SonarCloudRegion.EU, false))));

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections()).isEmpty());
    }

    @SonarLintTest
    void should_close_websocket_if_notifications_disabled(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(emptyList(), List.of(new SonarCloudConnectionConfigurationDto("connectionId", "orgKey", SonarCloudRegion.EU, true))));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(false, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").build())));
    }

    @SonarLintTest
    void should_close_and_reopen_websocket_if_notifications_are_disabled_but_other_connection_is_active(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId1", "token1")
        .withToken("connectionId2", "token2")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId1", "orgKey1", null)
        .withSonarCloudConnectionAndNotifications("connectionId2", "orgKey2", null)
        .withBoundConfigScope("configScope1", "connectionId1", "projectKey1")
        .withBoundConfigScope("configScope2", "connectionId2", "projectKey2")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey2", "projectKey1");

      backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(emptyList(),
        List.of(new SonarCloudConnectionConfigurationDto("connectionId1", "orgKey1", SonarCloudRegion.EU, false), new SonarCloudConnectionConfigurationDto(
          "connectionId2", "orgKey2", SonarCloudRegion.EU, true))));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(false, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey2", "projectKey1").build()),
          tuple(true, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey1").build())));
    }

    @SonarLintTest
    void should_open_websocket_and_subscribe_to_all_bound_projects_if_enabled_notifications(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnection("connectionId", "orgKey", true, null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);

      backend.getConnectionService()
        .didUpdateConnections(new DidUpdateConnectionsParams(emptyList(), List.of(new SonarCloudConnectionConfigurationDto("connectionId", "orgKey", SonarCloudRegion.EU, false))));

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .containsExactly(tuple(true, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").build())));
    }
  }

  @Nested
  class WhenReceivingSmartNotificationEvent {
    @SonarLintTest
    void should_forward_to_client_as_smart_notifications(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      newBackendWithWebSockets(harness)
        .withBackendCapability(BackendCapability.SMART_NOTIFICATIONS)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      webSocketServerEU.getConnections().get(0).sendMessage(
        "{\"event\": \"QualityGateChanged\", \"data\": {\"message\": \"msg\", \"link\": \"lnk\", \"project\": \"projectKey\", \"date\": " +
          "\"2023-07-19T15:08:01+0000\"}}");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow())
        .extracting(ShowSmartNotificationParams::getScopeIds, ShowSmartNotificationParams::getCategory,
          ShowSmartNotificationParams::getLink, ShowSmartNotificationParams::getText,
          ShowSmartNotificationParams::getConnectionId)
        .containsExactly(tuple(Set.of("configScope"), "QUALITY_GATE", "lnk", "msg", "connectionId")));
    }

    @SonarLintTest
    void should_forward_my_new_issues_to_client_as_smart_notifications(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      newBackendWithWebSockets(harness)
        .withBackendCapability(BackendCapability.SMART_NOTIFICATIONS)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      webSocketServerEU.getConnections().get(0).sendMessage(
        "{\"event\": \"MyNewIssues\", \"data\": {\"message\": \"msg\", \"link\": \"lnk\", \"project\": \"projectKey\", \"date\": " +
          "\"2023-07-19T15:08:01+0000\"}}");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow())
        .extracting(ShowSmartNotificationParams::getScopeIds, ShowSmartNotificationParams::getCategory,
          ShowSmartNotificationParams::getLink, ShowSmartNotificationParams::getText,
          ShowSmartNotificationParams::getConnectionId)
        .containsExactly(tuple(Set.of("configScope"), "NEW_ISSUES", "lnk", "msg", "connectionId")));
    }

    @SonarLintTest
    void should_not_forward_to_client_if_the_event_data_is_malformed(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      newBackendWithWebSockets(harness)
        .withBackendCapability(BackendCapability.SMART_NOTIFICATIONS)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      webSocketServerEU.getConnections().get(0).sendMessage("{\"event\": [\"QualityGateChanged\"], \"data\": {\"message\": 0}}");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow()).isEmpty());
    }

    @SonarLintTest
    void should_not_forward_to_client_if_the_message_is_missing(SonarLintTestHarness harness) {
      should_not_forward_to_client(harness, "{\"event\": [\"QualityGateChanged\"], \"data\": {\"link\": \"lnk\", \"project\": \"projectKey\", \"date\": " +
        "\"2023-07-19T15:08:01+0000\"}}");
    }

    @SonarLintTest
    void should_not_forward_to_client_if_the_link_is_missing(SonarLintTestHarness harness) {
      should_not_forward_to_client(harness, "{\"event\": [\"QualityGateChanged\"], \"data\": {\"message\": \"msg\", \"project\": \"projectKey\", \"date\": " +
        "\"2023-07-19T15:08:01+0000\"}}");
    }

    @SonarLintTest
    void should_not_forward_to_client_if_the_project_is_missing(SonarLintTestHarness harness) {
      should_not_forward_to_client(harness, "{\"event\": [\"QualityGateChanged\"], \"data\": {\"message\": \"msg\", \"link\": \"lnk\", \"date\": " +
        "\"2023-07-19T15:08:01+0000\"}}");
    }

    @SonarLintTest
    void should_not_forward_to_client_if_the_date_is_missing(SonarLintTestHarness harness) {
      should_not_forward_to_client(harness, "{\"event\": [\"QualityGateChanged\"], \"data\": {\"message\": \"msg\", \"link\": \"lnk\", \"project\": " +
        "\"projectKey\"}}");
    }

    void should_not_forward_to_client(SonarLintTestHarness harness, String payload) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      newBackendWithWebSockets(harness)
        .withBackendCapability(BackendCapability.SMART_NOTIFICATIONS)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      webSocketServerEU.getConnections().get(0).sendMessage(payload);

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow()).isEmpty());
    }
  }

  @Nested
  class WhenReceivingIssueChangedEvent {
    @SonarLintTest
    void should_change_issue_status(SonarLintTestHarness harness) {
      var serverIssue = aServerIssue("myIssueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
        .withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", storage -> storage
          .withProject("projectKey", project -> project.withMainBranch("master", branch -> branch.withIssue(serverIssue))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      var issueStorage = backend.getIssueStorageService().connection("connectionId").project("projectKey").findings();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getIssue("myIssueKey").isResolved()).isFalse());

      webSocketServerEU.getConnections().get(0).sendMessage(
        """
          {
            "event": "IssueChanged",
            "data": {
              "projectKey": "projectKey",
              "issues": [
                {
                  "issueKey": "myIssueKey",
                  "branchName": "master"
                }
              ],
              "resolved": true
            }
          }""");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getIssue("myIssueKey").isResolved()).isTrue());
    }

    @SonarLintTest
    void should_not_change_issue_if_the_event_data_is_malformed(SonarLintTestHarness harness) {
      var serverIssue = aServerIssue("myIssueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
        .withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", storage -> storage
          .withProject("projectKey", project -> project.withMainBranch("master", branch -> branch.withIssue(serverIssue))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      var issueStorage = backend.getIssueStorageService().connection("connectionId").project("projectKey").findings();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getIssue("myIssueKey").isResolved()).isFalse());

      webSocketServerEU.getConnections().get(0).sendMessage(
        """
          {
            "event": "IssueChanged",
            "data": {
              "projectKey": "projectKey",
              "invalid": [
                {
                  "issueKey": myIssueKey,
                  "branchName": "master"
                }
              ],
              "resolved": true
            }
          }""");

      await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(issueStorage.getIssue("myIssueKey").isResolved()).isFalse());
    }

    @SonarLintTest
    void should_change_issue_if_the_issue_key_is_missing(SonarLintTestHarness harness) {
      var serverIssue = aServerIssue("myIssueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
        .withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", storage -> storage
          .withProject("projectKey", project -> project.withMainBranch("master", branch -> branch.withIssue(serverIssue))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      var issueStorage = backend.getIssueStorageService().connection("connectionId").project("projectKey").findings();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getIssue("myIssueKey").isResolved()).isFalse());

      webSocketServerEU.getConnections().get(0).sendMessage(
        """
          {
            "event": "IssueChanged",
            "data": {
              "projectKey": "projectKey",
              "invalid": [
                {
                  "branchName": "master"
                }
              ],
              "resolved": true
            }
          }""");

      await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(issueStorage.getIssue("myIssueKey").isResolved()).isFalse());
    }

    @SonarLintTest
    void should_not_change_issue_if_the_resolution_is_missing(SonarLintTestHarness harness) {
      var serverIssue = aServerIssue("myIssueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
        .withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", storage -> storage
          .withProject("projectKey", project -> project.withMainBranch("master", branch -> branch.withIssue(serverIssue))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      var issueStorage = backend.getIssueStorageService().connection("connectionId").project("projectKey").findings();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getIssue("myIssueKey").isResolved()).isFalse());

      webSocketServerEU.getConnections().get(0).sendMessage(
        """
          {
            "event": "IssueChanged",
            "data": {
              "projectKey": "projectKey",
              "invalid": [
                {
                  "issueKey": myIssueKey,
                  "branchName": "master"
                }
              ]
            }
          }""");

      await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(issueStorage.getIssue("myIssueKey").isResolved()).isFalse());
    }

    @SonarLintTest
    void should_not_change_issue_if_the_project_is_missing(SonarLintTestHarness harness) {
      var serverIssue = aServerIssue("myIssueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
        .withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", storage -> storage
          .withProject("projectKey", project -> project.withMainBranch("master", branch -> branch.withIssue(serverIssue))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      var issueStorage = backend.getIssueStorageService().connection("connectionId").project("projectKey").findings();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getIssue("myIssueKey").isResolved()).isFalse());

      webSocketServerEU.getConnections().get(0).sendMessage(
        """
          {
            "event": "IssueChanged",
            "data": {
              "invalid": [
                {
                  "issueKey": myIssueKey,
                  "branchName": "master"
                }
              ],
              "resolved": true
            }
          }""");

      await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(issueStorage.getIssue("myIssueKey").isResolved()).isFalse());
    }
  }

  @Nested
  class WhenReceivingTaintVulnerabilityRaisedEvent {
    @SonarLintTest
    void should_create_taint_vulnerability(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", storage -> storage
          .withProject("projectKey"))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      var issueStorage = backend.getIssueStorageService().connection("connectionId").project("projectKey").findings();

      webSocketServerEU.getConnections().get(0).sendMessage(
        """
          {
            "event": "TaintVulnerabilityRaised",
            "data": {
              "key": "taintKey",
              "projectKey": "projectKey",
              "branch": "branch",
              "creationDate": 123456789,
              "ruleKey": "javasecurity:S123",
              "severity": "MAJOR",
              "type": "VULNERABILITY",
              "mainLocation": {
                "filePath": "functions/taint.js",
                "message": "blah blah",
                "textRange": {
                  "startLine": 17,
                  "startLineOffset": 10,
                  "endLine": 3,
                  "endLineOffset": 2,
                  "hash": "hash"
                }
              },
              "flows": [
                {
                  "locations": [
                    {
                      "filePath": "functions/taint.js",
                      "message": "sink: tainted value is used to perform a security-sensitive operation",
                      "textRange": {
                        "startLine": 17,
                        "startLineOffset": 10,
                        "endLine": 3,
                        "endLineOffset": 2,
                        "hash": "hash1"
                      }
                    },
                    {
                      "filePath": "functions/taint2.js",
                      "message": "sink: tainted value is used to perform a security-sensitive operation",
                      "textRange": {
                        "startLine": 18,
                        "startLineOffset": 11,
                        "endLine": 4,
                        "endLineOffset": 3,
                        "hash": "hash2"
                      }
                    }
                  ]
                }
              ]
            }
          }""");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.containsIssue("taintKey")).isTrue());
    }

    @SonarLintTest
    void should_not_create_taint_vulnerability_event_data_is_malformed(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", storage -> storage
          .withProject("projectKey"))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      var issueStorage = backend.getIssueStorageService().connection("connectionId").project("projectKey").findings();

      webSocketServerEU.getConnections().get(0).sendMessage(
        """
          {
            "event": "TaintVulnerabilityRaised",
            "data": {
              "invalidKey": "taintKey",
              "projectKey": "projectKey",
              "branch": "branch",
              "creationDate": 123456789,
              "ruleKey": "javasecurity:S123",
              "severity": "MAJOR",
              "type": "VULNERABILITY",
              "mainLocation": {
                "filePath": "functions/taint.js",
                "message": "blah blah",
                "textRange": {
                  "startLine": 17,
                  "startLineOffset": 10,
                  "endLine": 3,
                  "endLineOffset": 2,
                  "hash": "hash"
                }
              },
              "flows": [
                {
                  "locations": [
                    {
                      "filePath": "functions/taint.js",
                      "message": "sink: tainted value is used to perform a security-sensitive operation",
                      "textRange": {
                        "startLine": 17,
                        "startLineOffset": 10,
                        "endLine": 3,
                        "endLineOffset": 2,
                        "hash": "hash1"
                      }
                    },
                    {
                      "filePath": "functions/taint2.js",
                      "message": "sink: tainted value is used to perform a security-sensitive operation",
                      "textRange": {
                        "startLine": 18,
                        "startLineOffset": 11,
                        "endLine": 4,
                        "endLineOffset": 3,
                        "hash": "hash2"
                      }
                    }
                  ]
                }
              ]
            }
          }""");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.containsIssue("taintKey")).isFalse());
    }
  }

  @Nested
  class WhenReceivingTaintVulnerabilityClosedEvent {
    @SonarLintTest
    void should_remove_taint_vulnerability(SonarLintTestHarness harness) {
      var serverTaintIssue = aServerTaintIssue("taintKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
        .withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", storage -> storage
          .withProject("projectKey", project -> project.withMainBranch(branch -> branch.withTaintIssue(serverTaintIssue))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      var issueStorage = backend.getIssueStorageService().connection("connectionId").project("projectKey").findings();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.containsIssue("taintKey")).isTrue());

      webSocketServerEU.getConnections().get(0).sendMessage(
        """
          {
            "event": "TaintVulnerabilityClosed",
            "data": {
              "projectKey": "projectKey",
              "key": "taintKey"
            }
          }""");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.containsIssue("taintKey")).isFalse());
    }

    @SonarLintTest
    void should_not_remove_taint_vulnerability_event_data_is_malformed(SonarLintTestHarness harness) {
      var serverTaintIssue = aServerTaintIssue("taintKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
        .withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", storage -> storage
          .withProject("projectKey", project -> project.withMainBranch(branch -> branch.withTaintIssue(serverTaintIssue))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      var issueStorage = backend.getIssueStorageService().connection("connectionId").project("projectKey").findings();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.containsIssue("taintKey")).isTrue());

      webSocketServerEU.getConnections().get(0).sendMessage(
        """
          {
            "event": "TaintVulnerabilityClosed",
            "data": {
              "projectKey": "projectKey",
              "taintKey": "taintKey"
            }
          }""");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.containsIssue("taintKey")).isTrue());
    }
  }

  @Nested
  class WhenReceivingSecurityHotspotChangedEvent {
    @SonarLintTest
    void should_update_security_hotspot(SonarLintTestHarness harness) {
      var serverHotspot = aServerHotspot("hotspotKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
        .withIntroductionDate(Instant.EPOCH.plusSeconds(1));
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", storage -> storage
          .withProject("projectKey", project -> project.withMainBranch(branch -> branch.withHotspot(serverHotspot))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      var issueStorage = backend.getIssueStorageService().connection("connectionId").project("projectKey").findings();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getHotspot("hotspotKey").getStatus().isResolved()).isFalse());

      webSocketServerEU.getConnections().get(0).sendMessage(
        """
          {
            "event": "SecurityHotspotChanged",
            "data": {
              "key": "hotspotKey",
              "projectKey": "projectKey",
              "updateDate": 1685007187000,
              "status": "REVIEWED",
              "assignee": "assigneeEmail",
              "resolution": "SAFE",
              "filePath": "/project/path/to/file"
            }
          }""");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getHotspot("hotspotKey").getStatus().isResolved()).isTrue());
    }

    @SonarLintTest
    void should_not_update_security_hotspot_if_event_data_is_malformed(SonarLintTestHarness harness) {
      var serverHotspot = aServerHotspot("hotspotKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
        .withIntroductionDate(Instant.EPOCH.plusSeconds(1));
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", storage -> storage
          .withProject("projectKey", project -> project.withMainBranch(branch -> branch.withHotspot(serverHotspot))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      var issueStorage = backend.getIssueStorageService().connection("connectionId").project("projectKey").findings();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getHotspot("hotspotKey").getStatus().isResolved()).isFalse());

      webSocketServerEU.getConnections().get(0).sendMessage(
        """
          {
            "event": "SecurityHotspotChanged",
            "data": {
              "key": "hotspotKey",
              "projectKey": "projectKey",
              "updateDate": 1685007187000,
              "status": "REVIEWED",
              "assignee": "assigneeEmail",
              "resolution": "SAFE",
              "filePath": ""
            }
          }""");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getHotspot("hotspotKey").getStatus().isResolved()).isFalse());
    }
  }

  @Nested
  class WhenReceivingSecurityHotspotRaisedEvent {
    @SonarLintTest
    void should_create_new_security_hotspot(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", storage -> storage
          .withProject("projectKey"))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      var issueStorage = backend.getIssueStorageService().connection("connectionId").project("projectKey").findings();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getHotspot("hotspotKey")).isNull());

      webSocketServerEU.getConnections().get(0).sendMessage(
        """
          {
            "event": "SecurityHotspotRaised",
            "data": {
              "status": "REVIEWED",
              "resolution": "FIXED",
              "vulnerabilityProbability": "MEDIUM",
              "creationDate": 1685006550000,
              "mainLocation": {
                "filePath": "src/main/java/org/example/Main.java",
                "message": "Make sure that using this pseudorandom number generator is safe here.",
                "textRange": {
                  "startLine": 12,
                  "startLineOffset": 29,
                  "endLine": 12,
                  "endLineOffset": 36,
                  "hash": "43b5c9175984c071f30b873fdce0a000"
                }
              },
              "ruleKey": "java:S2245",
              "key": "hotspotKey",
              "projectKey": "projectKey",
              "branch": "some-branch"
          }}""");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
        assertThat(issueStorage.getHotspot("hotspotKey")).isNotNull();
        assertThat(issueStorage.getHotspot("hotspotKey").getStatus()).isEqualTo(HotspotReviewStatus.FIXED);
      });
    }

    @SonarLintTest
    void should_not_create_security_hotspot_if_event_data_is_malformed(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", storage -> storage
          .withProject("projectKey"))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      var issueStorage = backend.getIssueStorageService().connection("connectionId").project("projectKey").findings();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getHotspot("hotspotKey")).isNull());

      webSocketServerEU.getConnections().get(0).sendMessage(
        """
          {
            "event": "SecurityHotspotRaised",
            "data": {
              "status": "TO_REVIEW",
              "vulnerabilityProbability": "MMMMMMM",
              "creationDate": 1685006550000,
              "mainLocation": {
                "filePath": "src/main/java/org/example/Main.java",
                "message": "Make sure that using this pseudorandom number generator is safe here.",
                "textRange": {
                  "startLine": 12,
                  "startLineOffset": 29,
                  "endLine": 12,
                  "endLineOffset": 36,
                  "hash": "43b5c9175984c071f30b873fdce0a000"
                }
              },
              "ruleKey": "java:S2245",
              "key": "hotspotKey",
              "projectKey": "projectKey",
              "branch": "some-branch"
          }}""");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getHotspot("hotspotKey")).isNull());
    }
  }

  @Nested
  class WhenReceivingSecurityHotspotClosedEvent {
    @SonarLintTest
    void should_remove_security_hotspot(SonarLintTestHarness harness) {
      var serverHotspot = aServerHotspot("hotspotKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
        .withIntroductionDate(Instant.EPOCH.plusSeconds(1));
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", storage -> storage
          .withProject("projectKey", project -> project.withMainBranch(branch -> branch.withHotspot(serverHotspot))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      var issueStorage = backend.getIssueStorageService().connection("connectionId").project("projectKey").findings();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getHotspot("hotspotKey")).isNotNull());

      webSocketServerEU.getConnections().get(0).sendMessage(
        """
          {
            "event": "SecurityHotspotClosed",
            "data": {
              "key": "hotspotKey",
              "projectKey": "projectKey",
              "filePath": "/project/path/to/file"
            }
          }""");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getHotspot("hotspotKey")).isNull());
    }

    @SonarLintTest
    void should_not_remove_security_hotspot(SonarLintTestHarness harness) {
      var serverHotspot = aServerHotspot("hotspotKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
        .withIntroductionDate(Instant.EPOCH.plusSeconds(1));
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      var backend = newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", storage -> storage
          .withProject("projectKey", project -> project.withMainBranch(branch -> branch.withHotspot(serverHotspot))))
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      var issueStorage = backend.getIssueStorageService().connection("connectionId").project("projectKey").findings();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getHotspot("hotspotKey")).isNotNull());

      webSocketServerEU.getConnections().get(0).sendMessage(
        """
          {
            "event": "SecurityHotspotClosed",
            "data": {
              "wrongKey": "hotspotKey",
              "projectKey": "projectKey",
              "filePath": "/project/path/to/file"
            }
          }""");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(issueStorage.getHotspot("hotspotKey")).isNotNull());
    }
  }

  @Nested
  class WhenReceivingUnexpectedEvents {
    @SonarLintTest
    void should_ignore_if_the_event_type_is_unknown(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      webSocketServerEU.getConnections().get(0).sendMessage("{\"event\": \"UnknownEvent\", \"data\": {\"message\": \"msg\"}}");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow()).isEmpty());
    }

    @SonarLintTest
    void should_ignore_if_the_event_is_malformed(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      await().atMost(Duration.ofSeconds(2)).until(() -> !webSocketServerEU.getConnections().isEmpty());

      webSocketServerEU.getConnections().get(0).sendMessage("{\"event\": \"Malformed");

      await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow()).isEmpty());
    }

    @SonarLintTest
    void should_not_forward_to_client_duplicated_event(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      webSocketServerEU.getConnections().get(0).sendMessage(
        "{\"event\": \"QualityGateChanged\", \"data\": {\"message\": \"msg\", \"link\": \"lnk\", \"project\": \"projectKey\", \"date\": " +
          "\"2023-07-19T15:08:01+0000\"}}");
      webSocketServerEU.getConnections().get(0).sendMessage(
        "{\"event\": \"QualityGateChanged\", \"data\": {\"message\": \"msg\", \"link\": \"lnk\", \"project\": \"projectKey\", \"date\": " +
          "\"2023-07-19T15:08:01+0000\"}}");

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(client.getSmartNotificationsToShow())
        .extracting(ShowSmartNotificationParams::getScopeIds, ShowSmartNotificationParams::getCategory,
          ShowSmartNotificationParams::getLink, ShowSmartNotificationParams::getText,
          ShowSmartNotificationParams::getConnectionId)
        .containsExactly(tuple(Set.of("configScope"), "QUALITY_GATE", "lnk", "msg", "connectionId")));
    }
  }

  @Nested
  class WhenWebSocketClosed {
    @SonarLintTest
    void should_refresh_connection_if_closed_by_server(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      webSocketServerEU.getConnections().get(0).close();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .contains(tuple(false, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").build()),
          tuple(true, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").build())));
    }
    @SonarLintTest
    void should_send_one_subscribe_message_per_project_key_when_reopening_connection(SonarLintTestHarness harness) {
      var client = harness.newFakeClient()
        .withToken("connectionId", "token")
        .build();
      newBackendWithWebSockets(harness)
        .withSonarCloudConnectionAndNotifications("connectionId", "orgKey", null)
        .withBoundConfigScope("configScope", "connectionId", "projectKey")
        .withBoundConfigScope("configScope2", "connectionId", "projectKey")
        .start(client);
      awaitUntilFirstWebSocketSubscribedTo("projectKey");

      webSocketServerEU.getConnections().get(0).close();

      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(webSocketServerEU.getConnections())
        .extracting(WebSocketConnection::isOpened, WebSocketConnection::getReceivedMessages)
        .contains(tuple(false, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").build()),
          tuple(true, webSocketPayloadBuilder().subscribeWithProjectKey("projectKey").build())));
    }
  }

  public SonarLintBackendFixture.SonarLintBackendBuilder newBackendWithWebSockets(SonarLintTestHarness harness) {
    return harness.newBackend()
      .withBackendCapability(BackendCapability.SERVER_SENT_EVENTS)
      .withSonarQubeCloudEuRegionWebSocketUri(webSocketServerEU.getUrl())
      .withSonarQubeCloudUsRegionWebSocketUri(webSocketServerUS.getUrl());
  }

  public static class WebSocketPayloadBuilder {

    private final List<String> payload;

    public static WebSocketPayloadBuilder webSocketPayloadBuilder() {
      return new WebSocketPayloadBuilder();
    }

    private WebSocketPayloadBuilder() {
      payload = new ArrayList<>();
    }

    public WebSocketPayloadBuilder subscribeWithProjectKey(String... projectKey) {
      Arrays.stream(projectKey).forEach(key -> {
        subscribeToProjectFilterWithProjectKey(key);
        subscribeToProjectUserFilterWithProjectKey(key);
      });
      return this;
    }

    public void subscribeToProjectFilterWithProjectKey(String projectKey) {
      payload.add("{\"action\":\"subscribe\"," +
        "\"events\":[\"IssueChanged\",\"QualityGateChanged\",\"SecurityHotspotChanged\",\"SecurityHotspotClosed\"," +
        "\"SecurityHotspotRaised\",\"TaintVulnerabilityClosed\",\"TaintVulnerabilityRaised\"]," +
        "\"filterType\":\"PROJECT\"," +
        "\"project\":\"" + projectKey + "\"}");
    }

    public void subscribeToProjectUserFilterWithProjectKey(String projectKey) {
      payload.add("{\"action\":\"subscribe\"," +
        "\"events\":[\"MyNewIssues\"]," +
        "\"filterType\":\"PROJECT_USER\"," +
        "\"project\":\"" + projectKey + "\"}");
    }

    public WebSocketPayloadBuilder unsubscribeWithProjectKey(String... projectKey) {
      Arrays.stream(projectKey).forEach(key -> {
        unsubscribeToProjectFilterWithProjectKey(key);
        unsubscribeToProjectUserFilterWithProjectKey(key);
      });
      return this;
    }

    public void unsubscribeToProjectFilterWithProjectKey(String projectKey) {
      payload.add("{\"action\":\"unsubscribe\"," +
        "\"events\":[\"IssueChanged\",\"QualityGateChanged\",\"SecurityHotspotChanged\",\"SecurityHotspotClosed\"," +
        "\"SecurityHotspotRaised\",\"TaintVulnerabilityClosed\",\"TaintVulnerabilityRaised\"]," +
        "\"filterType\":\"PROJECT\"," +
        "\"project\":\"" + projectKey + "\"}");
    }

    public void unsubscribeToProjectUserFilterWithProjectKey(String projectKey) {
      payload.add("{\"action\":\"unsubscribe\"," +
        "\"events\":[\"MyNewIssues\"]," +
        "\"filterType\":\"PROJECT_USER\"," +
        "\"project\":\"" + projectKey + "\"}");
    }

    public List<String> build() {
      return payload;
    }

  }

  private void awaitUntilFirstWebSocketSubscribedTo(String... projectKey) {
    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
      assertThat(webSocketServerEU.getConnections()).hasSize(1)
        .first()
        .extracting(WebSocketConnection::getReceivedMessages)
        .isEqualTo(webSocketPayloadBuilder().subscribeWithProjectKey(projectKey).build());
    });
  }

}
