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
import org.sonarqube.ws.Common.Paging;
import org.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;
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
        .setCreationDate("2021-01-11T18:17:31+0000")
        .setComponent("project:foo/bar/Hello.java"))
      .addComponents(Issues.Component.newBuilder()
        .setKey("project:foo/bar/Hello.java")
        .setPath("foo/bar/Hello.java"))
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
        WsClientTestUtils.addResponse(wsClient, "/api/issues/search.protobuf?componentKeys=" + key + "&ps=500&p=1", inputStream);
      }
    }

    IssueDownloader issueDownloader = new IssueDownloader(wsClient, issueStorePaths);
    List<ServerIssue> issues = issueDownloader.download(key, projectConfiguration, PROGRESS);
    assertThat(issues).hasSize(1);
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
        WsClientTestUtils.addResponse(wsClient, "/api/issues/search.protobuf?componentKeys=" + key + "&ps=500&p=1", inputStream);
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
    WsClientTestUtils.addFailedResponse(wsClient, "/api/issues/search.protobuf?componentKeys=" + key + "&ps=500&p=1", 503, "");

    IssueDownloader issueDownloader = new IssueDownloader(wsClient, issueStorePaths);
    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> issueDownloader.download(key, projectConfiguration, PROGRESS));
    assertThat(thrown).hasMessageContaining("Error 503");
  }
}
