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
package org.sonarsource.sonarlint.core.serverapi.sca;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.sca.GetIssuesReleasesResponse.IssuesRelease;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScaApiTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private ScaApi scaApi;

  @BeforeEach
  void prepare() {
    scaApi = new ScaApi(mockServer.serverApiHelper());
  }

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

  @Test
  void should_get_issues_releases_with_empty_response() {
    mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=my-project&branchName=main&pageSize=500&pageIndex=1", EMPTY_ISSUES_RELEASES_JSON);

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
    mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=test-project&branchName=feature%2Fmy-branch&pageSize=500&pageIndex=1", jsonResponse);

    var response = scaApi.getIssuesReleases("test-project", "feature/my-branch", new SonarLintCancelMonitor());

    assertThat(response.issuesReleases()).hasSize(1);
    var issueRelease = response.issuesReleases().get(0);
    assertThat(issueRelease.key()).isEqualTo(uuid);
    assertThat(issueRelease.type()).isEqualTo(IssuesRelease.Type.VULNERABILITY);
    assertThat(issueRelease.severity()).isEqualTo(IssuesRelease.Severity.HIGH);
    assertThat(issueRelease.release().packageName()).isEqualTo("com.example.vulnerable");
    assertThat(issueRelease.release().version()).isEqualTo("1.0.0");
    assertThat(issueRelease.transitions()).containsExactly(
      IssuesRelease.Transition.CONFIRM,
      IssuesRelease.Transition.REOPEN);
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
    mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=license-project&branchName=develop&pageSize=500&pageIndex=1", jsonResponse);

    var response = scaApi.getIssuesReleases("license-project", "develop", new SonarLintCancelMonitor());

    assertThat(response.issuesReleases()).hasSize(1);
    var issueRelease = response.issuesReleases().get(0);
    assertThat(issueRelease.key()).isEqualTo(uuid);
    assertThat(issueRelease.type()).isEqualTo(IssuesRelease.Type.PROHIBITED_LICENSE);
    assertThat(issueRelease.severity()).isEqualTo(IssuesRelease.Severity.BLOCKER);
    assertThat(issueRelease.release().packageName()).isEqualTo("com.example.prohibited");
    assertThat(issueRelease.release().version()).isEqualTo("2.1.0");
    assertThat(issueRelease.transitions()).containsExactly(
      IssuesRelease.Transition.ACCEPT,
      IssuesRelease.Transition.SAFE);
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
    mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=multi-project&branchName=master&pageSize=500&pageIndex=1", jsonResponse);

    var response = scaApi.getIssuesReleases("multi-project", "master", new SonarLintCancelMonitor());

    assertThat(response.issuesReleases()).hasSize(2);

    var firstIssue = response.issuesReleases().get(0);
    assertThat(firstIssue.key()).isEqualTo(uuid1);
    assertThat(firstIssue.type()).isEqualTo(IssuesRelease.Type.VULNERABILITY);
    assertThat(firstIssue.severity()).isEqualTo(IssuesRelease.Severity.MEDIUM);
    assertThat(firstIssue.release().packageName()).isEqualTo("com.example.first");
    assertThat(firstIssue.release().version()).isEqualTo("1.0.0");
    assertThat(firstIssue.transitions()).containsExactly(IssuesRelease.Transition.CONFIRM);

    var secondIssue = response.issuesReleases().get(1);
    assertThat(secondIssue.key()).isEqualTo(uuid2);
    assertThat(secondIssue.type()).isEqualTo(IssuesRelease.Type.PROHIBITED_LICENSE);
    assertThat(secondIssue.severity()).isEqualTo(IssuesRelease.Severity.LOW);
    assertThat(secondIssue.release().packageName()).isEqualTo("com.example.second");
    assertThat(secondIssue.release().version()).isEqualTo("2.0.0");
    assertThat(secondIssue.transitions()).containsExactly(
      IssuesRelease.Transition.ACCEPT,
      IssuesRelease.Transition.SAFE,
      IssuesRelease.Transition.FIXED);
  }

  @Test
  void should_handle_special_characters_in_project_key_and_branch_name() {
    mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=my%3Aproject%2Bkey&branchName=feature%2Fmy-branch%3Atest&pageSize=500&pageIndex=1", EMPTY_ISSUES_RELEASES_JSON);

    var response = scaApi.getIssuesReleases("my:project+key", "feature/my-branch:test", new SonarLintCancelMonitor());

    assertThat(response.issuesReleases()).isEmpty();
  }

  @Test
  void should_handle_malformed_json_response() {
    mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=test&branchName=main&pageSize=500&pageIndex=1", "invalid json");

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
    mockServer.addStringResponse("/api/v2/sca/issues-releases?projectKey=minimal-project&branchName=main&pageSize=500&pageIndex=1", jsonResponse);

    var response = scaApi.getIssuesReleases("minimal-project", "main", new SonarLintCancelMonitor());

    assertThat(response.issuesReleases()).hasSize(1);
    var issueRelease = response.issuesReleases().get(0);
    assertThat(issueRelease.key()).isEqualTo(uuid);
    assertThat(issueRelease.type()).isEqualTo(IssuesRelease.Type.VULNERABILITY);
    assertThat(issueRelease.severity()).isEqualTo(IssuesRelease.Severity.INFO);
    assertThat(issueRelease.transitions()).isEmpty();
  }
}
