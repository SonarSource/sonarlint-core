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

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.container.storage.ProtobufUtilTest.toByteArray;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;

public class IssueDownloaderImplTest {
  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Test
  public void test_download() throws IOException {
    ScannerInput.ServerIssue issue = ScannerInput.ServerIssue.newBuilder().build();

    SonarLintWsClient wsClient = WsClientTestUtils.createMock();

    String key = "dummyKey";
    try (InputStream inputStream = new ByteArrayInputStream(toByteArray(issue))) {
      WsClientTestUtils.addResponse(wsClient, "/batch/issues?key=" + key, inputStream);
    }

    IssueDownloader issueDownloader = new IssueDownloaderImpl(wsClient);
    assertThat(issueDownloader.apply(key)).containsOnly(issue);
  }

  @Test
  public void test_code403() throws IOException {
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    String key = "dummyKey";
    WsClientTestUtils.addFailedResponse(wsClient, "/batch/issues?key=" + key, 403, "");

    IssueDownloader issueDownloader = new IssueDownloaderImpl(wsClient);
    assertThat(issueDownloader.apply(key)).isEmpty();
  }

  @Test
  public void test_fail_other_codes() throws IOException {
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    String key = "dummyKey";
    WsClientTestUtils.addFailedResponse(wsClient, "/batch/issues?key=" + key, 503, "");

    IssueDownloader issueDownloader = new IssueDownloaderImpl(wsClient);
    exception.expect(IllegalStateException.class);
    exception.expectMessage("Error 503");
    issueDownloader.apply(key);
  }

  @Test
  public void test_code404() throws IOException {
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    String key = "dummyKey";
    WsClientTestUtils.addFailedResponse(wsClient, "/batch/issues?key=" + key, 404, "");

    IssueDownloader issueDownloader = new IssueDownloaderImpl(wsClient);
    assertThat(issueDownloader.apply(key)).isEmpty();
  }
}
