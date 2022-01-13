/*
 * SonarLint Server API
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.push;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import mockwebserver3.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PushApiTest {
  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private PushApi underTest;
  private final ClientLogOutput silentLogOutput = (message, level) -> {};

  @BeforeEach
  void setUp() {
    underTest = new PushApi(mockServer.serverApiHelper());
  }

  @Test
  @Disabled("Settings will be supported later")
  void should_notify_setting_changed_event_for_simple_setting() {
    var mockResponse = new MockResponse();
    mockResponse.setBody("event: SettingChanged\n" +
      "data: {" +
      "\"projects\": [\"projectKey1\", \"projectKey2\"]," +
      "\"key\": \"key1\"," +
      "\"value\": \"value1\"" +
      "}\n\n");
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<ServerEvent> receivedEvents = new ArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(Language.JAVA, Language.PYTHON)), receivedEvents::add, silentLogOutput);

    assertThat(receivedEvents)
      .extracting("projectKeys", "keyValues")
      .containsOnly(tuple(List.of("projectKey1", "projectKey2"), Map.of("key1", "value1")));
  }

  @Test
  @Disabled("Settings will be supported later")
  void should_notify_setting_changed_event_for_multi_values_setting() {
    var mockResponse = new MockResponse();
    mockResponse.setBody("event: SettingChanged\n" +
      "data: {" +
      "\"projects\": [\"projectKey1\", \"projectKey2\"]," +
      "\"key\": \"key1\"," +
      "\"values\": [\"value1\",\"value2\"]" +
      "}\n\n");
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<ServerEvent> receivedEvents = new ArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(Language.JAVA, Language.PYTHON)), receivedEvents::add, silentLogOutput);

    assertThat(receivedEvents)
      .extracting("projectKeys", "keyValues")
      .containsOnly(tuple(List.of("projectKey1", "projectKey2"), Map.of("key1", "value1,value2")));
  }

  @Test
  @Disabled("Settings will be supported later")
  void should_notify_setting_changed_event_for_field_values_setting() {
    var mockResponse = new MockResponse();
    mockResponse.setBody("event: SettingChanged\n" +
      "data: {" +
      "\"projects\": [\"projectKey1\", \"projectKey2\"]," +
      "\"key\": \"key1\"," +
      "\"fieldValues\": [{" +
      "\"key2\": \"value2\"," +
      "\"key3\": \"value3\"" +
      "}," +
      "{" +
      "\"key4\": \"value4\"," +
      "\"key5\": \"value5\"" +
      "}]" +
      "}\n\n");
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<ServerEvent> receivedEvents = new ArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(Language.JAVA, Language.PYTHON)), receivedEvents::add, silentLogOutput);

    assertThat(receivedEvents)
      .extracting("projectKeys", "keyValues")
      .containsOnly(tuple(
        List.of("projectKey1", "projectKey2"),
        Map.of("key1", "1,2", "key1.1.key2", "value2", "key1.1.key3", "value3", "key1.2.key4", "value4", "key1.2.key5", "value5")));
  }

  @Test
  void should_notify_rule_set_changed_event() {
    var mockResponse = new MockResponse();
    mockResponse.setBody("event: RuleSetChanged\n" +
      "data: {" +
      "\"projects\": [\"projectKey1\", \"projectKey2\"]," +
      "\"activatedRules\": [{" +
      "\"key\": \"java:S0000\"," +
      "\"severity\": \"MAJOR\"," +
      "\"params\": [{" +
      "\"key\": \"key1\"," +
      "\"value\": \"value1\"" +
      "}]" +
      "}]," +
      "\"deactivatedRules\": [\"java:S4321\"]" +
      "}\n\n");
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<ServerEvent> receivedEvents = new ArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(Language.JAVA, Language.PYTHON)), receivedEvents::add, silentLogOutput);

    assertThat(receivedEvents)
      .extracting("projectKeys", "deactivatedRules")
      .containsOnly(tuple(List.of("projectKey1", "projectKey2"), List.of("java:S4321")));
    assertThat(receivedEvents)
      .flatExtracting("activatedRules")
      .extracting("key", "severity")
      .containsOnly(tuple("java:S0000", "MAJOR"));
    assertThat(receivedEvents)
      .flatExtracting("activatedRules")
      .extracting("parameters")
      .containsOnly(Map.of("key1", "value1"));
  }

  @Test
  void should_not_notify_while_event_is_incomplete() {
    var mockResponse = new MockResponse();
    mockResponse.setBody("event: RuleSetChanged\n" +
      "data: {" +
      "\"projects\": [\"projectKey1\", \"projectKey2\"]," +
      "\"activatedRules\": ");
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<ServerEvent> receivedEvents = new ArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(Language.JAVA, Language.PYTHON)), receivedEvents::add, silentLogOutput);

    assertThat(receivedEvents).isEmpty();
  }

  @Test
  void should_ignore_events_without_project_keys() {
    var mockResponse = new MockResponse();
    mockResponse.setBody("event: RuleSetChanged\n" +
      "data: {" +
      "\"activatedRules\": [\"java:S1234\"]," +
      "\"deactivatedRules\": [\"java:S4321\"]," +
      "\"changedRules\": [{" +
      "\"key\": \"java:S0000\"," +
      "\"overriddenSeverity\": \"MAJOR\"," +
      "\"params\": [{" +
      "\"key\": \"key1\"," +
      "\"value\": \"value1\"" +
      "}]" +
      "}]" +
      "}\n\n");
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<ServerEvent> receivedEvents = new ArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(Language.JAVA, Language.PYTHON)), receivedEvents::add, silentLogOutput);

    assertThat(receivedEvents).isEmpty();
  }

  @Test
  void should_ignore_unknown_events() {
    var mockResponse = new MockResponse();
    mockResponse.setBody("event: UnknownEvent\n" +
      "data: \"plop\"\n\n");
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<ServerEvent> receivedEvents = new ArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(Language.JAVA, Language.PYTHON)), receivedEvents::add, silentLogOutput);

    assertThat(receivedEvents).isEmpty();
  }

  @Test
  void should_ignore_ruleset_changed_events_with_invalid_json() {
    var mockResponse = new MockResponse();
    mockResponse.setBody("event: RuleSetChanged\n" +
      "data: {]\n\n");
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<ServerEvent> receivedEvents = new ArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(Language.JAVA, Language.PYTHON)), receivedEvents::add, silentLogOutput);

    assertThat(receivedEvents).isEmpty();
  }

  @Test
  void should_ignore_setting_changed_events_with_invalid_json() {
    var mockResponse = new MockResponse();
    mockResponse.setBody("event: SettingChanged\n" +
      "data: {]\n\n");
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<ServerEvent> receivedEvents = new ArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(Language.JAVA, Language.PYTHON)), receivedEvents::add, silentLogOutput);

    assertThat(receivedEvents).isEmpty();
  }

  @Test
  void should_ignore_invalid_setting_changed_events() {
    var mockResponse = new MockResponse();
    mockResponse.setBody("event: SettingChanged\n" +
      "data: {" +
      "\"projects\": [\"projectKey1\", \"projectKey2\"]," +
      "\"key\": \"key1\"," +
      "\"value\": \"\"," +
      "\"values\": []," +
      "\"fieldValues\": []" +
      "}\n\n");
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<ServerEvent> receivedEvents = new ArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(Language.JAVA, Language.PYTHON)), receivedEvents::add, silentLogOutput);

    assertThat(receivedEvents).isEmpty();
  }
}
