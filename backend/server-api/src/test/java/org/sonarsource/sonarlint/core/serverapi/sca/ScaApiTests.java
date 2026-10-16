/*
 * SonarLint Core - Server API
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.serverapi.sca;

import com.google.gson.JsonParseException;
import io.sentry.Sentry;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.exception.InvalidServerItemException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class ScaApiTests {
  private MockedStatic<Sentry> sentry;

  @BeforeEach
  void mockSentry() {
    var logger = Sentry.logger();
    sentry = mockStatic(Sentry.class);
    sentry.when(Sentry::logger).thenReturn(logger);
  }

  @AfterEach
  void closeSentry() {
    sentry.close();
  }

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private static final String EMPTY_ISSUES_RELEASES_JSON = """
    {
      "issuesReleases": [],
      "page": {
        "pageIndex": 1,
        "pageSize": 100,
        "total": 0
      }
    }
    """;

  private ScaApi scaApi;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void should_recover_individual_json_failures_on_both_routes(boolean cloud) {
    var prefix = cloud ? "" : "/api/v2";
    scaApi = new ScaApi(cloud ? mockServer.serverApiHelper("org") : mockServer.serverApiHelper());
    var first = UUID.randomUUID();
    var last = UUID.randomUUID();
    mockServer.addStringResponse(prefix + "/sca/issues-releases?projectKey=project&branchKey=main&pageSize=500&pageIndex=1",
      "{\"issuesReleases\":[{\"key\":\"" + first + "\"},{\"key\":\"bad-uuid\"},null,42,[],{\"release\":42},{\"key\":\"" + last + "\"}],\"page\":{\"total\":7}}");

    var result = scaApi.getIssuesReleases("project", "main", new SonarLintCancelMonitor());

    assertThat(result.issuesReleases()).extracting(GetIssuesReleasesResponse.IssuesRelease::key).containsExactly(first, last);
    assertThat(result.page().total()).isEqualTo(2);
    var captor = ArgumentCaptor.forClass(Throwable.class);
    sentry.verify(() -> Sentry.captureException(captor.capture()), times(5));
    assertThat(captor.getAllValues()).allSatisfy(error -> assertThat(error).isInstanceOf(InvalidServerItemException.class));
    assertThat(captor.getAllValues()).anySatisfy(error -> assertThat(error).hasCauseInstanceOf(JsonParseException.class));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void should_continue_after_rejected_page_without_total(boolean cloud) {
    var prefix = cloud ? "" : "/api/v2";
    scaApi = new ScaApi(cloud ? mockServer.serverApiHelper("org") : mockServer.serverApiHelper());
    var key = UUID.randomUUID();
    var url = prefix + "/sca/issues-releases?projectKey=project&branchKey=main&pageSize=500&pageIndex=";
    mockServer.addStringResponse(url + "1", "{\"issuesReleases\":[{\"key\":\"bad\"}],\"page\":{}}");
    mockServer.addStringResponse(url + "2", "{\"issuesReleases\":[{\"key\":\"" + key + "\"}],\"page\":{}}");
    mockServer.addStringResponse(url + "3", EMPTY_ISSUES_RELEASES_JSON);

    assertThat(scaApi.getIssuesReleases("project", "main", new SonarLintCancelMonitor()).issuesReleases())
      .extracting(GetIssuesReleasesResponse.IssuesRelease::key).containsExactly(key);
    sentry.verify(() -> Sentry.captureException(any(Throwable.class)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid json", "null", "[]", "{}", "{\"issuesReleases\":[],\"page\":null}",
    "{\"issuesReleases\":{},\"page\":{}}", "{\"issuesReleases\":[],\"page\":{\"total\":\"bad\"}}"})
  void should_propagate_invalid_documents_and_envelopes(String body) {
    scaApi = new ScaApi(mockServer.serverApiHelper());
    mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=project&branchKey=main&pageSize=500&pageIndex=1", body);

    var monitor = new SonarLintCancelMonitor();
    assertThatThrownBy(() -> scaApi.getIssuesReleases("project", "main", monitor)).isInstanceOf(JsonParseException.class);
    sentry.verify(() -> Sentry.captureException(any(Throwable.class)), never());
  }

  @Nested
  class SonarQubeServer {

    @BeforeEach
    void prepare() {
      scaApi = new ScaApi(mockServer.serverApiHelper());
    }

    @Test
    void should_get_issues_releases_with_empty_response() {
      mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=my-project&branchKey=main&pageSize=500&pageIndex=1", EMPTY_ISSUES_RELEASES_JSON);

      var response = scaApi.getIssuesReleases("my-project", "main", new SonarLintCancelMonitor());

      assertThat(response.issuesReleases()).isEmpty();
    }

    @Test
    void should_get_issues_releases_of_vulnerability_type() {
      var uuid = UUID.randomUUID();
      var jsonResponse = String.format("""
      {
        "issuesReleases": [
          {
            "key": "%s",
            "type": "VULNERABILITY",
            "severity": "HIGH",
            "quality": "MAINTAINABILITY",
            "vulnerabilityId": "CVE-2023-12345",
            "cvssScore": "7.5",
            "release": {
              "packageName": "com.example.vulnerable",
              "version": "1.0.0"
            },
            "transitions": ["CONFIRM", "REOPEN"]
          }
        ],
        "page": {
          "pageIndex": 1,
          "pageSize": 100,
          "total": 1
        }
      }
      """, uuid);
      mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=test-project&branchKey=feature%2Fmy-branch&pageSize=500&pageIndex=1", jsonResponse);

      var response = scaApi.getIssuesReleases("test-project", "feature/my-branch", new SonarLintCancelMonitor());

      assertThat(response.issuesReleases()).hasSize(1);
      var issueRelease = response.issuesReleases().get(0);
      assertThat(issueRelease.key()).isEqualTo(uuid);
      assertThat(issueRelease.type()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.Type.VULNERABILITY);
      assertThat(issueRelease.severity()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.Severity.HIGH);
      assertThat(issueRelease.quality()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.SoftwareQuality.MAINTAINABILITY);
      assertThat(issueRelease.vulnerabilityId()).isEqualTo("CVE-2023-12345");
      assertThat(issueRelease.cvssScore()).isEqualTo("7.5");
      assertThat(issueRelease.release().packageName()).isEqualTo("com.example.vulnerable");
      assertThat(issueRelease.release().version()).isEqualTo("1.0.0");
      assertThat(issueRelease.transitions()).containsExactly(
        GetIssuesReleasesResponse.IssuesRelease.Transition.CONFIRM,
        GetIssuesReleasesResponse.IssuesRelease.Transition.REOPEN);
    }

    @Test
    void should_get_issues_releases_of_prohibited_license_type() {
      var uuid = UUID.randomUUID();
      var jsonResponse = String.format("""
      {
        "issuesReleases": [
          {
            "key": "%s",
            "type": "PROHIBITED_LICENSE",
            "severity": "BLOCKER",
            "quality": "SECURITY",
            "vulnerabilityId": null,
            "cvssScore": null,
            "release": {
              "packageName": "com.example.prohibited",
              "version": "2.1.0"
            },
            "transitions": ["ACCEPT", "SAFE"]
          }
        ],
        "page": {
          "pageIndex": 1,
          "pageSize": 100,
          "total": 1
        }
      }
      """, uuid);
      mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=license-project&branchKey=develop&pageSize=500&pageIndex=1", jsonResponse);

      var response = scaApi.getIssuesReleases("license-project", "develop", new SonarLintCancelMonitor());

      assertThat(response.issuesReleases()).hasSize(1);
      var issueRelease = response.issuesReleases().get(0);
      assertThat(issueRelease.key()).isEqualTo(uuid);
      assertThat(issueRelease.type()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.Type.PROHIBITED_LICENSE);
      assertThat(issueRelease.severity()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.Severity.BLOCKER);
      assertThat(issueRelease.quality()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.SoftwareQuality.SECURITY);
      assertThat(issueRelease.vulnerabilityId()).isNull();
      assertThat(issueRelease.cvssScore()).isNull();
      assertThat(issueRelease.release().packageName()).isEqualTo("com.example.prohibited");
      assertThat(issueRelease.release().version()).isEqualTo("2.1.0");
      assertThat(issueRelease.transitions()).containsExactly(
        GetIssuesReleasesResponse.IssuesRelease.Transition.ACCEPT,
        GetIssuesReleasesResponse.IssuesRelease.Transition.SAFE);
    }

    @Test
    void should_get_issues_releases_of_malware_type() {
      var uuid = UUID.randomUUID();
      var jsonResponse = String.format("""
      {
        "issuesReleases": [
          {
            "key": "%s",
            "type": "MALWARE",
            "severity": "BLOCKER",
            "quality": "SECURITY",
            "status": "OPEN",
            "vulnerabilityId": "CVE-2024-99999",
            "cvssScore": "9.8",
            "release": {
              "packageName": "com.example.malware",
              "version": "3.0.0"
            },
            "transitions": ["CONFIRM"]
          }
        ],
        "page": {
          "pageIndex": 1,
          "pageSize": 100,
          "total": 1
        }
      }
      """, uuid);
      mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=malware-project&branchKey=master&pageSize=500&pageIndex=1", jsonResponse);

      var response = scaApi.getIssuesReleases("malware-project", "master", new SonarLintCancelMonitor());

      assertThat(response.issuesReleases()).hasSize(1);
      var issueRelease = response.issuesReleases().get(0);
      assertThat(issueRelease.key()).isEqualTo(uuid);
      assertThat(issueRelease.type()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.Type.MALWARE);
      assertThat(issueRelease.severity()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.Severity.BLOCKER);
      assertThat(issueRelease.quality()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.SoftwareQuality.SECURITY);
      assertThat(issueRelease.status()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.Status.OPEN);
      assertThat(issueRelease.vulnerabilityId()).isEqualTo("CVE-2024-99999");
      assertThat(issueRelease.cvssScore()).isEqualTo("9.8");
      assertThat(issueRelease.release().packageName()).isEqualTo("com.example.malware");
      assertThat(issueRelease.release().version()).isEqualTo("3.0.0");
      assertThat(issueRelease.transitions()).containsExactly(GetIssuesReleasesResponse.IssuesRelease.Transition.CONFIRM);
    }

    @Test
    void should_get_issues_releases_with_multiple_issues() {
      var uuid1 = UUID.randomUUID();
      var uuid2 = UUID.randomUUID();
      var jsonResponse = String.format("""
      {
        "issuesReleases": [
          {
            "key": "%s",
            "type": "VULNERABILITY",
            "severity": "MEDIUM",
            "quality": "RELIABILITY",
            "vulnerabilityId": "CVE-2023-12345",
            "cvssScore": "7.5",
            "release": {
              "packageName": "com.example.first",
              "version": "1.0.0"
            },
            "transitions": ["CONFIRM"]
          },
          {
            "key": "%s",
            "type": "PROHIBITED_LICENSE",
            "severity": "LOW",
            "quality": "MAINTAINABILITY",
            "vulnerabilityId": null,
            "cvssScore": null,
            "release": {
              "packageName": "com.example.second",
              "version": "2.0.0"
            },
            "transitions": ["ACCEPT", "SAFE", "FIXED"]
          }
        ],
        "page": {
          "pageIndex": 1,
          "pageSize": 100,
          "total": 2
        }
      }
      """, uuid1, uuid2);
      mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=multi-project&branchKey=master&pageSize=500&pageIndex=1", jsonResponse);

      var response = scaApi.getIssuesReleases("multi-project", "master", new SonarLintCancelMonitor());

      assertThat(response.issuesReleases()).hasSize(2);

      var firstIssue = response.issuesReleases().get(0);
      assertThat(firstIssue.key()).isEqualTo(uuid1);
      assertThat(firstIssue.type()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.Type.VULNERABILITY);
      assertThat(firstIssue.severity()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.Severity.MEDIUM);
      assertThat(firstIssue.quality()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.SoftwareQuality.RELIABILITY);
      assertThat(firstIssue.vulnerabilityId()).isEqualTo("CVE-2023-12345");
      assertThat(firstIssue.cvssScore()).isEqualTo("7.5");
      assertThat(firstIssue.release().packageName()).isEqualTo("com.example.first");
      assertThat(firstIssue.release().version()).isEqualTo("1.0.0");
      assertThat(firstIssue.transitions()).containsExactly(GetIssuesReleasesResponse.IssuesRelease.Transition.CONFIRM);

      var secondIssue = response.issuesReleases().get(1);
      assertThat(secondIssue.key()).isEqualTo(uuid2);
      assertThat(secondIssue.type()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.Type.PROHIBITED_LICENSE);
      assertThat(secondIssue.severity()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.Severity.LOW);
      assertThat(secondIssue.quality()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.SoftwareQuality.MAINTAINABILITY);
      assertThat(secondIssue.vulnerabilityId()).isNull();
      assertThat(secondIssue.cvssScore()).isNull();
      assertThat(secondIssue.release().packageName()).isEqualTo("com.example.second");
      assertThat(secondIssue.release().version()).isEqualTo("2.0.0");
      assertThat(secondIssue.transitions()).containsExactly(
        GetIssuesReleasesResponse.IssuesRelease.Transition.ACCEPT,
        GetIssuesReleasesResponse.IssuesRelease.Transition.SAFE,
        GetIssuesReleasesResponse.IssuesRelease.Transition.FIXED);
    }

    @Test
    void should_handle_special_characters_in_project_key_and_branch_name() {
      mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=my%3Aproject%2Bkey&branchKey=feature%2Fmy-branch%3Atest&pageSize=500&pageIndex=1", EMPTY_ISSUES_RELEASES_JSON);

      var response = scaApi.getIssuesReleases("my:project+key", "feature/my-branch:test", new SonarLintCancelMonitor());

      assertThat(response.issuesReleases()).isEmpty();
    }

    @Test
    void should_handle_malformed_json_response() {
      mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=test&branchKey=main&pageSize=500&pageIndex=1", "invalid json");

      assertThatThrownBy(() -> scaApi.getIssuesReleases("test", "main", new SonarLintCancelMonitor()))
        .isInstanceOf(Exception.class);
    }

    @Test
    void should_handle_empty_transitions() {
      var uuid = UUID.randomUUID();
      var jsonResponse = String.format("""
      {
        "issuesReleases": [
          {
            "key": "%s",
            "type": "VULNERABILITY",
            "severity": "INFO",
            "release": {
              "packageName": "com.example.minimal",
              "version": "0.1.0"
            },
            "transitions": []
          }
        ],
        "page": {
          "pageIndex": 1,
          "pageSize": 100,
          "total": 1
        }
      }
      """, uuid);
      mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=minimal-project&branchKey=main&pageSize=500&pageIndex=1", jsonResponse);

      var response = scaApi.getIssuesReleases("minimal-project", "main", new SonarLintCancelMonitor());

      assertThat(response.issuesReleases()).hasSize(1);
      var issueRelease = response.issuesReleases().get(0);
      assertThat(issueRelease.key()).isEqualTo(uuid);
      assertThat(issueRelease.type()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.Type.VULNERABILITY);
      assertThat(issueRelease.severity()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.Severity.INFO);
      assertThat(issueRelease.transitions()).isEmpty();
    }
  }

  @Nested
  class SonarQubeCloud {

    @BeforeEach
    void prepare() {
      scaApi = new ScaApi(mockServer.serverApiHelper("orgKey"));
    }

    @Test
    void should_get_issues_releases_with_empty_response() {
      mockServer.addStringResponse("/sca/issues-releases?projectKey=my-project&branchKey=main&pageSize=500&pageIndex=1", EMPTY_ISSUES_RELEASES_JSON);

      var response = scaApi.getIssuesReleases("my-project", "main", new SonarLintCancelMonitor());

      assertThat(response.issuesReleases()).isEmpty();
    }

    @Test
    void should_get_issues_releases_of_vulnerability_type() {
      var uuid = UUID.randomUUID();
      var jsonResponse = String.format("""
      {
        "issuesReleases": [
          {
            "key": "%s",
            "type": "VULNERABILITY",
            "severity": "HIGH",
            "quality": "MAINTAINABILITY",
            "vulnerabilityId": "CVE-2023-12345",
            "cvssScore": "7.5",
            "release": {
              "packageName": "com.example.vulnerable",
              "version": "1.0.0"
            },
            "transitions": ["CONFIRM", "REOPEN"]
          }
        ],
        "page": {
          "pageIndex": 1,
          "pageSize": 100,
          "total": 1
        }
      }
      """, uuid);
      mockServer.addStringResponse("/sca/issues-releases?projectKey=test-project&branchKey=feature%2Fmy-branch&pageSize=500&pageIndex=1", jsonResponse);

      var response = scaApi.getIssuesReleases("test-project", "feature/my-branch", new SonarLintCancelMonitor());

      assertThat(response.issuesReleases()).hasSize(1);
      var issueRelease = response.issuesReleases().get(0);
      assertThat(issueRelease.key()).isEqualTo(uuid);
      assertThat(issueRelease.type()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.Type.VULNERABILITY);
      assertThat(issueRelease.severity()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.Severity.HIGH);
      assertThat(issueRelease.quality()).isEqualTo(GetIssuesReleasesResponse.IssuesRelease.SoftwareQuality.MAINTAINABILITY);
      assertThat(issueRelease.vulnerabilityId()).isEqualTo("CVE-2023-12345");
      assertThat(issueRelease.cvssScore()).isEqualTo("7.5");
      assertThat(issueRelease.release().packageName()).isEqualTo("com.example.vulnerable");
      assertThat(issueRelease.release().version()).isEqualTo("1.0.0");
      assertThat(issueRelease.transitions()).containsExactly(
        GetIssuesReleasesResponse.IssuesRelease.Transition.CONFIRM,
        GetIssuesReleasesResponse.IssuesRelease.Transition.REOPEN);
    }
  }
}
