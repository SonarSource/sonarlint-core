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
package org.sonarsource.sonarlint.core.tracking;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.client.api.common.ExtendedIssue;
import org.sonarsource.sonarlint.core.client.api.common.TextRange;

import static org.sonarsource.sonarlint.core.tracking.DigestUtils.digest;

public class IssueTrackable implements Trackable {

  private final ExtendedIssue issue;
  private final TextRange textRange;
  private final Integer textRangeHash;
  private final Integer lineHash;

  public IssueTrackable(ExtendedIssue issue) {
    this(issue, null, null, null);
  }

  public IssueTrackable(ExtendedIssue issue, @Nullable TextRange textRange, @Nullable String textRangeContent, @Nullable String lineContent) {
    this.issue = issue;
    this.textRange = textRange;
    this.textRangeHash = hashOrNull(textRangeContent);
    this.lineHash = hashOrNull(lineContent);
  }

  private static Integer hashOrNull(@Nullable String content) {
    return content != null ? digest(content).hashCode() : null;
  }

  @Override
  public Issue getIssue() {
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
  public TextRange getTextRange() {
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

  @Override
  public String getAssignee() {
    return "";
  }
}
