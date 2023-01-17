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
package org.sonarsource.sonarlint.core.serverconnection;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.Flow;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.Paging;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.Severity;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.TextRange;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues.Location;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues.TaintVulnerabilityLite;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.serverconnection.TaintIssueDownloader.hash;

class TaintIssueDownloaderTests {

  private static final String PROJECT_KEY = "project";
  private static final String FILE_1_KEY = PROJECT_KEY + ":foo/bar/Hello.java";
  private static final String FILE_2_KEY = PROJECT_KEY + ":foo/bar/Hello2.java";
  private static final String FILE_3_KEY = PROJECT_KEY + ":foo/bar/Hello3.java";

  private static final String DUMMY_KEY = "dummyKey";

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();
  private ServerApi serverApi;

  private static final ProgressMonitor PROGRESS = new ProgressMonitor(null);

  private TaintIssueDownloader underTest;

  @BeforeEach
  void prepare() {
    underTest = new TaintIssueDownloader(Set.of(Language.JAVA));
    serverApi = new ServerApi(mockServer.serverApiHelper());
  }

  @Test
  void test_download_vulnerabilities_from_issue_search() {
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
        .setType(Common.RuleType.VULNERABILITY)
        .setSeverity(Severity.INFO)
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
            .setTextRange(TextRange.newBuilder().setStartLine(5).setStartOffset(1).setEndLine(5).setEndOffset(6))))
        .setRuleDescriptionContextKey("context1"))
      .addIssues(Issues.Issue.newBuilder()
        .setKey("uuid2")
        .setRule("javasecurity:S789")
        .setMessage("Project level issue")
        .setCreationDate("2021-01-11T18:17:31+0000")
        .setComponent(PROJECT_KEY)
        .setType(Common.RuleType.VULNERABILITY)
        .setSeverity(Severity.CRITICAL)
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
            .setTextRange(TextRange.newBuilder().setStartLine(5).setStartOffset(1).setEndLine(5).setEndOffset(6))))
        .setRuleDescriptionContextKey("context2"))
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

    var issues = underTest.downloadTaintFromIssueSearch(serverApi, DUMMY_KEY, null, PROGRESS);

    assertThat(issues).hasSize(1);

    var taintIssue = issues.get(0);

    assertThat(taintIssue.getMessage()).isEqualTo("Primary message 2");
    assertThat(taintIssue.getFilePath()).isEqualTo("foo/bar/Hello.java");
    assertThat(taintIssue.getType()).isEqualTo(RuleType.VULNERABILITY);
    assertThat(taintIssue.getSeverity()).isEqualTo(IssueSeverity.INFO);

    assertTextRange(taintIssue.getTextRange(), 2, 7,4, 9, hash("My\n\tCode\n  Snippet"));

    assertThat(taintIssue.getFlows()).hasSize(2);
    assertThat(taintIssue.getFlows().get(0).locations()).hasSize(4);

    var flowLocation11 = taintIssue.getFlows().get(0).locations().get(0);
    assertThat(flowLocation11.getFilePath()).isEqualTo("foo/bar/Hello.java");

    assertTextRange(flowLocation11.getTextRange(), 5, 1, 5, 6, hash("After"));

    // Invalid text range
    assertThat(taintIssue.getFlows().get(0).locations().get(1).getTextRange().getHash()).isEmpty();

    // 404
    assertThat(taintIssue.getFlows().get(0).locations().get(2).getTextRange().getHash()).isEmpty();

    // No text range
    assertThat(taintIssue.getFlows().get(0).locations().get(3).getTextRange()).isNull();

    assertThat(taintIssue.getFlows().get(1).locations()).hasSize(1);
    assertThat(taintIssue.getRuleDescriptionContextKey()).isEqualTo("context1");
  }

  @Test
  void test_filter_taint_issues_by_branch_if_branch_parameter_provided() {
    var response = Issues.SearchWsResponse.newBuilder()
      .addIssues(Issues.Issue.newBuilder()
        .setRule("javasecurity:S789")
        .setCreationDate("2021-01-11T18:17:31+0000")
        .setComponent(FILE_1_KEY)
        .setType(Common.RuleType.BUG)
        .setSeverity(Severity.INFO))
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

    var issues = underTest.downloadTaintFromIssueSearch(serverApi, DUMMY_KEY, "branchName", PROGRESS);

    assertThat(issues).hasSize(1);
  }

  @Test
  void test_download_taint_issues_from_pull_ws() {
    var timestamp = Issues.IssuesPullQueryTimestamp.newBuilder().setQueryTimestamp(123L).build();
    var taint1 = TaintVulnerabilityLite.newBuilder()
      .setKey("uuid1")
      .setRuleKey("sonarjava:S123")
      .setType(Common.RuleType.VULNERABILITY)
      .setSeverity(Common.Severity.MAJOR)
      .setMainLocation(Location.newBuilder().setFilePath("foo/bar/Hello.java").setMessage("Primary message")
        .setTextRange(org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues.TextRange.newBuilder().setStartLine(1).setStartLineOffset(2).setEndLine(3)
          .setEndLineOffset(4).setHash("hash")))
      .setCreationDate(123456789L)
      .addFlows(Issues.Flow.newBuilder()
        .addLocations(Issues.Location.newBuilder().setMessage("Flow 1 - Location 1").setFilePath("foo/bar/Hello.java")
          .setTextRange(Issues.TextRange.newBuilder().setStartLine(5).setStartLineOffset(1).setEndLine(5).setEndLineOffset(6).setHash("hashLocation11")))
        .addLocations(Issues.Location.newBuilder().setMessage("Flow 1 - Another file").setFilePath("foo/bar/Hello2.java")
          .setTextRange(Issues.TextRange.newBuilder().setStartLine(9).setStartLineOffset(10).setEndLine(11).setEndLineOffset(12).setHash("hashLocation12")))
        .addLocations(Issues.Location.newBuilder().setMessage("Flow 1 - Location No Text Range").setFilePath("foo/bar/Hello.java")))
      .addFlows(Issues.Flow.newBuilder()
        .addLocations(Issues.Location.newBuilder().setMessage("Flow 2 - Location 1").setFilePath("foo/bar/Hello.java")
          .setTextRange(Issues.TextRange.newBuilder().setStartLine(5).setStartLineOffset(1).setEndLine(5).setEndLineOffset(6).setHash("hashLocation21"))))
      .setRuleDescriptionContextKey("context")
      .build();

    var taintNoRange = TaintVulnerabilityLite.newBuilder()
      .setKey("uuid2")
      .setRuleKey("sonarjava:S123")
      .setType(Common.RuleType.VULNERABILITY)
      .setSeverity(Common.Severity.MINOR)
      .setMainLocation(Location.newBuilder().setFilePath("foo/bar/Hello.java").setMessage("Primary message"))
      .setCreationDate(123456789L)
      .build();

    mockServer.addProtobufResponseDelimited("/api/issues/pull_taint?projectKey=" + DUMMY_KEY + "&branchName=myBranch&languages=java", timestamp, taint1, taintNoRange);

    var result = underTest.downloadTaintFromPull(serverApi, DUMMY_KEY, "myBranch", Optional.empty());
    assertThat(result.getQueryTimestamp()).isEqualTo(Instant.ofEpochMilli(123L));

    assertThat(result.getChangedTaintIssues()).hasSize(2);
    assertThat(result.getClosedIssueKeys()).isEmpty();

    var serverTaintIssue = result.getChangedTaintIssues().get(0);
    assertThat(serverTaintIssue.getKey()).isEqualTo("uuid1");
    assertThat(serverTaintIssue.getMessage()).isEqualTo("Primary message");
    assertThat(serverTaintIssue.getFilePath()).isEqualTo("foo/bar/Hello.java");
    assertThat(serverTaintIssue.getSeverity()).isEqualTo(IssueSeverity.MAJOR);
    assertThat(serverTaintIssue.getType()).isEqualTo(RuleType.VULNERABILITY);

    assertTextRange(serverTaintIssue.getTextRange(), 1, 2, 3, 4, "hash");

    assertThat(serverTaintIssue.getFlows()).hasSize(2);
    assertThat(serverTaintIssue.getFlows().get(0).locations()).hasSize(3);

    var flowLocation11 = serverTaintIssue.getFlows().get(0).locations().get(0);
    assertThat(flowLocation11.getFilePath()).isEqualTo("foo/bar/Hello.java");
    assertTextRange(flowLocation11.getTextRange(),5, 1, 5, 6, "hashLocation11");

    // No text range
    assertThat(serverTaintIssue.getFlows().get(0).locations().get(2).getTextRange()).isNull();

    assertThat(serverTaintIssue.getFlows().get(1).locations()).hasSize(1);

    var taintIssueNoRange = result.getChangedTaintIssues().get(1);
    assertThat(taintIssueNoRange.getKey()).isEqualTo("uuid2");
    assertThat(taintIssueNoRange.getFilePath()).isEqualTo("foo/bar/Hello.java");
    assertThat(taintIssueNoRange.getTextRange()).isNull();

    assertThat(serverTaintIssue.getRuleDescriptionContextKey()).isEqualTo("context");
  }

  private static void assertTextRange(@Nullable TextRangeWithHash textRangeWithHash, int startLine, int startLineOffset,
    int endLine, int endLineOffset, String hash) {
    assertThat(textRangeWithHash).isNotNull();
    assertThat(textRangeWithHash.getStartLine()).isEqualTo(startLine);
    assertThat(textRangeWithHash.getStartLineOffset()).isEqualTo(startLineOffset);
    assertThat(textRangeWithHash.getEndLine()).isEqualTo(endLine);
    assertThat(textRangeWithHash.getEndLineOffset()).isEqualTo(endLineOffset);
    assertThat(textRangeWithHash.getHash()).isEqualTo(hash);
  }

}
