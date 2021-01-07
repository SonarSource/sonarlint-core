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

import com.google.protobuf.Parser;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class IssueDownloaderImpl implements IssueDownloader {

  private static final Logger LOG = Loggers.get(IssueDownloaderImpl.class);

  private final SonarLintWsClient wsClient;

  public IssueDownloaderImpl(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  /**
   * Fetch all issues of the component with specified key.
   * If the component doesn't exist or it exists but has no issues, an empty iterator is returned.
   * See also sonarqube:/web_api/batch
   *
   * @param key project key, module key, or file key.
   * @return Iterator of issues. It can be empty but never null.
   */
  @Override
  public List<ScannerInput.ServerIssue> apply(String key) {
    return SonarLintWsClient.processTimed(
      () -> wsClient.rawGet(getIssuesUrl(key)),
      response -> {
        if (response.code() == 403 || response.code() == 404) {
          return Collections.emptyList();
        } else if (response.code() != 200) {
          throw SonarLintWsClient.handleError(response);
        }
        InputStream input = response.contentStream();
        Parser<ScannerInput.ServerIssue> parser = ScannerInput.ServerIssue.parser();
        return ProtobufUtil.readMessages(input, parser);
      },
      duration -> LOG.debug("Downloaded issues in {}ms", duration));
  }

  private static String getIssuesUrl(String key) {
    return "/batch/issues?key=" + StringUtils.urlEncode(key);
  }
}
