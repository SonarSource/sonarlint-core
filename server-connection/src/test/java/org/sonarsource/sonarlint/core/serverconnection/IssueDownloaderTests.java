/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import mockwebserver3.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.exception.ServerErrorException;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.Flow;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.Paging;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.TextRange;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssueDownloaderTests {

  private static final Version SQ_VERSION_BEFORE_PULL = Version.create("9.3");
  private static final String PROJECT_KEY = "project";
  private static final String FILE_1_KEY = PROJECT_KEY + ":foo/bar/Hello.java";
  private static final String FILE_2_KEY = PROJECT_KEY + ":foo/bar/Hello2.java";
  private static final String FILE_3_KEY = PROJECT_KEY + ":foo/bar/Hello3.java";

  private static final String DUMMY_KEY = "dummyKey";

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private static final ProgressMonitor PROGRESS = new ProgressMonitor(null);

  private IssueDownloader underTest;

  @BeforeEach
  void prepare() {
    underTest = new IssueDownloader();
  }

  @Test
  void test_download_one_issue_old_batch_ws() {
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

    var issues = underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, null, false, SQ_VERSION_BEFORE_PULL, PROGRESS);
    assertThat(issues).hasSize(1);

    var serverIssue = issues.get(0);
    assertThat(serverIssue.lineHash()).isEqualTo("hash");
    assertThat(serverIssue.getMessage()).isEqualTo("Primary message");
    assertThat(serverIssue.getFilePath()).isEqualTo("foo/bar/Hello.java");
    assertThat(serverIssue.getLine()).isEqualTo(1);
  }

  @Test
  void test_download_issue_ignore_project_level() {
    var response = ScannerInput.ServerIssue.newBuilder()
      .setRuleRepository("sonarjava")
      .setRuleKey("S123")
      .setChecksum("hash")
      .setMsg("Primary message")
      .setLine(1)
      .setCreationDate(123456789L)
      // No path
      .setModuleKey("project")
      .build();

    mockServer.addProtobufResponseDelimited("/batch/issues?key=" + DUMMY_KEY, response);

    var issues = underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, null, false, SQ_VERSION_BEFORE_PULL, PROGRESS);
    assertThat(issues).isEmpty();
  }

  @Test
  void test_download_issues_fetch_vulnerabilities() {
    var ruleSearchResponse = Rules.SearchResponse.newBuilder()
      .setTotal(1)
      .addRules(Rules.Rule.newBuilder()
        .setKey("javasecurity:S789"))
      .build();

    var issueSearchResponse = Issues.SearchWsResponse.newBuilder()
      .addIssues(Issues.Issue.newBuilder()
        .setKey("uuid1")
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
      .addIssues(Issues.Issue.newBuilder()
        .setKey("uuid2")
        .setRule("javasecurity:S789")
        .setMessage("Project level issue")
        .setCreationDate("2021-01-11T18:17:31+0000")
        .setComponent(PROJECT_KEY)
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
        .setKey(PROJECT_KEY))
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

    mockServer.addProtobufResponse(
      "/api/rules/search.protobuf?repositories=roslyn.sonaranalyzer.security.cs,javasecurity,jssecurity,phpsecurity,pythonsecurity,tssecurity&f=repo&s=key&ps=500&p=1",
      ruleSearchResponse);
    mockServer.addProtobufResponse(
      "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED&types=VULNERABILITY&componentKeys=" + DUMMY_KEY + "&rules=javasecurity%3AS789&ps=500&p=1",
      issueSearchResponse);
    mockServer.addStringResponse("/api/sources/raw?key=" + URLEncoder.encode(FILE_1_KEY, StandardCharsets.UTF_8), "Even\nBefore My\n\tCode\n  Snippet And\n After");

    var issues = underTest.downloadTaint(mockServer.serverApiHelper(), DUMMY_KEY, null, PROGRESS);

    assertThat(issues).hasSize(1);

    var taintIssue = issues.get(0);

    assertThat(taintIssue.lineHash()).isEqualTo("hash2");
    assertThat(taintIssue.getMessage()).isEqualTo("Primary message 2");
    assertThat(taintIssue.getFilePath()).isEqualTo("foo/bar/Hello.java");
    assertThat(taintIssue.getTextRange().getStartLine()).isEqualTo(2);
    assertThat(taintIssue.getTextRange().getStartLineOffset()).isEqualTo(7);
    assertThat(taintIssue.getTextRange().getEndLine()).isEqualTo(4);
    assertThat(taintIssue.getTextRange().getEndLineOffset()).isEqualTo(9);
    assertThat(taintIssue.getCodeSnippet()).isEqualTo("My\n\tCode\n  Snippet");

    assertThat(taintIssue.getFlows()).hasSize(2);
    assertThat(taintIssue.getFlows().get(0).locations()).hasSize(4);

    var flowLocation11 = taintIssue.getFlows().get(0).locations().get(0);
    assertThat(flowLocation11.getFilePath()).isEqualTo("foo/bar/Hello.java");
    assertThat(flowLocation11.getTextRange().getStartLine()).isEqualTo(5);
    assertThat(flowLocation11.getTextRange().getStartLineOffset()).isEqualTo(1);
    assertThat(flowLocation11.getTextRange().getEndLine()).isEqualTo(5);
    assertThat(flowLocation11.getTextRange().getEndLineOffset()).isEqualTo(6);
    assertThat(flowLocation11.getCodeSnippet()).isEqualTo("After");

    // Invalid text range
    assertThat(taintIssue.getFlows().get(0).locations().get(1).getCodeSnippet()).isNull();

    // 404
    assertThat(taintIssue.getFlows().get(0).locations().get(2).getCodeSnippet()).isNull();

    // No text range
    assertThat(taintIssue.getFlows().get(0).locations().get(3).getCodeSnippet()).isNull();

    assertThat(taintIssue.getFlows().get(1).locations()).hasSize(1);
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

    var ruleSearchResponse = Rules.SearchResponse.newBuilder()
      .setTotal(1)
      .addRules(Rules.Rule.newBuilder()
        .setKey("javasecurity:S789"))
      .build();

    mockServer.addProtobufResponseDelimited("/batch/issues?key=" + DUMMY_KEY, issue1, taint1);
    mockServer.addProtobufResponse(
      "/api/rules/search.protobuf?repositories=roslyn.sonaranalyzer.security.cs,javasecurity,jssecurity,phpsecurity,pythonsecurity,tssecurity&f=repo&s=key&ps=500&p=1",
      ruleSearchResponse);
    mockServer.addResponse(
      "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED&types=VULNERABILITY&componentKeys=" + DUMMY_KEY + "&rules=javasecurity%3AS789&ps=500&p=1",
      new MockResponse().setResponseCode(404));

    var issues = underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, null, false, SQ_VERSION_BEFORE_PULL, PROGRESS);

    assertThat(issues).hasSize(1);
  }

  @Test
  void test_download_no_issues() {
    mockServer.addProtobufResponseDelimited("/batch/issues?key=" + DUMMY_KEY);

    var issues = underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, null, false, SQ_VERSION_BEFORE_PULL, PROGRESS);
    assertThat(issues).isEmpty();
  }

  @Test
  void test_fail_other_codes() {
    mockServer.addResponse("/batch/issues?key=" + DUMMY_KEY, new MockResponse().setResponseCode(503));

    var thrown = assertThrows(ServerErrorException.class,
      () -> underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, null, false, SQ_VERSION_BEFORE_PULL, PROGRESS));
    assertThat(thrown).hasMessageContaining("Error 503");
  }

  @Test
  void test_return_empty_if_404() {
    mockServer.addResponse("/batch/issues?key=" + DUMMY_KEY, new MockResponse().setResponseCode(404));

    var issues = underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, null, false, SQ_VERSION_BEFORE_PULL, PROGRESS);
    assertThat(issues).isEmpty();
  }

  @Test
  void test_filter_batch_issues_by_branch_if_branch_parameter_provided() {
    var response = ScannerInput.ServerIssue.newBuilder()
      .setRuleRepository("sonarjava")
      .setRuleKey("S123")
      .setPath("src/Foo.java")
      .build();

    mockServer.addProtobufResponseDelimited("/batch/issues?key=" + DUMMY_KEY + "&branch=branchName", response);

    var issues = underTest.download(mockServer.serverApiHelper(), DUMMY_KEY, "branchName", false, SQ_VERSION_BEFORE_PULL, PROGRESS);
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
    var ruleSearchResponse = Rules.SearchResponse.newBuilder()
      .setTotal(1)
      .addRules(Rules.Rule.newBuilder()
        .setKey("javasecurity:S789"))
      .build();
    mockServer.addProtobufResponse(
      "/api/rules/search.protobuf?repositories=roslyn.sonaranalyzer.security.cs,javasecurity,jssecurity,phpsecurity,pythonsecurity,tssecurity&f=repo&s=key&ps=500&p=1",
      ruleSearchResponse);
    mockServer.addProtobufResponse(
      "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED&types=VULNERABILITY&componentKeys=dummyKey&rules=javasecurity%3AS789&branch=branchName&ps=500&p=1", response);

    var issues = underTest.downloadTaint(mockServer.serverApiHelper(), DUMMY_KEY, "branchName", PROGRESS);

    assertThat(issues).hasSize(1);
  }

}
