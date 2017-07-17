/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.client.api.notifications.SonarQubeNotification;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.util.ws.WsResponse;

public class NotificationCheckerTest {
  private static final String VALID_RESPONSE = "{" +
    "\"events\": [" +
    "{" +
    "\"category\": \"QUALITY_GATE\"," +
    "\"message\": \"Quality Gate is Red (was Orange)\"," +
    "\"link\": \"https://sonarcloud.io/dashboard?id=myproject\"," +
    "\"project\": \"myproject\","
    + "\"date\":\"2017-07-17T09:55:26+0200\"" +
    "}," +
    "{" +
    "\"category\": \"NEW_ISSUES\"," +
    "\"message\": \"There are 15 new unresolved issues assigned to you\"," +
    "\"link\": \"https://sonarcloud.io/project/issues?asdw\"," +
    "\"project\": \"myproject\","
    + "\"date\":\"2017-07-17T09:55:26+0200\"" +
    "}" + "]" + "}";
  private static final String INVALID_RESPONSE = "{ \"invalid\" : \"invalid\" }";

  @Before
  public void setUp() {
  }

  @Test
  public void testSuccess() {
    ZonedDateTime timestamp = ZonedDateTime.of(2017, 06, 04, 20, 0, 0, 0, ZoneOffset.ofHours(0));
    String expectedUrl = "api/developers/search_events?projects=myproject&from=2017-06-04T20%3A00%3A00%2B0000";
    SonarLintWsClient client = WsClientTestUtils.createMockWithResponse(expectedUrl, VALID_RESPONSE);

    NotificationChecker checker = new NotificationChecker(client);
    List<SonarQubeNotification> notifications = checker.request(Collections.singletonMap("myproject", timestamp));

    assertThat(notifications.size()).isEqualTo(2);
    assertThat(notifications.get(0).category()).isEqualTo("QUALITY_GATE");
    assertThat(notifications.get(0).message()).isEqualTo("Quality Gate is Red (was Orange)");
    assertThat(notifications.get(0).link()).isEqualTo("https://sonarcloud.io/dashboard?id=myproject");
    assertThat(notifications.get(0).projectKey()).isEqualTo("myproject");
  }

  @Test
  public void testFailParsing() {
    ZonedDateTime timestamp = ZonedDateTime.of(2017, 06, 04, 20, 0, 0, 0, ZoneOffset.ofHours(0));
    String expectedUrl = "api/developers/search_events?projects=myproject&from=2017-06-04T20%3A00%3A00%2B0000";
    SonarLintWsClient client = WsClientTestUtils.createMockWithResponse(expectedUrl, INVALID_RESPONSE);

    NotificationChecker checker = new NotificationChecker(client);
    List<SonarQubeNotification> notifications = checker.request(Collections.singletonMap("myproject", timestamp));
    assertThat(notifications).isEmpty();
  }

  @Test
  public void testIsNotSupported() {
    String expectedUrl = "api/developers/search_events?projects=&from=";

    SonarLintWsClient client = WsClientTestUtils.createMock();

    WsResponse wsResponse = mock(WsResponse.class);
    when(client.rawGet(startsWith(expectedUrl))).thenReturn(wsResponse);
    when(wsResponse.isSuccessful()).thenReturn(false);

    NotificationChecker checker = new NotificationChecker(client);
    assertThat(checker.isSupported()).isFalse();
  }

  @Test
  public void testIsSupported() {
    String expectedUrl = "api/developers/search_events?projects=&from=";

    SonarLintWsClient client = WsClientTestUtils.createMock();

    WsResponse wsResponse = mock(WsResponse.class);
    when(client.rawGet(startsWith(expectedUrl))).thenReturn(wsResponse);
    when(wsResponse.isSuccessful()).thenReturn(true);

    NotificationChecker checker = new NotificationChecker(client);
    assertThat(checker.isSupported()).isTrue();
  }

  @Test
  public void testFailCode() {
    ZonedDateTime timestamp = ZonedDateTime.of(2017, 06, 04, 20, 0, 0, 0, ZoneOffset.ofHours(0));
    String expectedUrl = "api/developers/search_events?projects=myproject&from=2017-06-04T20%3A00%3A00%2B0000";
    SonarLintWsClient client = WsClientTestUtils.createMock();
    WsClientTestUtils.addFailedResponse(client, expectedUrl, 404, "failed");

    NotificationChecker checker = new NotificationChecker(client);
    List<SonarQubeNotification> notifications = checker.request(Collections.singletonMap("myproject", timestamp));
    assertThat(notifications).isEmpty();
  }
}
