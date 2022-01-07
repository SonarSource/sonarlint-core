/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import mockwebserver3.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Common.Flow;
import org.sonarqube.ws.Common.Paging;
import org.sonarqube.ws.Common.TextRange;
import org.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue.Location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssueDownloaderTests {

  private static final String FILE_1_KEY = "project:foo/bar/Hello.java";
  private static final String FILE_2_KEY = "project:foo/bar/Hello2.java";
  private static final String FILE_3_KEY = "project:foo/bar/Hello3.java";

  private static final String DUMMY_KEY = "dummyKey";

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private static final ProgressMonitor PROGRESS = new ProgressMonitor(null);

  private final Sonarlint.ProjectConfiguration projectConfiguration = Sonarlint.ProjectConfiguration.newBuilder().build();
  private final IssueStorePaths issueStorePaths = new IssueStorePaths();
  private IssueDownloader underTest;

  @BeforeEach
  void prepare() {
    underTest = new IssueDownloader(issueStorePaths);
  }

  @Test
  void test_download_one_issue_no_taint() {
    var response = ScannerInput.ServerIssue.newBuilder()
      .setRuleRepository("sonarjava")
      .setRuleKey("S123")
      .setChecksum("hash")
      .setMsg("Primary message")
      .setLine(1)
      .setCreationDate(123456789L)
      .setPath("foo/bar/Hello.java")
      .setModuleKey("project")
      .build();

    mockServer.addProtobufResponseDelimited("/batch/issues?key=" + DUMMY_KEY, response);

    var issues = underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, projectConfiguration, false, null, PROGRESS);
    assertThat(issues).hasSize(1);

    var serverIssue = issues.get(0);
    assertThat(serverIssue.getLineHash()).isEqualTo("hash");
    assertThat(serverIssue.getPrimaryLocation().getMsg()).isEqualTo("Primary message");
    assertThat(serverIssue.getPrimaryLocation().getPath()).isEqualTo("foo/bar/Hello.java");
    assertThat(serverIssue.getPrimaryLocation().getTextRange().getStartLine()).isEqualTo(1);
    // Unset
    assertThat(serverIssue.getPrimaryLocation().getTextRange().getStartLineOffset()).isZero();
    assertThat(serverIssue.getPrimaryLocation().getTextRange().getEndLine()).isZero();
    assertThat(serverIssue.getPrimaryLocation().getTextRange().getEndLineOffset()).isZero();

    assertThat(serverIssue.getFlowList()).isEmpty();
  }

  @Test
  void test_download_issues_fetch_vulnerabilities() {
    var issue1 = ScannerInput.ServerIssue.newBuilder()
      .setRuleRepository("sonarjava")
      .setRuleKey("S123")
      .setChecksum("hash1")
      .setMsg("Primary message 1")
      .setLine(1)
      .setCreationDate(123456789L)
      .setPath("foo/bar/Hello.java")
      .setModuleKey("project")
      .build();

    var taint1 = ScannerInput.ServerIssue.newBuilder()
      .setRuleRepository("javasecurity")
      .setRuleKey("S789")
      .setChecksum("hash2")
      .setMsg("Primary message 2")
      .setLine(2)
      .setStatus("OPEN")
      .setCreationDate(123456789L)
      .setPath("foo/bar/Hello2.java")
      .setModuleKey("project")
      .build();

    var response = Issues.SearchWsResponse.newBuilder()
      .addIssues(Issues.Issue.newBuilder()
        .setRule("javasecurity:S789")
        .setHash("hash2")
        .setMessage("Primary message 2")
        .setTextRange(TextRange.newBuilder().setStartLine(2).setStartOffset(7).setEndLine(4).setEndOffset(9))
        .setCreationDate("2021-01-11T18:17:31+0000")
        .setComponent(FILE_1_KEY)
        .addFlows(Flow.newBuilder()
          .addLocations(Common.Location.newBuilder().setMsg("Flow 1 - Location 1").setComponent(FILE_1_KEY)
            .setTextRange(TextRange.newBuilder().setStartLine(5).setStartOffset(1).setEndLine(5).setEndOffset(6)))
          .addLocations(Common.Location.newBuilder().setMsg("Flow 1 - Invalid text range").setComponent(FILE_1_KEY)
            .setTextRange(TextRange.newBuilder().setStartLine(5).setStartOffset(1).setEndLine(7).setEndOffset(6)))
          .addLocations(Common.Location.newBuilder().setMsg("Flow 1 - Another file").setComponent(FILE_2_KEY)
            .setTextRange(TextRange.newBuilder().setStartLine(9).setStartOffset(10).setEndLine(11).setEndOffset(12)))
          .addLocations(Common.Location.newBuilder().setMsg("Flow 1 - Location No Text Range").setComponent(FILE_3_KEY)))
        .addFlows(Flow.newBuilder()
          .addLocations(Common.Location.newBuilder().setMsg("Flow 2 - Location 1").setComponent(FILE_1_KEY)
            .setTextRange(TextRange.newBuilder().setStartLine(5).setStartOffset(1).setEndLine(5).setEndOffset(6)))))
      .addComponents(Issues.Component.newBuilder()
        .setKey(FILE_1_KEY)
        .setPath("foo/bar/Hello.java"))
      .addComponents(Issues.Component.newBuilder()
        .setKey(FILE_2_KEY)
        .setPath("foo/bar/Hello2.java"))
      .addComponents(Issues.Component.newBuilder()
        .setKey(FILE_3_KEY)
        .setPath("foo/bar/Hello3.java"))
      .setPaging(Paging.newBuilder()
        .setPageIndex(1)
        .setPageSize(500)
        .setTotal(1))
      .build();

    mockServer.addProtobufResponseDelimited("/batch/issues?key=" + DUMMY_KEY, issue1, taint1);
    mockServer.addProtobufResponse(
      "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED&types=VULNERABILITY&componentKeys=" + DUMMY_KEY + "&rules=javasecurity%3AS789&ps=500&p=1",
      response);
    mockServer.addStringResponse("/api/sources/raw?key=" + URLEncoder.encode(FILE_1_KEY, StandardCharsets.UTF_8), "Even\nBefore My\n\tCode\n  Snippet And\n After");

    var issues = underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, projectConfiguration, true, null, PROGRESS);

    assertThat(issues).hasSize(2);

    var issue = issues.get(0);
    assertThat(issue.getLineHash()).isEqualTo("hash1");
    assertThat(issue.getPrimaryLocation().getMsg()).isEqualTo("Primary message 1");
    assertThat(issue.getPrimaryLocation().getPath()).isEqualTo("foo/bar/Hello.java");
    assertThat(issue.getPrimaryLocation().getTextRange().getStartLine()).isEqualTo(1);

    var taintIssue = issues.get(1);

    assertThat(taintIssue.getLineHash()).isEqualTo("hash2");
    assertThat(taintIssue.getPrimaryLocation().getMsg()).isEqualTo("Primary message 2");
    assertThat(taintIssue.getPrimaryLocation().getPath()).isEqualTo("foo/bar/Hello.java");
    assertThat(taintIssue.getPrimaryLocation().getTextRange().getStartLine()).isEqualTo(2);
    assertThat(taintIssue.getPrimaryLocation().getTextRange().getStartLineOffset()).isEqualTo(7);
    assertThat(taintIssue.getPrimaryLocation().getTextRange().getEndLine()).isEqualTo(4);
    assertThat(taintIssue.getPrimaryLocation().getTextRange().getEndLineOffset()).isEqualTo(9);
    assertThat(taintIssue.getPrimaryLocation().getCodeSnippet()).isEqualTo("My\n\tCode\n  Snippet");

    assertThat(taintIssue.getFlowList()).hasSize(2);
    assertThat(taintIssue.getFlow(0).getLocationList()).hasSize(4);

    var flowLocation11 = taintIssue.getFlow(0).getLocation(0);
    assertThat(flowLocation11.getPath()).isEqualTo("foo/bar/Hello.java");
    assertThat(flowLocation11.getTextRange().getStartLine()).isEqualTo(5);
    assertThat(flowLocation11.getTextRange().getStartLineOffset()).isEqualTo(1);
    assertThat(flowLocation11.getTextRange().getEndLine()).isEqualTo(5);
    assertThat(flowLocation11.getTextRange().getEndLineOffset()).isEqualTo(6);
    assertThat(flowLocation11.getCodeSnippet()).isEqualTo("After");

    // Invalid text range
    assertThat(taintIssue.getFlow(0).getLocation(1).getCodeSnippet()).isEmpty();

    // 404
    assertThat(taintIssue.getFlow(0).getLocation(2).getCodeSnippet()).isEmpty();

    // No text range
    assertThat(taintIssue.getFlow(0).getLocation(3).getCodeSnippet()).isEmpty();

    assertThat(taintIssue.getFlow(1).getLocationList()).hasSize(1);
  }

  @Test
  void test_download_issues_dont_fetch_resolved_vulnerabilities() {
    var issue1 = ScannerInput.ServerIssue.newBuilder()
      .setRuleRepository("sonarjava")
      .setRuleKey("S123")
      .setChecksum("hash1")
      .setMsg("Primary message 1")
      .setLine(1)
      .setCreationDate(123456789L)
      .setPath("foo/bar/Hello.java")
      .setModuleKey("project")
      .build();

    var taint1 = ScannerInput.ServerIssue.newBuilder()
      .setRuleRepository("javasecurity")
      .setRuleKey("S789")
      .setChecksum("hash2")
      .setMsg("Primary message 2")
      .setLine(2)
      .setStatus("RESOLVED")
      .setCreationDate(123456789L)
      .setPath("foo/bar/Hello2.java")
      .setModuleKey("project")
      .build();

    mockServer.addProtobufResponseDelimited("/batch/issues?key=" + DUMMY_KEY, issue1, taint1);

    var issues = underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, projectConfiguration, true, null, PROGRESS);

    assertThat(issues).hasSize(1);

    assertThat(mockServer.getRequestCount()).isEqualTo(1);
  }

  @Test
  void test_ignore_failure_when_fetching_taint_vulnerabilities() {
    var issue1 = ScannerInput.ServerIssue.newBuilder()
      .setRuleRepository("sonarjava")
      .setRuleKey("S123")
      .setChecksum("hash1")
      .setMsg("Primary message 1")
      .setLine(1)
      .setCreationDate(123456789L)
      .setPath("foo/bar/Hello.java")
      .setModuleKey("project")
      .build();

    var taint1 = ScannerInput.ServerIssue.newBuilder()
      .setRuleRepository("javasecurity")
      .setRuleKey("S789")
      .setChecksum("hash2")
      .setMsg("Primary message 2")
      .setLine(2)
      .setCreationDate(123456789L)
      .setPath("foo/bar/Hello2.java")
      .setModuleKey("project")
      .build();

    mockServer.addProtobufResponseDelimited("/batch/issues?key=" + DUMMY_KEY, issue1, taint1);
    mockServer.addResponse(
      "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED&types=VULNERABILITY&componentKeys=" + DUMMY_KEY + "&rules=javasecurity%3AS789&ps=500&p=1",
      new MockResponse().setResponseCode(404));

    var issues = underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, projectConfiguration, true, null, PROGRESS);

    assertThat(issues).hasSize(1);
  }

  @Test
  void test_download_no_issues() {
    mockServer.addProtobufResponseDelimited("/batch/issues?key=" + DUMMY_KEY);

    var issues = underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, projectConfiguration, true, null, PROGRESS);
    assertThat(issues).isEmpty();
  }

  @Test
  void test_fail_other_codes() {
    mockServer.addResponse("/batch/issues?key=" + DUMMY_KEY, new MockResponse().setResponseCode(503));

    var thrown = assertThrows(IllegalStateException.class,
      () -> underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, projectConfiguration, true, null, PROGRESS));
    assertThat(thrown).hasMessageContaining("Error 503");
  }

  @Test
  void test_return_empty_if_404() {
    mockServer.addResponse("/batch/issues?key=" + DUMMY_KEY, new MockResponse().setResponseCode(404));

    var issues = underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, projectConfiguration, true, null, PROGRESS);
    assertThat(issues).isEmpty();
  }

  @Test
  void test_filter_batch_issues_by_branch_if_branch_parameter_provided() {
    var response = ScannerInput.ServerIssue.newBuilder()
      .setRuleRepository("sonarjava")
      .setRuleKey("S123")
      .build();

    mockServer.addProtobufResponseDelimited("/batch/issues?key=" + DUMMY_KEY + "&branch=branchName", response);

    var issues = underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, projectConfiguration, false, "branchName", PROGRESS);
    assertThat(issues).hasSize(1);
  }

  @Test
  void test_filter_taint_issues_by_branch_if_branch_parameter_provided() {
    var response = Issues.SearchWsResponse.newBuilder()
      .addIssues(Issues.Issue.newBuilder()
        .setRule("javasecurity:S789")
        .setCreationDate("2021-01-11T18:17:31+0000")
        .setComponent(FILE_1_KEY))
      .addComponents(Issues.Component.newBuilder()
        .setKey(FILE_1_KEY)
        .setPath("foo/bar/Hello2.java"))
      .setPaging(Paging.newBuilder()
        .setPageIndex(1)
        .setPageSize(500)
        .setTotal(1))
      .build();
    var taint1 = ScannerInput.ServerIssue.newBuilder()
      .setRuleRepository("javasecurity")
      .setRuleKey("S789")
      .setStatus("OPEN")
      .build();
    mockServer.addProtobufResponseDelimited("/batch/issues?key=" + DUMMY_KEY + "&branch=branchName", taint1);
    mockServer.addProtobufResponse(
      "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED&types=VULNERABILITY&componentKeys=dummyKey&rules=javasecurity%3AS789&branch=branchName&ps=500&p=1", response);

    var issues = underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, projectConfiguration, true, "branchName", PROGRESS);

    assertThat(issues).hasSize(1);
  }

}
