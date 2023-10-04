/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.client.issue;

import java.util.List;
import org.sonarsource.sonarlint.core.clientapi.common.FlowDto;
import org.sonarsource.sonarlint.core.clientapi.common.TextRangeDto;

public class ShowIssueParams {
  private final String serverRelativeFilePath;
  private final String message;
  private final String configScopeId;
  private final String ruleKey;
  private final String issueKey;
  private final String creationDate;
  private final String codeSnippet;
  private final List<FlowDto> flows;
  private final TextRangeDto textRange;

  public ShowIssueParams(TextRangeDto textRange, String configScopeId, String ruleKey, String issueKey, String serverRelativeFilePath, String message,
    String creationDate, String codeSnippet, List<FlowDto> flows) {
    this.textRange = textRange;
    this.configScopeId = configScopeId;
    this.ruleKey = ruleKey;
    this.issueKey = issueKey;
    this.serverRelativeFilePath = serverRelativeFilePath;
    this.message = message;
    this.creationDate = creationDate;
    this.codeSnippet = codeSnippet;
    this.flows = flows;
  }

  public TextRangeDto getTextRange() {
    return textRange;
  }

  public String getConfigScopeId() {
    return configScopeId;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  public String getIssueKey() {
    return issueKey;
  }

  public String getCreationDate() {
    return creationDate;
  }

  public String getServerRelativeFilePath() {
    return serverRelativeFilePath;
  }

  public String getCodeSnippet() {
    return codeSnippet;
  }

  public String getMessage() {
    return message;
  }

  public List<FlowDto> getFlows() {
    return flows;
  }
}
