/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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

import java.io.IOException;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Common.Flow;
import org.sonarqube.ws.Common.Paging;
import org.sonarqube.ws.Common.TextRange;
import org.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue.Location;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssueDownloaderTests {

  private static final String DUMMY_KEY = "dummyKey";

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  private static final ProgressWrapper PROGRESS = new ProgressWrapper(null);

  private final Sonarlint.ProjectConfiguration projectConfiguration = Sonarlint.ProjectConfiguration.newBuilder().build();
  private final IssueStorePaths issueStorePaths = new IssueStorePaths();

  @Test
  void test_download_one_issue_no_taint() throws IOException {
    ScannerInput.ServerIssue response = ScannerInput.ServerIssue.newBuilder()
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

    IssueDownloader issueDownloader = new IssueDownloader(mockServer.slClient(), issueStorePaths);
    List<ServerIssue> issues = issueDownloader.download(DUMMY_KEY, projectConfiguration, false, PROGRESS);
    assertThat(issues).hasSize(1);

    ServerIssue serverIssue = issues.get(0);
    assertThat(serverIssue.getLineHash()).isEqualTo("hash");
    assertThat(serverIssue.getPrimaryLocation().getMsg()).isEqualTo("Primary message");
    assertThat(serverIssue.getPrimaryLocation().getPath()).isEqualTo("foo/bar/Hello.java");
    assertThat(serverIssue.getPrimaryLocation().getTextRange().getStartLine()).isEqualTo(1);
    // Unset
    assertThat(serverIssue.getPrimaryLocation().getTextRange().getStartLineOffset()).isEqualTo(0);
    assertThat(serverIssue.getPrimaryLocation().getTextRange().getEndLine()).isEqualTo(0);
    assertThat(serverIssue.getPrimaryLocation().getTextRange().getEndLineOffset()).isEqualTo(0);

    assertThat(serverIssue.getFlowList()).isEmpty();
  }

  @Test
  void test_download_issues_fetch_vulnerabilities() throws IOException {
    ScannerInput.ServerIssue issue1 = ScannerInput.ServerIssue.newBuilder()
      .setRuleRepository("sonarjava")
      .setRuleKey("S123")
      .setChecksum("hash1")
      .setMsg("Primary message 1")
      .setLine(1)
      .setCreationDate(123456789L)
      .setPath("foo/bar/Hello.java")
      .setModuleKey("project")
      .build();

    ScannerInput.ServerIssue taint1 = ScannerInput.ServerIssue.newBuilder()
      .setRuleRepository("javasecurity")
      .setRuleKey("S789")
      .setChecksum("hash2")
      .setMsg("Primary message 2")
      .setLine(2)
      .setCreationDate(123456789L)
      .setPath("foo/bar/Hello2.java")
      .setModuleKey("project")
      .build();

    Issues.SearchWsResponse response = Issues.SearchWsResponse.newBuilder()
      .addIssues(Issues.Issue.newBuilder()
        .setRule("javasecurity:S789")
        .setHash("hash2")
        .setMessage("Primary message 2")
        .setTextRange(TextRange.newBuilder().setStartLine(2).setStartOffset(2).setEndLine(3).setEndOffset(4))
        .setCreationDate("2021-01-11T18:17:31+0000")
        .setComponent("project:foo/bar/Hello2.java")
        .addFlows(Flow.newBuilder()
          .addLocations(Common.Location.newBuilder().setMsg("Flow 1 - Location 1").setComponent("project:foo/bar/Hello.java")
            .setTextRange(TextRange.newBuilder().setStartLine(5).setStartOffset(6).setEndLine(7).setEndOffset(8)))
          .addLocations(Common.Location.newBuilder().setMsg("Flow 1 - Location 2").setComponent("project:foo/bar/Hello2.java")
            .setTextRange(TextRange.newBuilder().setStartLine(9).setStartOffset(10).setEndLine(11).setEndOffset(12))))
        .addFlows(Flow.newBuilder()
          .addLocations(Common.Location.newBuilder().setMsg("Flow 2 - Location 1").setComponent("project:foo/bar/Hello3.java")
            .setTextRange(TextRange.newBuilder().setStartLine(5).setStartOffset(6).setEndLine(7).setEndOffset(8)))
          .addLocations(Common.Location.newBuilder().setMsg("Flow 2 - Location 2").setComponent("project:foo/bar/Hello.java")
            .setTextRange(TextRange.newBuilder().setStartLine(9).setStartOffset(10).setEndLine(11).setEndOffset(12)))))
      .addComponents(Issues.Component.newBuilder()
        .setKey("project:foo/bar/Hello.java")
        .setPath("foo/bar/Hello.java"))
      .addComponents(Issues.Component.newBuilder()
        .setKey("project:foo/bar/Hello2.java")
        .setPath("foo/bar/Hello2.java"))
      .addComponents(Issues.Component.newBuilder()
        .setKey("project:foo/bar/Hello3.java")
        .setPath("foo/bar/Hello3.java"))
      .setPaging(Paging.newBuilder()
        .setPageIndex(1)
        .setPageSize(500)
        .setTotal(1))
      .build();

    mockServer.addProtobufResponseDelimited("/batch/issues?key=" + DUMMY_KEY, issue1, taint1);
    mockServer.addProtobufResponse(
      "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED,RESOLVED&types=VULNERABILITY&componentKeys=" + DUMMY_KEY + "&rules=javasecurity%3AS789&ps=500&p=1",
      response);

    IssueDownloader issueDownloader = new IssueDownloader(mockServer.slClient(), issueStorePaths);
    List<ServerIssue> issues = issueDownloader.download(DUMMY_KEY, projectConfiguration, true, PROGRESS);

    assertThat(issues).hasSize(2);

    ServerIssue issue = issues.get(0);
    assertThat(issue.getLineHash()).isEqualTo("hash1");
    assertThat(issue.getPrimaryLocation().getMsg()).isEqualTo("Primary message 1");
    assertThat(issue.getPrimaryLocation().getPath()).isEqualTo("foo/bar/Hello.java");
    assertThat(issue.getPrimaryLocation().getTextRange().getStartLine()).isEqualTo(1);

    ServerIssue taintIssue = issues.get(1);

    assertThat(taintIssue.getLineHash()).isEqualTo("hash2");
    assertThat(taintIssue.getPrimaryLocation().getMsg()).isEqualTo("Primary message 2");
    assertThat(taintIssue.getPrimaryLocation().getPath()).isEqualTo("foo/bar/Hello2.java");
    assertThat(taintIssue.getPrimaryLocation().getTextRange().getStartLine()).isEqualTo(2);
    assertThat(taintIssue.getPrimaryLocation().getTextRange().getStartLineOffset()).isEqualTo(2);
    assertThat(taintIssue.getPrimaryLocation().getTextRange().getEndLine()).isEqualTo(3);
    assertThat(taintIssue.getPrimaryLocation().getTextRange().getEndLineOffset()).isEqualTo(4);

    assertThat(taintIssue.getFlowList()).hasSize(2);
    assertThat(taintIssue.getFlow(0).getLocationList()).hasSize(2);
    Location flowLocation12 = taintIssue.getFlow(0).getLocation(1);
    assertThat(flowLocation12.getMsg()).isEqualTo("Flow 1 - Location 2");
    assertThat(flowLocation12.getPath()).isEqualTo("foo/bar/Hello2.java");
    assertThat(flowLocation12.getTextRange().getStartLine()).isEqualTo(9);
    assertThat(flowLocation12.getTextRange().getStartLineOffset()).isEqualTo(10);
    assertThat(flowLocation12.getTextRange().getEndLine()).isEqualTo(11);
    assertThat(flowLocation12.getTextRange().getEndLineOffset()).isEqualTo(12);

    assertThat(taintIssue.getFlow(1).getLocationList()).hasSize(2);
  }

  @Test
  void test_ignore_failure_when_fetching_taint_vulnerabilities() throws IOException {
    ScannerInput.ServerIssue issue1 = ScannerInput.ServerIssue.newBuilder()
      .setRuleRepository("sonarjava")
      .setRuleKey("S123")
      .setChecksum("hash1")
      .setMsg("Primary message 1")
      .setLine(1)
      .setCreationDate(123456789L)
      .setPath("foo/bar/Hello.java")
      .setModuleKey("project")
      .build();

    ScannerInput.ServerIssue taint1 = ScannerInput.ServerIssue.newBuilder()
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
      "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED,RESOLVED&types=VULNERABILITY&componentKeys=" + DUMMY_KEY + "&rules=javasecurity%3AS789&ps=500&p=1",
      new MockResponse().setResponseCode(404));

    IssueDownloader issueDownloader = new IssueDownloader(mockServer.slClient(), issueStorePaths);
    List<ServerIssue> issues = issueDownloader.download(DUMMY_KEY, projectConfiguration, true, PROGRESS);

    assertThat(issues).hasSize(1);
  }

  @Test
  void test_download_no_issues() throws IOException {
    mockServer.addProtobufResponseDelimited("/batch/issues?key=" + DUMMY_KEY);

    IssueDownloader issueDownloader = new IssueDownloader(mockServer.slClient(), issueStorePaths);
    List<ServerIssue> issues = issueDownloader.download(DUMMY_KEY, projectConfiguration, true, PROGRESS);
    assertThat(issues).isEmpty();
  }

  @Test
  void test_fail_other_codes() throws IOException {
    mockServer.addResponse("/batch/issues?key=" + DUMMY_KEY, new MockResponse().setResponseCode(503));

    IssueDownloader issueDownloader = new IssueDownloader(mockServer.slClient(), issueStorePaths);
    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> issueDownloader.download(DUMMY_KEY, projectConfiguration, true, PROGRESS));
    assertThat(thrown).hasMessageContaining("Error 503");
  }

  @Test
  void test_return_empty_if_404() throws IOException {
    mockServer.addResponse("/batch/issues?key=" + DUMMY_KEY, new MockResponse().setResponseCode(404));

    IssueDownloader issueDownloader = new IssueDownloader(mockServer.slClient(), issueStorePaths);
    List<ServerIssue> issues = issueDownloader.download(DUMMY_KEY, projectConfiguration, true, PROGRESS);
    assertThat(issues).isEmpty();
  }
}
