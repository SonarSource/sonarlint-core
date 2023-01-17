/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.developers;

import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DevelopersApiTests {
  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private DevelopersApi underTest;

  @BeforeEach
  void setUp() {
    underTest = new DevelopersApi(mockServer.serverApiHelper());
  }

  @Test
  void should_consider_notifications_unsupported_if_endpoint_does_not_exist() {
    var supported = underTest.isSupported();

    assertThat(supported).isFalse();
  }

  @Test
  void should_consider_notifications_supported_if_endpoint_exists() {
    mockServer.addStringResponse("/api/developers/search_events?projects=&from=", "");

    var supported = underTest.isSupported();

    assertThat(supported).isTrue();
  }

  @Test
  void should_return_events_for_a_given_project_key() {
    mockServer.addStringResponse("/api/developers/search_events?projects=projectKey&from=2022-01-01T12%3A00%3A00%2B0000", "{\"events\": [" +
      "{" +
      "\"category\": \"cat\"," +
      "\"message\": \"msg\"," +
      "\"link\": \"lnk\"," +
      "\"project\": \"projectKey\"," +
      "\"date\": \"2022-01-01T08:00:00+0000\"" +
      "}" +
      "]" +
      "}");

    var events = underTest.getEvents(Map.of("projectKey", ZonedDateTime.parse("2022-01-01T12:00:00Z")));

    assertThat(events)
      .extracting("category", "message", "link", "projectKey", "time")
      .containsOnly(tuple("cat", "msg", "lnk", "projectKey", ZonedDateTime.parse("2022-01-01T08:00:00Z")));
  }

  @Test
  void should_return_no_event_if_a_field_is_missing_in_one_of_them() {
    mockServer.addStringResponse("/api/developers/search_events?projects=projectKey&from=2022-01-01T12%3A00%3A00%2B0000", "{\"events\": [" +
      "{" +
      "\"message\": \"msg\"," +
      "\"link\": \"lnk\"," +
      "\"project\": \"projectKey\"," +
      "\"date\": \"2022-01-01T08:00:00+0000\"" +
      "}" +
      "]" +
      "}");

    var events = underTest.getEvents(Map.of("projectKey", ZonedDateTime.parse("2022-01-01T12:00:00Z")));

    assertThat(events).isEmpty();
  }

  @Test
  void should_return_no_event_if_the_request_fails() {
    var events = underTest.getEvents(Map.of("projectKey", ZonedDateTime.parse("2022-01-01T12:00:00Z")));

    assertThat(events).isEmpty();
  }
}
