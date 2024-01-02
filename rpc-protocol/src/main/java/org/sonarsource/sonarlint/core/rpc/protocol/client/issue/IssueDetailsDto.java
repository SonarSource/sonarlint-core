/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.issue;

import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.common.FlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

public class IssueDetailsDto {
  private final String issueKey;
  private final String ruleKey;
  private final Path ideFilePath;
  private final String branch;
  @Nullable
  private final String pullRequest;
  private final String message;
  private final String creationDate;
  private final String codeSnippet;
  private final boolean isTaint;
  private final List<FlowDto> flows;
  private final TextRangeDto textRange;

  public IssueDetailsDto(TextRangeDto textRange, String ruleKey, String issueKey,
    Path ideFilePath, String branch, @Nullable String pullRequest, String message, String creationDate,
    String codeSnippet, boolean isTaint, List<FlowDto> flows) {
    this.issueKey = issueKey;
    this.ruleKey = ruleKey;
    this.textRange = textRange;
    this.ideFilePath = ideFilePath;
    this.branch = branch;
    this.pullRequest = pullRequest;
    this.message = message;
    this.creationDate = creationDate;
    this.codeSnippet = codeSnippet;
    this.isTaint = isTaint;
    this.flows = flows;
  }

  public TextRangeDto getTextRange() {
    return textRange;
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

  public Path getIdeFilePath() {
    return ideFilePath;
  }

  public String getBranch() {
    return branch;
  }

  @Nullable
  public String getPullRequest() {
    return pullRequest;
  }

  public String getCodeSnippet() {
    return codeSnippet;
  }

  public String getMessage() {
    return message;
  }

  public boolean isTaint() {
    return isTaint;
  }

  public List<FlowDto> getFlows() {
    return flows;
  }
}
