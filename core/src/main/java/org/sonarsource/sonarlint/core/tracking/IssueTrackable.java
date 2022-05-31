/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.tracking;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;
import org.sonarsource.sonarlint.core.serverconnection.ServerIssue;

import static org.sonarsource.sonarlint.core.tracking.DigestUtils.digest;

public class IssueTrackable implements Trackable<Issue> {

  private final Issue issue;
  private final org.sonarsource.sonarlint.core.issuetracking.TextRange textRange;
  private final Integer textRangeHash;
  private final Integer lineHash;

  public IssueTrackable(Issue issue) {
    this(issue, null, null, null);
  }

  public IssueTrackable(Issue issue, @Nullable ServerIssue.TextRange textRange, @Nullable String textRangeContent,
    @Nullable String lineContent) {
    this.issue = issue;
    this.textRange = textRange != null ? convertToTrackingTextRange(textRange) : null;
    this.textRangeHash = hashOrNull(textRangeContent);
    this.lineHash = hashOrNull(lineContent);
  }

  static org.sonarsource.sonarlint.core.issuetracking.TextRange convertToTrackingTextRange(ServerIssue.TextRange fromAnalysis) {
    return new org.sonarsource.sonarlint.core.issuetracking.TextRange(fromAnalysis.getStartLine(), fromAnalysis.getStartLineOffset(), fromAnalysis.getEndLine(),
      fromAnalysis.getEndLineOffset());
  }

  private static Integer hashOrNull(@Nullable String content) {
    return content != null ? digest(content).hashCode() : null;
  }

  @Override
  public Issue getClientObject() {
    return issue;
  }

  @Override
  public String getRuleKey() {
    return issue.getRuleKey();
  }

  @Override
  public String getSeverity() {
    return issue.getSeverity();
  }

  @Override
  public String getType() {
    return issue.getType();
  }

  @Override
  public String getMessage() {
    return issue.getMessage();
  }

  @Override
  public Integer getLine() {
    return issue.getStartLine();
  }

  @Override
  public Integer getLineHash() {
    return lineHash;
  }

  @Override
  public org.sonarsource.sonarlint.core.issuetracking.TextRange getTextRange() {
    return textRange;
  }

  @Override
  public Integer getTextRangeHash() {
    return textRangeHash;
  }

  @Override
  public Long getCreationDate() {
    return null;
  }

  @Override
  public String getServerIssueKey() {
    return null;
  }

  @Override
  public boolean isResolved() {
    return false;
  }
}
