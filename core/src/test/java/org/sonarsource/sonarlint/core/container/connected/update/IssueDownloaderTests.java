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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Common.Flow;
import org.sonarqube.ws.Common.Paging;
import org.sonarqube.ws.Common.TextRange;
import org.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue.Location;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssueDownloaderTests {
  private static final ProgressWrapper PROGRESS = new ProgressWrapper(null);

  private final Sonarlint.ProjectConfiguration projectConfiguration = Sonarlint.ProjectConfiguration.newBuilder().build();
  private final IssueStorePaths issueStorePaths = new IssueStorePaths();

  @Test
  void test_download_one_issue() throws IOException {
    Issues.SearchWsResponse response = Issues.SearchWsResponse.newBuilder()
      .addIssues(Issues.Issue.newBuilder()
        .setRule("sonarjava:S123")
        .setHash("hash")
        .setMessage("Primary message")
        .setTextRange(TextRange.newBuilder().setStartLine(1).setStartOffset(2).setEndLine(3).setEndOffset(4))
        .setCreationDate("2021-01-11T18:17:31+0000")
        .setComponent("project:foo/bar/Hello.java")
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

    SonarLintWsClient wsClient = WsClientTestUtils.createMock();

    String key = "dummyKey";
    try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
      response.writeTo(byteStream);
      try (InputStream inputStream = new ByteArrayInputStream(byteStream.toByteArray())) {
        WsClientTestUtils.addResponse(wsClient,
          "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED,RESOLVED&types=CODE_SMELL,BUG,VULNERABILITY&componentKeys=" + key + "&ps=500&p=1", inputStream);
      }
    }

    IssueDownloader issueDownloader = new IssueDownloader(wsClient, issueStorePaths);
    List<ServerIssue> issues = issueDownloader.download(key, projectConfiguration, PROGRESS);
    assertThat(issues).hasSize(1);

    ServerIssue serverIssue = issues.get(0);
    assertThat(serverIssue.getLineHash()).isEqualTo("hash");
    assertThat(serverIssue.getPrimaryLocation().getMsg()).isEqualTo("Primary message");
    assertThat(serverIssue.getPrimaryLocation().getPath()).isEqualTo("foo/bar/Hello.java");
    assertThat(serverIssue.getPrimaryLocation().getTextRange().getStartLine()).isEqualTo(1);
    assertThat(serverIssue.getPrimaryLocation().getTextRange().getStartOffset()).isEqualTo(2);
    assertThat(serverIssue.getPrimaryLocation().getTextRange().getEndLine()).isEqualTo(3);
    assertThat(serverIssue.getPrimaryLocation().getTextRange().getEndOffset()).isEqualTo(4);

    assertThat(serverIssue.getFlowList()).hasSize(2);
    assertThat(serverIssue.getFlow(0).getLocationList()).hasSize(2);
    Location flowLocation12 = serverIssue.getFlow(0).getLocation(1);
    assertThat(flowLocation12.getMsg()).isEqualTo("Flow 1 - Location 2");
    assertThat(flowLocation12.getPath()).isEqualTo("foo/bar/Hello2.java");
    assertThat(flowLocation12.getTextRange().getStartLine()).isEqualTo(9);
    assertThat(flowLocation12.getTextRange().getStartOffset()).isEqualTo(10);
    assertThat(flowLocation12.getTextRange().getEndLine()).isEqualTo(11);
    assertThat(flowLocation12.getTextRange().getEndOffset()).isEqualTo(12);

    assertThat(serverIssue.getFlow(1).getLocationList()).hasSize(2);
  }

  @Test
  void test_download_no_issues() throws IOException {
    Issues.SearchWsResponse response = Issues.SearchWsResponse.newBuilder()
      .setPaging(Paging.newBuilder()
        .setPageIndex(1)
        .setPageSize(500)
        .setTotal(0))
      .build();

    SonarLintWsClient wsClient = WsClientTestUtils.createMock();

    String key = "dummyKey";
    try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
      response.writeTo(byteStream);
      try (InputStream inputStream = new ByteArrayInputStream(byteStream.toByteArray())) {
        WsClientTestUtils.addResponse(wsClient,
          "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED,RESOLVED&types=CODE_SMELL,BUG,VULNERABILITY&componentKeys=" + key + "&ps=500&p=1", inputStream);
      }
    }

    IssueDownloader issueDownloader = new IssueDownloader(wsClient, issueStorePaths);
    List<ServerIssue> issues = issueDownloader.download(key, projectConfiguration, PROGRESS);
    assertThat(issues).isEmpty();
  }

  @Test
  void test_fail_other_codes() throws IOException {
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    String key = "dummyKey";
    WsClientTestUtils.addFailedResponse(wsClient,
      "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED,RESOLVED&types=CODE_SMELL,BUG,VULNERABILITY&componentKeys=" + key + "&ps=500&p=1", 503, "");

    IssueDownloader issueDownloader = new IssueDownloader(wsClient, issueStorePaths);
    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> issueDownloader.download(key, projectConfiguration, PROGRESS));
    assertThat(thrown).hasMessageContaining("Error 503");
  }
}
