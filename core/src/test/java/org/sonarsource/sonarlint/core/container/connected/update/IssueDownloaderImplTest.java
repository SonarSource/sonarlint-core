/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.container.storage.ProtobufUtilTest.toByteArray;

public class IssueDownloaderImplTest {

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
}
