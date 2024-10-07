/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.grip.web.api;

import java.net.URI;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

public class SuggestFixWebApiRequest {
  private final URI serviceURI;
  private final String authenticationToken;
  private final String promptId;
  private final String fileContent;
  private final String issueMessage;
  private final TextRangeDto issueRange;
  private final String ruleKey;

  public SuggestFixWebApiRequest(URI serviceURI, String authenticationToken, String promptId, String fileContent, String issueMessage, TextRangeDto issueRange, String ruleKey) {
    this.serviceURI = serviceURI;
    this.authenticationToken = authenticationToken;
    this.promptId = promptId;
    this.fileContent = fileContent;
    this.issueMessage = issueMessage;
    this.issueRange = issueRange;
    this.ruleKey = ruleKey;
  }

  public URI getServiceURI() {
    return serviceURI;
  }

  public String getAuthenticationToken() {
    return authenticationToken;
  }

  public String getPromptId() {
    return promptId;
  }

  public String getFileContent() {
    return fileContent;
  }

  public String getIssueMessage() {
    return issueMessage;
  }

  public TextRangeDto getIssueRange() {
    return issueRange;
  }

  public String getRuleKey() {
    return ruleKey;
  }
}
