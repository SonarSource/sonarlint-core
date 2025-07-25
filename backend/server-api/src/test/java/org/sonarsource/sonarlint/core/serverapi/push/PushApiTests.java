/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.push;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

class PushApiTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester(true);
  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private PushApi underTest;

  @BeforeEach
  void setUp() {
    underTest = new PushApi(mockServer.serverApiHelper());
  }

  @Test
  @Disabled("Settings will be supported later")
  void should_notify_setting_changed_event_for_simple_setting() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: SettingChanged
        data: {
          "projects": ["projectKey1", "projectKey2"],
          "key": "key1",
          "value": "value1"
        }
    
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    assertThat(receivedEvents)
      .extracting("projectKeys", "keyValues")
      .containsOnly(tuple(List.of("projectKey1", "projectKey2"), Map.of("key1", "value1")));
  }

  @Test
  @Disabled("Settings will be supported later")
  void should_notify_setting_changed_event_for_multi_values_setting() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: SettingChanged
        data: {
          "projects": ["projectKey1", "projectKey2"],
          "key": "key1",
          "values": ["value1","value2"]
        }
    
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    assertThat(receivedEvents)
      .extracting("projectKeys", "keyValues")
      .containsOnly(tuple(List.of("projectKey1", "projectKey2"), Map.of("key1", "value1,value2")));
  }

  @Test
  @Disabled("Settings will be supported later")
  void should_notify_setting_changed_event_for_field_values_setting() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: SettingChanged
        data: {
          "projects": ["projectKey1", "projectKey2"],
          "key": "key1",
          "fieldValues": [
            {
              "key2": "value2",
              "key3": "value3"
            },
            {
              "key4": "value4",
              "key5": "value5"
            }
          ]
        }
    
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    assertThat(receivedEvents)
      .extracting("projectKeys", "keyValues")
      .containsOnly(tuple(
        List.of("projectKey1", "projectKey2"),
        Map.of("key1", "1,2", "key1.1.key2", "value2", "key1.1.key3", "value3", "key1.2.key4", "value4", "key1.2.key5", "value5")));
  }

  @Test
  void should_notify_rule_set_changed_event_without_impacts() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: RuleSetChanged
        data: {\
          "projects": ["projectKey1", "projectKey2"],\
          "activatedRules": [{\
            "key": "java:S0000",\
            "severity": "MAJOR",\
            "params": [{\
              "key": "key1",\
              "value": "value1"\
            }]\
          }],\
          "deactivatedRules": ["java:S4321"]\
        }
        
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(receivedEvents)
      .extracting("projectKeys", "deactivatedRules")
      .containsOnly(tuple(List.of("projectKey1", "projectKey2"), List.of("java:S4321"))));
    assertThat(receivedEvents)
      .flatExtracting("activatedRules")
      .extracting("key", "severity")
      .containsOnly(tuple("java:S0000", IssueSeverity.MAJOR));
    assertThat(receivedEvents)
      .flatExtracting("activatedRules")
      .extracting("parameters")
      .containsOnly(Map.of("key1", "value1"));
  }

  @Test
  void should_notify_rule_set_changed_event() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: RuleSetChanged
        data: {\
          "projects": ["projectKey1", "projectKey2"],\
          "activatedRules": [{\
            "key": "java:S0000",\
            "severity": "MAJOR",\
            "params": [{\
              "key": "key1",\
              "value": "value1"\
            }],\
            "templateKey": "templateKey",\
            "impacts": [{\
              "softwareQuality": "SECURITY",\
              "severity": "HIGH"\
            }]\
          }],\
          "deactivatedRules": ["java:S4321"]\
        }
        
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(receivedEvents)
      .extracting("projectKeys", "deactivatedRules")
      .containsOnly(tuple(List.of("projectKey1", "projectKey2"), List.of("java:S4321"))));
    assertThat(receivedEvents)
      .flatExtracting("activatedRules")
      .extracting("key", "severity")
      .containsOnly(tuple("java:S0000", IssueSeverity.MAJOR));
    assertThat(receivedEvents)
      .flatExtracting("activatedRules")
      .extracting("parameters")
      .containsOnly(Map.of("key1", "value1"));
  }

  @Test
  void should_not_notify_while_event_is_incomplete() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: RuleSetChanged
        data: {\
          "projects": ["projectKey1", "projectKey2"],\
          "activatedRules":
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    assertThat(receivedEvents).isEmpty();
  }

  @Test
  void should_ignore_events_without_project_keys() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: RuleSetChanged
        data: {\
          "activatedRules": ["java:S1234"],\
          "deactivatedRules": ["java:S4321"],\
          "changedRules": [{\
            "key": "java:S0000",\
            "overriddenSeverity": "MAJOR",\
            "params": [{\
              "key": "key1",\
              "value": "value1"\
            }]\
          }]\
        }
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    assertThat(receivedEvents).isEmpty();
  }

  @Test
  void should_ignore_unknown_events() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: UnknownEvent
        data: "plop"
        
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    assertThat(receivedEvents).isEmpty();
  }

  @Test
  void should_ignore_ruleset_changed_events_with_invalid_json() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: RuleSetChanged
        data: {]
        
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    assertThat(receivedEvents).isEmpty();
  }

  @Test
  void should_ignore_setting_changed_events_with_invalid_json() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: SettingChanged
        data: {]
        
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    assertThat(receivedEvents).isEmpty();
  }

  @Test
  void should_ignore_invalid_setting_changed_events() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: SettingChanged
        data: {\
          "projects": ["projectKey1", "projectKey2"],\
          "key": "key1",\
          "value": "",\
          "values": [],\
          "fieldValues": []\
        }
        
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1,projectKey2&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1", "projectKey2")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    assertThat(receivedEvents).isEmpty();
  }

  @Test
  void should_notify_issue_changed_event_when_resolved_status_changed() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: IssueChanged
        data: {\
          "projectKey": "projectKey1",\
          "issues": [{\
            "issueKey": "key1",\
            "branchName": "master",\
            "impacts": [ { "softwareQuality": "MAINTAINABILITY", "severity": "HIGH" } ]\
          }],\
          "resolved": true\
        }
        
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(receivedEvents)
        .asInstanceOf(InstanceOfAssertFactories.list(IssueChangedEvent.class))
        .extracting(IssueChangedEvent::getResolved, IssueChangedEvent::getUserSeverity, IssueChangedEvent::getUserType)
        .containsOnly(tuple(true, null, null));

      assertThat(receivedEvents).isNotEmpty();
      assertThat(((IssueChangedEvent) receivedEvents.get(0)).getImpactedIssues()).hasSize(1);
      assertThat(((IssueChangedEvent) receivedEvents.get(0)).getImpactedIssues().get(0).getIssueKey()).isEqualTo("key1");
    });
  }

  @Test
  void should_notify_issue_changed_event_when_severity_changed() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: IssueChanged
        data: {\
          "projectKey": "projectKey1",\
          "issues": [{\
            "issueKey": "key1",\
            "branchName": "master"\
          }],\
          "userSeverity": "MAJOR"\
        }
        
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(receivedEvents)
        .asInstanceOf(InstanceOfAssertFactories.list(IssueChangedEvent.class))
        .extracting(IssueChangedEvent::getResolved, IssueChangedEvent::getUserSeverity, IssueChangedEvent::getUserType)
        .containsOnly(tuple(null, IssueSeverity.MAJOR, null));

      assertThat(receivedEvents).isNotEmpty();
      assertThat(((IssueChangedEvent) receivedEvents.get(0)).getImpactedIssues()).hasSize(1);
      assertThat(((IssueChangedEvent) receivedEvents.get(0)).getImpactedIssues().get(0).getIssueKey()).isEqualTo("key1");
    });
  }

  @Test
  void should_notify_issue_changed_event_when_type_changed() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: IssueChanged
        data: {\
          "projectKey": "projectKey1",\
          "issues": [{\
            "issueKey": "key1",\
            "branchName": "master"\
          }],\
          "userType": "BUG"\
        }
        
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(receivedEvents)
        .asInstanceOf(InstanceOfAssertFactories.list(IssueChangedEvent.class))
        .extracting(IssueChangedEvent::getResolved, IssueChangedEvent::getUserSeverity, IssueChangedEvent::getUserType)
        .containsOnly(tuple(null, null, RuleType.BUG));

      assertThat(receivedEvents).isNotEmpty();
      assertThat(((IssueChangedEvent) receivedEvents.get(0)).getImpactedIssues()).hasSize(1);
      assertThat(((IssueChangedEvent) receivedEvents.get(0)).getImpactedIssues().get(0).getIssueKey()).isEqualTo("key1");
    });
  }

  @Test
  void should_not_notify_issue_changed_event_when_no_change_is_present() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: IssueChangedEvent
        data: {\
          "projectKey": "projectKey1",\
          "issues": [{\
            "issueKey": "key1",\
            "branchName": "master"\
          }]\
        }
        
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    assertThat(receivedEvents).isEmpty();
  }

  @Test
  void should_notify_taint_vulnerability_raised_event() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: TaintVulnerabilityRaised
        data: {\
          "key": "taintKey",\
          "projectKey": "projectKey1",\
          "branch": "branch",\
          "creationDate": 123456789,\
          "ruleKey": "javasecurity:S123",\
          "severity": "MAJOR",\
          "type": "VULNERABILITY",\
          "mainLocation": {\
            "filePath": "functions/taint.js",\
            "message": "blah blah",\
            "textRange": {\
              "startLine": 17,\
              "startLineOffset": 10,\
              "endLine": 3,\
              "endLineOffset": 2,\
              "hash": "hash"\
            }\
          },\
          "flows": [{\
            "locations": [{\
              "filePath": "functions/taint.js",\
              "message": "sink: tainted value is used to perform a security-sensitive operation",\
              "textRange": {\
                "startLine": 17,\
                "startLineOffset": 10,\
                "endLine": 3,\
                "endLineOffset": 2,\
                "hash": "hash1"\
              }\
            },\
            {\
              "filePath": "functions/taint2.js",\
              "message": "sink: tainted value is used to perform a security-sensitive operation",\
              "textRange": {\
                "startLine": 18,\
                "startLineOffset": 11,\
                "endLine": 4,\
                "endLineOffset": 3,\
                "hash": "hash2"\
              }\
            }]\
          }]\
        }
        
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(receivedEvents)
      .extracting("key", "projectKey", "branchName", "creationDate", "ruleKey", "severity", "type")
      .containsOnly(tuple("taintKey", "projectKey1", "branch", Instant.parse("1970-01-02T10:17:36.789Z"), "javasecurity:S123", IssueSeverity.MAJOR, RuleType.VULNERABILITY)));
    assertThat(receivedEvents)
      .extracting("mainLocation")
      .extracting("filePath", "message", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "textRange.hash")
      .containsOnly(tuple(Path.of("functions/taint.js"), "blah blah", 17, 10, 3, 2, "hash"));
    assertThat(receivedEvents)
      .flatExtracting("flows")
      .flatExtracting("locations")
      .extracting("filePath", "message", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "textRange.hash")
      .containsOnly(
        tuple(Path.of("functions/taint.js"), "sink: tainted value is used to perform a security-sensitive operation", 17, 10, 3, 2, "hash1"),
        tuple(Path.of("functions/taint2.js"), "sink: tainted value is used to perform a security-sensitive operation", 18, 11, 4, 3, "hash2"));
  }

  @Test
  void should_notify_taint_vulnerability_raised_event_with_cct() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: TaintVulnerabilityRaised
        data: {\
          "key": "taintKey",\
          "projectKey": "projectKey1",\
          "branch": "branch",\
          "creationDate": 123456789,\
          "ruleKey": "javasecurity:S123",\
          "severity": "MAJOR",\
          "type": "VULNERABILITY",\
          "cleanCodeAttribute": "TRUSTWORTHY",\
          "impacts": [ { "softwareQuality": "SECURITY", "severity": "HIGH" } ],\
          "type": "VULNERABILITY",\
          "mainLocation": {\
            "filePath": "functions/taint.js",\
            "message": "blah blah",\
            "textRange": {\
              "startLine": 17,\
              "startLineOffset": 10,\
              "endLine": 3,\
              "endLineOffset": 2,\
              "hash": "hash"\
            }\
          },\
          "flows": [{\
            "locations": [{\
              "filePath": "functions/taint.js",\
              "message": "sink: tainted value is used to perform a security-sensitive operation",\
              "textRange": {\
                "startLine": 17,\
                "startLineOffset": 10,\
                "endLine": 3,\
                "endLineOffset": 2,\
                "hash": "hash1"\
              }\
            },\
            {\
              "filePath": "functions/taint2.js",\
              "message": "sink: tainted value is used to perform a security-sensitive operation",\
              "textRange": {\
                "startLine": 18,\
                "startLineOffset": 11,\
                "endLine": 4,\
                "endLineOffset": 3,\
                "hash": "hash2"\
              }\
            }]\
          }]\
        }
        
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(receivedEvents)
      .extracting("key", "projectKey", "branchName", "creationDate", "ruleKey", "severity", "type", "cleanCodeAttribute", "impacts")
      .containsOnly(tuple("taintKey", "projectKey1", "branch", Instant.parse("1970-01-02T10:17:36.789Z"), "javasecurity:S123", IssueSeverity.MAJOR, RuleType.VULNERABILITY, Optional.of(CleanCodeAttribute.TRUSTWORTHY), Map.of(SoftwareQuality.SECURITY, ImpactSeverity.HIGH))));
  }

  @Test
  void should_notify_taint_vulnerability_closed_event() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: TaintVulnerabilityClosed
        data: {\
          "projectKey": "projectKey1",\
          "key": "taintKey"\
        }
        
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(receivedEvents)
      .extracting("taintIssueKey")
      .containsOnly("taintKey"));
  }

  @Test
  void should_notify_issue_changed_event_when_software_impacts_changed() {
    var mockResponse = new MockResponse.Builder()
      .body("""
        event: IssueChanged
        data: {\
          "projectKey": "projectKey1",\
          "issues": [{\
            "issueKey": "key1",\
            "branchName": "master",\
            "impacts": [ { "softwareQuality": "MAINTAINABILITY", "severity": "HIGH" } ]\
          }],\
          "resolved": true\
        }
        
        """)
      .build();
    mockServer.addResponse("/api/push/sonarlint_events?projectKeys=projectKey1&languages=java,py", mockResponse);

    List<SonarServerEvent> receivedEvents = new CopyOnWriteArrayList<>();
    underTest.subscribe(new LinkedHashSet<>(List.of("projectKey1")), new LinkedHashSet<>(List.of(SonarLanguage.JAVA, SonarLanguage.PYTHON)), receivedEvents::add);

    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(receivedEvents)
        .asInstanceOf(InstanceOfAssertFactories.list(IssueChangedEvent.class))
        .extracting(IssueChangedEvent::getResolved, IssueChangedEvent::getUserSeverity, IssueChangedEvent::getUserType)
        .containsOnly(tuple(true, null, null));

      assertThat(receivedEvents).isNotEmpty();
      assertThat(((IssueChangedEvent) receivedEvents.get(0)).getImpactedIssues()).hasSize(1);
      assertThat(((IssueChangedEvent) receivedEvents.get(0)).getImpactedIssues().get(0).getIssueKey()).isEqualTo("key1");
      assertThat(((IssueChangedEvent) receivedEvents.get(0)).getImpactedIssues().get(0).getBranchName()).isEqualTo("master");
      assertThat(((IssueChangedEvent) receivedEvents.get(0)).getImpactedIssues().get(0).getImpacts()).isNotEmpty();
      assertThat(((IssueChangedEvent) receivedEvents.get(0)).getImpactedIssues().get(0).getImpacts()).containsKey(SoftwareQuality.MAINTAINABILITY);
      assertThat(((IssueChangedEvent) receivedEvents.get(0)).getImpactedIssues().get(0).getImpacts()).containsValue(ImpactSeverity.HIGH);
    });
  }
}
