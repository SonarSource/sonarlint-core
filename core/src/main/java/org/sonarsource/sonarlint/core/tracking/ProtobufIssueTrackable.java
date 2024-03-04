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

import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Issues.Issue;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class ProtobufIssueTrackable implements Trackable {

  private final Issue issue;

  public ProtobufIssueTrackable(Issue issue) {
    this.issue = issue;
  }

  @Override
  public org.sonarsource.sonarlint.core.client.api.common.analysis.Issue getIssue() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Integer getLine() {
    return issue.getLine() != 0 ? issue.getLine() : null;
  }

  @Override
  public String getMessage() {
    return issue.getMessage();
  }

  @Override
  public Integer getTextRangeHash() {
    return null;
  }

  @Override
  public Integer getLineHash() {
    return issue.getChecksum();
  }

  @Override
  public String getRuleKey() {
    return issue.getRuleKey();
  }

  @Override
  public String getRuleName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getServerIssueKey() {
    return !StringUtils.isEmpty(issue.getServerIssueKey()) ? issue.getServerIssueKey() : null;
  }

  @Override
  public Long getCreationDate() {
    return issue.getCreationDate() != 0 ? issue.getCreationDate() : null;
  }

  @Override
  public boolean isResolved() {
    return issue.getResolved();
  }

  @Override
  public String getAssignee() {
    return issue.getAssignee();
  }

  @Override
  public String getSeverity() {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public String getType() {
    throw new UnsupportedOperationException();
  }

  @Override
  public TextRange getTextRange() {
    throw new UnsupportedOperationException();
  }
}
