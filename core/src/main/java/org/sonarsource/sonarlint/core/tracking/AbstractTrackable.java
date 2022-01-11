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

import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

public class AbstractTrackable implements Trackable {
  protected Issue issue;
  protected String ruleKey;
  protected String ruleName;
  protected String severity;
  protected String type;
  protected String message;
  protected Integer line;
  protected Integer lineHash;
  protected TextRange textRange;
  protected Integer textRangeHash;
  protected Long creationDate;
  protected String serverIssueKey;
  protected boolean resolved;
  protected String assignee;

  protected AbstractTrackable(Trackable trackable) {
    // copy fieds instead of using given trackable to avoid always increase level of proxying
    this.issue = trackable.getIssue();
    this.ruleKey = trackable.getRuleKey();
    this.ruleName = trackable.getRuleName();
    this.severity = trackable.getSeverity();
    this.type = trackable.getType();
    this.message = trackable.getMessage();
    this.line = trackable.getLine();
    this.lineHash = trackable.getLineHash();
    this.textRange = trackable.getTextRange();
    this.textRangeHash = trackable.getTextRangeHash();
    this.creationDate = trackable.getCreationDate();
    this.serverIssueKey = trackable.getServerIssueKey();
    this.resolved = trackable.isResolved();
    this.assignee = trackable.getAssignee();
  }

  @Override
  public Issue getIssue() {
    return issue;
  }

  @Override
  public String getRuleKey() {
    return ruleKey;
  }

  @Override
  public String getRuleName() {
    return ruleName;
  }

  @Override
  public String getSeverity() {
    return severity;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public Integer getLine() {
    return line;
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
    return creationDate;
  }

  @Override
  public String getServerIssueKey() {
    return serverIssueKey;
  }

  @Override
  public boolean isResolved() {
    return resolved;
  }

  @Override
  public String getAssignee() {
    return assignee;
  }
}
