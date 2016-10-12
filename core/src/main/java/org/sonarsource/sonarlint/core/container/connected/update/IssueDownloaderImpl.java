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

import com.google.protobuf.Parser;
import java.io.InputStream;
import java.util.Iterator;

import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class IssueDownloaderImpl implements IssueDownloader {

  private final SonarLintWsClient wsClient;

  public IssueDownloaderImpl(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  /**
   * Fetch all issues of the component with specified key.
   * See also sonarqube:/web_api/batch
   *
   * @param key project key, module key, or file key.
   * @return iterable of issues
   */
  @Override
  public Iterator<ScannerInput.ServerIssue> apply(String key) {
    InputStream input = wsClient.get(getIssuesUrl(key)).contentStream();
    Parser<ScannerInput.ServerIssue> parser = ScannerInput.ServerIssue.parser();
    return ProtobufUtil.streamMessages(input, parser);
  }

  private static String getIssuesUrl(String key) {
    return "/batch/issues?key=" + StringUtils.urlEncode(key);
  }
}
