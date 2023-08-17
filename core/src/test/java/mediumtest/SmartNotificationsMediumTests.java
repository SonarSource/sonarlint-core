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
package mediumtest;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import testutils.MockWebServerExtensionWithProtobuf;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SmartNotificationsMediumTests {

  private static final ZonedDateTime STORED_DATE = ZonedDateTime.now().minusHours(1);
  private static final String PROJECT_KEY = "projectKey";
  private static final String PROJECT_KEY_2 = "projectKey2";
  private static final String PROJECT_KEY_3 = "projectKey3";
  private static final String CONNECTION_ID = "connectionId";
  private static final String CONNECTION_ID_2 = "connectionId2";
  private static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(DATETIME_FORMAT);
  private String oldSonarCloudUrl;
  private static final String EVENT_PROJECT_1 = "{\"events\": [" +
    "{\"message\": \"msg1\"," +
    "\"link\": \"lnk\"," +
    "\"project\": \"" + PROJECT_KEY + "\"," +
    "\"date\": \"2022-01-01T08:00:00+0000\"," +
    "\"category\": \"category\"}]}";

  private static final String QG_EVENT_PROJECT_1 = "{\"events\": [" +
    "{\"message\": \"msg1\"," +
    "\"link\": \"lnk\"," +
    "\"project\": \"" + PROJECT_KEY + "\"," +
    "\"date\": \"2022-01-01T08:00:00+0000\"," +
    "\"category\": \"QUALITY_GATE\"}]}";
  private static final String EVENT_PROJECT_2 = "{\"events\": [" +
    "{\"message\": \"msg2\"," +
    "\"link\": \"lnk\"," +
    "\"project\": \"" + PROJECT_KEY_2 + "\"," +
    "\"date\": \"2022-01-01T08:00:00+0000\"," +
    "\"category\": \"category\"}]}";
  private static final String TWO_EVENTS_P1_P3 = "{\"events\": [" +
    "{\"message\": \"msg2\"," +
    "\"link\": \"lnk\"," +
    "\"project\": \"" + PROJECT_KEY + "\"," +
    "\"date\": \"2022-01-01T08:00:00+0000\"," +
    "\"category\": \"category\"" +
    "},{" +
    "\"message\": \"msg3\"," +
    "\"link\": \"lnk\"," +
    "\"project\": \"" + PROJECT_KEY_3 + "\"," +
    "\"date\": \"2022-01-01T08:00:00+0000\"," +
    "\"category\": \"category\"}]}";
  @RegisterExtension
  private final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();
  private SonarLintBackendImpl backend;

  @BeforeEach
  void prepare() {
    oldSonarCloudUrl = System.getProperty("sonarlint.internal.sonarcloud.url");
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (oldSonarCloudUrl == null) {
      System.clearProperty("sonarlint.internal.sonarcloud.url");
    } else {
      System.setProperty("sonarlint.internal.sonarcloud.url", oldSonarCloudUrl);
    }
  }

  @Test
  void it_should_send_notification_for_two_config_scope_with_same_binding() {
    var fakeClient = newFakeClient().build();
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "&from=" +
      UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)), EVENT_PROJECT_1);

    backend = newBackend()
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage ->
        storage.withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID_2, mockWebServerExtension.endpointParams().getBaseUrl())
      .withBoundConfigScope("scopeId", CONNECTION_ID, PROJECT_KEY)
      .withBoundConfigScope("scopeId2", CONNECTION_ID, PROJECT_KEY)
      .withSmartNotifications()
      .build(fakeClient);

    await().atMost(3, TimeUnit.SECONDS).until(() -> !fakeClient.getSmartNotificationsToShow().isEmpty());

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).hasSize(1);
    assertThat(notificationsResult.get(0).getScopeIds()).hasSize(2).containsExactlyInAnyOrder("scopeId", "scopeId2");
  }

  @Test
  void it_should_send_notification_for_different_bindings() {
    var fakeClient = newFakeClient().build();
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY_2 + "&from=" +
      UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)), EVENT_PROJECT_2);
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "," + PROJECT_KEY_3 + "&from=" +
        UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)) + "," + UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)),
      TWO_EVENTS_P1_P3);

    backend = newBackend()
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage ->
        storage.withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE))
            .withProject(PROJECT_KEY_3, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID_2, mockWebServerExtension.endpointParams().getBaseUrl(), storage ->
        storage.withProject(PROJECT_KEY_2, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withBoundConfigScope("scopeId", CONNECTION_ID, PROJECT_KEY)
      .withBoundConfigScope("scopeId2", CONNECTION_ID, PROJECT_KEY)
      .withBoundConfigScope("scopeId3", CONNECTION_ID_2, PROJECT_KEY_2)
      .withBoundConfigScope("scopeId4", CONNECTION_ID, PROJECT_KEY_3)
      .withSmartNotifications()
      .build(fakeClient);

    await().atMost(3, TimeUnit.SECONDS).until(() -> fakeClient.getSmartNotificationsToShow().size() == 3);

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).hasSize(3);
    assertThat(notificationsResult).extracting(ShowSmartNotificationParams::getScopeIds)
      .haveExactly(1, new Condition<>(s -> s.size() == 2, "Size of 2"))
      .haveExactly(1, new Condition<>(s -> s.containsAll(Set.of("scopeId2", "scopeId")), "Contains scopeId2 and scopeId"));
    assertThat(notificationsResult).extracting(ShowSmartNotificationParams::getScopeIds)
      .haveExactly(2, new Condition<>(s -> s.size() == 1, "Size of 1"))
      .haveExactly(1, new Condition<>(s -> s.contains("scopeId4"), "Contains scopeId4"))
      .haveExactly(1, new Condition<>(s -> s.contains("scopeId3"), "Contains scopeId3"));
  }

  @Test
  void it_should_not_send_notification_with_unbound_config_scope() {
    var fakeClient = newFakeClient().build();
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "&from=" +
      UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)), EVENT_PROJECT_1);

    backend = newBackend()
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage ->
        storage.withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withUnboundConfigScope("scopeId")
      .withBoundConfigScope("scopeId2", CONNECTION_ID, PROJECT_KEY)
      .withSmartNotifications()
      .build(fakeClient);

    await().atMost(3, TimeUnit.SECONDS).until(() -> !fakeClient.getSmartNotificationsToShow().isEmpty());

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).hasSize(1);
    assertThat(notificationsResult.get(0).getScopeIds()).hasSize(1).contains("scopeId2");
  }

  @Test
  void it_should_send_notification_after_adding_removing_binding() {
    var fakeClient = newFakeClient().build();
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "&from=" +
      UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)), EVENT_PROJECT_1);

    backend = newBackend()
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage ->
        storage.withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withBoundConfigScope("scopeId", CONNECTION_ID, PROJECT_KEY)
      .withSmartNotifications()
      .build(fakeClient);

    backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams("scopeId"));

    backend.getConfigurationService()
      .didAddConfigurationScopes(
        new DidAddConfigurationScopesParams(List.of(
          new ConfigurationScopeDto("scopeId", null, true, "sonarlint-core",
            new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY, false)))));

    await().atMost(3, TimeUnit.SECONDS).until(() -> !fakeClient.getSmartNotificationsToShow().isEmpty());

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).hasSize(1);
    assertThat(notificationsResult.get(0).getScopeIds()).hasSize(1).contains("scopeId");
  }

  @Test
  void it_should_send_notification_after_adding_removing_connection() {
    var fakeClient = newFakeClient().build();
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "&from=" +
      UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)), EVENT_PROJECT_1);

    backend = newBackend()
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage ->
        storage.withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withBoundConfigScope("scopeId", CONNECTION_ID, PROJECT_KEY)
      .withSmartNotifications()
      .build(fakeClient);

    backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(List.of(), List.of()));

    var sonarQubeDto = new SonarQubeConnectionConfigurationDto(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), false);
    backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(List.of(sonarQubeDto), List.of()));

    await().atMost(3, TimeUnit.SECONDS).until(() -> !fakeClient.getSmartNotificationsToShow().isEmpty());

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).hasSize(1);
    assertThat(notificationsResult.get(0).getScopeIds()).hasSize(1).contains("scopeId");
  }

  @Test
  void it_should_not_send_notification_handled_by_sonarcloud_websocket() {
    var fakeClient = newFakeClient().build();
    System.setProperty("sonarlint.internal.sonarcloud.url", mockWebServerExtension.endpointParams().getBaseUrl());
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "&from=" +
      UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)), QG_EVENT_PROJECT_1);

    backend = newBackend()
      .withSonarCloudConnectionAndNotifications(CONNECTION_ID, "myOrg", storage ->
        storage.withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withBoundConfigScope("scopeId", CONNECTION_ID, PROJECT_KEY)
      .withSmartNotifications()
      .build(fakeClient);

    await().atMost(3, TimeUnit.SECONDS).until(() -> fakeClient.getSmartNotificationsToShow().isEmpty());

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).isEmpty();
  }

  @Test
  void it_should_send_notification_not_yet_handled_by_sonarcloud_websocket() {
    var fakeClient = newFakeClient().build();
    System.setProperty("sonarlint.internal.sonarcloud.url", mockWebServerExtension.endpointParams().getBaseUrl());
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "&from=" +
      UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)), EVENT_PROJECT_1);

    backend = newBackend()
      .withSonarCloudConnectionAndNotifications(CONNECTION_ID, "myOrg", storage ->
        storage.withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withBoundConfigScope("scopeId", CONNECTION_ID, PROJECT_KEY)
      .withSmartNotifications()
      .build(fakeClient);

    await().atMost(3, TimeUnit.SECONDS).until(() -> !fakeClient.getSmartNotificationsToShow().isEmpty());

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).hasSize(1);
    assertThat(notificationsResult.get(0).getScopeIds()).hasSize(1).contains("scopeId");
  }

  @Test
  void it_should_send_sonarqube_notification() {
    var fakeClient = newFakeClient().build();
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addStringResponse("/api/developers/search_events?projects=" + PROJECT_KEY + "&from=" +
      UrlUtils.urlEncode(STORED_DATE.format(TIME_FORMATTER)), EVENT_PROJECT_1);

    backend = newBackend()
      .withSonarQubeConnectionAndNotifications(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage ->
        storage.withProject(PROJECT_KEY, project -> project.withLastSmartNotificationPoll(STORED_DATE)))
      .withBoundConfigScope("scopeId", CONNECTION_ID, PROJECT_KEY)
      .withSmartNotifications()
      .build(fakeClient);

    await().atMost(3, TimeUnit.SECONDS).until(() -> !fakeClient.getSmartNotificationsToShow().isEmpty());

    var notificationsResult = fakeClient.getSmartNotificationsToShow();
    assertThat(notificationsResult).hasSize(1);
    assertThat(notificationsResult.get(0).getScopeIds()).hasSize(1).contains("scopeId");
  }
}
