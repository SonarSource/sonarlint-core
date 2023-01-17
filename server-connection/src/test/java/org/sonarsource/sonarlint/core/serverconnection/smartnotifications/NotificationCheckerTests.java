/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.smartnotifications;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import mockwebserver3.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationCheckerTests {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

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

  private NotificationChecker underTest;

  @BeforeEach
  void setUp() {
    underTest = new NotificationChecker(mockServer.serverApiHelper());
  }

  @Test
  void testSuccess() {
    var timestamp = ZonedDateTime.of(2017, 6, 4, 20, 0, 0, 0, ZoneOffset.ofHours(0));
    var expectedUrl = "/api/developers/search_events?projects=myproject&from=2017-06-04T20%3A00%3A00%2B0000";
    mockServer.addStringResponse(expectedUrl, VALID_RESPONSE);

    var notifications = underTest.request(Collections.singletonMap("myproject", timestamp));

    assertThat(notifications).hasSize(2);
    assertThat(notifications.get(0).category()).isEqualTo("QUALITY_GATE");
    assertThat(notifications.get(0).message()).isEqualTo("Quality Gate is Red (was Orange)");
    assertThat(notifications.get(0).link()).isEqualTo("https://sonarcloud.io/dashboard?id=myproject");
    assertThat(notifications.get(0).projectKey()).isEqualTo("myproject");
    assertThat(notifications.get(0).time()).isEqualTo(ZonedDateTime.of(2017, 7, 17, 9, 55, 26, 0, ZoneOffset.ofHours(2)));

  }

  @Test
  void testFailParsing() {
    var timestamp = ZonedDateTime.of(2017, 6, 4, 20, 0, 0, 0, ZoneOffset.ofHours(0));
    var expectedUrl = "/api/developers/search_events?projects=myproject&from=2017-06-04T20%3A00%3A00%2B0000";
    mockServer.addStringResponse(expectedUrl, INVALID_RESPONSE);

    var notifications = underTest.request(Collections.singletonMap("myproject", timestamp));
    assertThat(notifications).isEmpty();
  }

  @Test
  void testIsNotSupported() {
    assertThat(underTest.isSupported()).isFalse();
  }

  @Test
  void testIsSupported() {
    var expectedUrl = "/api/developers/search_events?projects=&from=";

    mockServer.addResponse(expectedUrl, new MockResponse());

    assertThat(underTest.isSupported()).isTrue();
  }

  @Test
  void testFailCode() {
    var timestamp = ZonedDateTime.of(2017, 6, 4, 20, 0, 0, 0, ZoneOffset.ofHours(0));
    var expectedUrl = "/api/developers/search_events?projects=myproject&from=2017-06-04T20%3A00%3A00%2B0000";

    mockServer.addResponse(expectedUrl, new MockResponse().setResponseCode(500).setBody("failed"));

    var notifications = underTest.request(Collections.singletonMap("myproject", timestamp));
    assertThat(notifications).isEmpty();
  }
}
