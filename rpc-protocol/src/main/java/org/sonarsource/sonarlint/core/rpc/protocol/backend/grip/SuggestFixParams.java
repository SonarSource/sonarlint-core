/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.grip;

import java.net.URI;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

public class SuggestFixParams {
  private final String authenticationToken;
  private final String configurationScopeId;
  private final URI fileUri;
  private final String issueMessage;
  private final TextRangeDto issueRange;
  private final String ruleKey;

  public SuggestFixParams(String authenticationToken, String configurationScopeId, URI fileUri, String issueMessage, TextRangeDto issueRange, String ruleKey) {
    this.authenticationToken = authenticationToken;
    this.configurationScopeId = configurationScopeId;
    this.fileUri = fileUri;
    this.issueMessage = issueMessage;
    this.issueRange = issueRange;
    this.ruleKey = ruleKey;
  }

  public String getAuthenticationToken() {
    return authenticationToken;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public URI getFileUri() {
    return fileUri;
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
