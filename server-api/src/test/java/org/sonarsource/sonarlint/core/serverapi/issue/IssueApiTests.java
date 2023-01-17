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
package org.sonarsource.sonarlint.core.serverapi.issue;

import java.util.Set;
import mockwebserver3.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.exception.ServerErrorException;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;

class IssueApiTests {
  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private IssueApi underTest;

  @BeforeEach
  void setUp() {
    underTest = new IssueApi(mockServer.serverApiHelper());
  }

  @Test
  void should_download_all_issues_as_batch() {
    mockServer.addProtobufResponseDelimited("/batch/issues?key=keyyy", ScannerInput.ServerIssue.newBuilder().setRuleKey("ruleKey").build());

    var issues = underTest.downloadAllFromBatchIssues("keyyy", null);

    assertThat(issues)
      .extracting("ruleKey")
      .containsOnly("ruleKey");
  }

  @Test
  void should_download_all_issues_as_batch_from_branch() {
    mockServer.addProtobufResponseDelimited("/batch/issues?key=keyyy&branch=branchName", ScannerInput.ServerIssue.newBuilder().setRuleKey("ruleKey").build());

    var issues = underTest.downloadAllFromBatchIssues("keyyy", "branchName");

    assertThat(issues)
      .extracting("ruleKey")
      .containsOnly("ruleKey");
  }

  @Test
  void should_return_no_batch_issue_if_download_is_forbidden() {
    mockServer.addResponse("/batch/issues?key=keyyy", new MockResponse().setResponseCode(403));

    var issues = underTest.downloadAllFromBatchIssues("keyyy", null);

    assertThat(issues).isEmpty();
  }

  @Test
  void should_return_no_batch_issue_if_endpoint_is_not_found() {
    mockServer.addResponse("/batch/issues?key=keyyy", new MockResponse().setResponseCode(404));

    var issues = underTest.downloadAllFromBatchIssues("keyyy", null);

    assertThat(issues).isEmpty();
  }

  @Test
  void should_throw_an_error_if_batch_issue_download_fails() {
    mockServer.addResponse("/batch/issues?key=keyyy", new MockResponse().setResponseCode(500));

    var throwable = catchThrowable(() -> underTest.downloadAllFromBatchIssues("keyyy", null));

    assertThat(throwable).isInstanceOf(ServerErrorException.class);
  }

  @Test
  void should_throw_an_error_if_batch_issue_body__format_is_unexpected() {
    mockServer.addStringResponse("/batch/issues?key=keyyy", "nope");

    var throwable = catchThrowable(() -> underTest.downloadAllFromBatchIssues("keyyy", null));

    assertThat(throwable).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void should_download_all_vulnerabilities() {
    mockServer.addProtobufResponse("/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED&types=VULNERABILITY&componentKeys=keyyy&rules=ruleKey&ps=500&p=1",
      Issues.SearchWsResponse.newBuilder().addIssues(Issues.Issue.newBuilder().setKey("issueKey").build()).build());
    mockServer.addProtobufResponse("/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED&types=VULNERABILITY&componentKeys=keyyy&rules=ruleKey&ps=500&p=2",
      Issues.SearchWsResponse.newBuilder().addComponents(Issues.Component.newBuilder().setKey("componentKey").setPath("componentPath").build()).build());

    var result = underTest.downloadVulnerabilitiesForRules("keyyy", Set.of("ruleKey"), null, new ProgressMonitor(null));

    assertThat(result.getIssues())
      .extracting("key")
      .containsOnly("issueKey");
    assertThat(result.getComponentPathsByKey())
      .containsOnly(entry("componentKey", "componentPath"));
  }
}
