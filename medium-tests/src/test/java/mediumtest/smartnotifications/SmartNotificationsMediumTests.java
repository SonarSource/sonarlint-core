/*
 * SonarLint Core - Medium Tests
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
package mediumtest.smartnotifications;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mockwebserver3.MockResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import testutils.MockWebServerExtensionWithProtobuf;
import testutils.websockets.WebSocketServer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

class SmartNotificationsMediumTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final ZonedDateTime STORED_DATE = ZonedDateTime.now().minusHours(1);
  private static final String PROJECT_KEY = "projectKey";
  private static final String PROJECT_KEY_2 = "projectKey2";
  private static final String PROJECT_KEY_3 = "projectKey3";
  private static final String PROJECT_KEY_4 = "projectKey4";
  private static final String CONNECTION_ID = "connectionId";
  private static final String CONNECTION_ID_2 = "connectionId2";
  private static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(DATETIME_FORMAT);
  private static final String EVENT_PROJECT_1 = "{\"events\": [" +
    "{\"message\": \"msg1\"," +
    "\"link\": \"lnk\"," +
    "\"project\": \"" + PROJECT_KEY + "\"," +
    "\"date\": \"2022-01-01T08:00:00+0000\"," +
    "\"category\": \"category\"}]}";
  private static final String EVENT_PROJECT_P2 = "{\"events\": [" +
    "{\"message\": \"msg2\"," +
    "\"link\": \"lnk\"," +
    "\"project\": \"" + PROJECT_KEY_2 + "\"," +
    "\"date\": \"2022-01-01T08:00:00+0000\"," +
    "\"category\": \"category\"}]}";
  private static final String THREE_EVENTS_P1_P3_P4 = "{\"events\": [" +
    "{\"message\": \"msg1\"," +
    "\"link\": \"lnk\"," +
    "\"project\": \"" + PROJECT_KEY + "\"," +
    "\"date\": \"2022-01-01T08:00:00+0000\"," +
    "\"category\": \"category\"},"
    + "{\"message\": \"msg3\"," +
    "\"link\": \"lnk\"," +
    "\"project\": \"" + PROJECT_KEY_3 + "\"," +
    "\"date\": \"2022-01-01T08:00:00+0000\"," +
    "\"category\": \"category\"},"
    + "{\"message\": \"msg4\"," +
    "\"link\": \"lnk\"," +
    "\"project\": \"" + PROJECT_KEY_4 + "\"," +
    "\"date\": \"2022-01-01T08:00:00+0000\"," +
    "\"category\": \"category\"}"
    + "]}";
  private static final String NEW_ISSUES_EVENT = "{\n" +
    "  \"event\": \"MyNewIssues\", \n" +
    "  \"data\": {\n" +
    "    \"message\": \"You have 3 new issues on project u0027SonarLint Coreu0027 on pull request u0027657u0027\",\n" +
    "    \"link\": \"link\",\n" +
    "    \"project\": \"" + PROJECT_KEY + "\",\n" +
    "    \"date\": \"2023-07-19T15:08:01+0000\"\n" +
    "  }\n" +
    "}";
  @RegisterExtension
  private final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();
  @RegisterExtension
  private final MockWebServerExtensionWithProtobuf mockWebServerExtension2 = new MockWebServerExtensionWithProtobuf();

  private SonarLintTestRpcServer backend;
  private WebSocketServer webSocketServer;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
    if (webSocketServer != null) {
      webSocketServer.stop();
    }
  }

  @Test
  void it_should_send_notification_for_two_config_scope_with_same_binding() {
    var fakeClient = newFakeClient().build();
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "&from=" +
      UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)), EVENT_PROJECT_1);

    backend = newBackend()
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(),
        storage -> storage.withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID_2, mockWebServerExtension.endpointParams().getBaseUrl())
      .withBoundConfigScope("scopeId", CONNECTION_ID, PROJECT_KEY)
      .withBoundConfigScope("scopeId2", CONNECTION_ID, PROJECT_KEY)
      .withSmartNotifications()
      .build(fakeClient);

    await().atMost(3, SECONDS).until(() -> !fakeClient.getSmartNotificationsToShow().isEmpty());

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).hasSize(1);
    assertThat(notificationsResult.element().getScopeIds()).hasSize(2).containsExactlyInAnyOrder("scopeId", "scopeId2");
  }

  @Test
  void it_should_send_notification_for_two_config_scope_with_inherited_binding() {
    var fakeClient = newFakeClient().build();
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "&from=" +
      UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)), EVENT_PROJECT_1);

    backend = newBackend()
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(),
        storage -> storage.withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID_2, mockWebServerExtension.endpointParams().getBaseUrl())
      .withBoundConfigScope("parentScopeId", CONNECTION_ID, PROJECT_KEY)
      .withChildConfigScope("childScopeId", "parentScopeId")
      .withSmartNotifications()
      .build(fakeClient);

    await().atMost(3, SECONDS).until(() -> !fakeClient.getSmartNotificationsToShow().isEmpty());

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).hasSize(1);
    assertThat(notificationsResult.element().getScopeIds()).hasSize(2).containsExactlyInAnyOrder("parentScopeId", "childScopeId");
  }

  @Test
  void it_should_send_notification_for_different_bindings() {
    var fakeClient = newFakeClient().build();
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension2.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    var timestamp = UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "," + PROJECT_KEY_3 + "," + PROJECT_KEY_4 + "&from=" +
      timestamp + "," + timestamp + "," + timestamp, THREE_EVENTS_P1_P3_P4);
    mockWebServerExtension2.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY_2 + "," + PROJECT_KEY_4 + "&from=" +
      timestamp + "," + timestamp, EVENT_PROJECT_P2);

    backend = newBackend()
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(),
        storage -> storage
          .withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE))
          .withProject(PROJECT_KEY_3, project -> project.withLastSmartNotificationPoll(STORED_DATE))
          .withProject(PROJECT_KEY_4, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID_2, mockWebServerExtension2.endpointParams().getBaseUrl(),
        storage -> storage
          .withProject(PROJECT_KEY_2, project -> project.withLastSmartNotificationPoll(STORED_DATE))
          .withProject(PROJECT_KEY_4, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withBoundConfigScope("scopeId", CONNECTION_ID, PROJECT_KEY)
      .withBoundConfigScope("scopeId2", CONNECTION_ID, PROJECT_KEY)
      .withBoundConfigScope("scopeId3", CONNECTION_ID_2, PROJECT_KEY_2)
      .withBoundConfigScope("scopeId4", CONNECTION_ID, PROJECT_KEY_3)
      // We have two bindings with the same project key, but on different connection, so it might be considered as different projects
      .withBoundConfigScope("scopeId5", CONNECTION_ID, PROJECT_KEY_4)
      .withBoundConfigScope("scopeId6", CONNECTION_ID_2, PROJECT_KEY_4)
      .withSmartNotifications()
      .build(fakeClient);

    await().atMost(3, SECONDS).untilAsserted(() -> assertThat(fakeClient.getSmartNotificationsToShow()).hasSize(4));

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult)
      .extracting(ShowSmartNotificationParams::getConnectionId, ShowSmartNotificationParams::getScopeIds, ShowSmartNotificationParams::getText)
      .containsExactlyInAnyOrder(
        tuple("connectionId", Set.of("scopeId", "scopeId2"), "msg1"),
        tuple("connectionId", Set.of("scopeId4"), "msg3"),
        tuple("connectionId", Set.of("scopeId5"), "msg4"),
        tuple("connectionId2", Set.of("scopeId3"), "msg2")
      );
  }

  @Test
  void it_should_not_send_notification_with_unbound_config_scope() {
    var fakeClient = newFakeClient().build();
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "&from=" +
      UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)), EVENT_PROJECT_1);

    backend = newBackend()
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(),
        storage -> storage.withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withUnboundConfigScope("scopeId")
      .withBoundConfigScope("scopeId2", CONNECTION_ID, PROJECT_KEY)
      .withSmartNotifications()
      .build(fakeClient);

    await().atMost(3, SECONDS).until(() -> !fakeClient.getSmartNotificationsToShow().isEmpty());

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).hasSize(1);
    assertThat(notificationsResult.element().getScopeIds()).hasSize(1).contains("scopeId2");
  }

  @Test
  void it_should_send_notification_after_adding_removing_binding() {
    var fakeClient = newFakeClient().build();
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "&from=" +
      UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)), EVENT_PROJECT_1);

    backend = newBackend()
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(),
        storage -> storage.withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withBoundConfigScope("scopeId", CONNECTION_ID, PROJECT_KEY)
      .withSmartNotifications()
      .build(fakeClient);

    backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams("scopeId"));

    backend.getConfigurationService()
      .didAddConfigurationScopes(
        new DidAddConfigurationScopesParams(List.of(
          new ConfigurationScopeDto("scopeId", null, true, "sonarlint-core",
            new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY, false)))));

    await().atMost(3, SECONDS).until(() -> !fakeClient.getSmartNotificationsToShow().isEmpty());

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).hasSize(1);
    assertThat(notificationsResult.element().getScopeIds()).hasSize(1).contains("scopeId");
  }

  @Test
  void it_should_send_notification_handled_by_sonarcloud_websocket_as_fallback() {
    var fakeClient = newFakeClient().build();
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "&from=" +
      UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)), EVENT_PROJECT_1);

    backend = newBackend()
      .withSonarCloudUrl(mockWebServerExtension.endpointParams().getBaseUrl())
      .withSonarCloudConnectionAndNotifications(CONNECTION_ID, "myOrg", storage -> storage.withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withBoundConfigScope("scopeId", CONNECTION_ID, PROJECT_KEY)
      .withSmartNotifications()
      .build(fakeClient);

    await().atMost(3, SECONDS).until(() -> !fakeClient.getSmartNotificationsToShow().isEmpty());

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).hasSize(1);
    assertThat(notificationsResult.element().getScopeIds()).hasSize(1).contains("scopeId");
  }

  @Test
  void it_should_skip_polling_notifications_when_sonarcloud_websocket_opened() {
    webSocketServer = new WebSocketServer();
    webSocketServer.start();
    var fakeClient = newFakeClient().withToken(CONNECTION_ID, "token")
      .build();
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "&from=" +
      UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)), EVENT_PROJECT_1);

    backend = newBackend()
      .withSonarCloudUrl(mockWebServerExtension.endpointParams().getBaseUrl())
      .withSonarCloudWebSocketsUrl(webSocketServer.getUrl())
      .withSonarCloudConnectionAndNotifications(CONNECTION_ID, "myOrg", storage -> storage.withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withBoundConfigScope("scopeId", CONNECTION_ID, PROJECT_KEY)
      .withSmartNotifications()
      .withServerSentEventsEnabled()
      .build(fakeClient);

    await().atMost(2, SECONDS).until(() -> webSocketServer.getConnections().size() == 1);

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).isEmpty();

    webSocketServer.getConnections().get(0).sendMessage(NEW_ISSUES_EVENT);

    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(fakeClient.getSmartNotificationsToShow()).isNotEmpty());

    notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).hasSize(1);
    assertThat(notificationsResult.element().getScopeIds()).hasSize(1).contains("scopeId");
  }

  @Test
  void it_should_send_sonarqube_notification() {
    var fakeClient = newFakeClient().build();
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "&from=" +
      UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)), EVENT_PROJECT_1);

    backend = newBackend()
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(),
        storage -> storage.withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withBoundConfigScope("scopeId", CONNECTION_ID, PROJECT_KEY)
      .withSmartNotifications()
      .build(fakeClient);

    await().atMost(3, SECONDS).until(() -> !fakeClient.getSmartNotificationsToShow().isEmpty());

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).hasSize(1);
    assertThat(notificationsResult.element().getScopeIds()).hasSize(1).contains("scopeId");
  }
}
