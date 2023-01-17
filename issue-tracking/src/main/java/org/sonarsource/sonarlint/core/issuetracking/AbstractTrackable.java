/*
 * SonarLint Issue Tracking
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
package org.sonarsource.sonarlint.core.issuetracking;

import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;

public class AbstractTrackable<G> implements Trackable<G> {
  private final G clientObject;
  protected String ruleKey;
  protected IssueSeverity severity;
  protected RuleType type;
  protected String message;
  protected Integer line;
  protected String lineHash;
  protected TextRangeWithHash textRange;
  protected Long creationDate;
  protected String serverIssueKey;
  protected boolean resolved;

  protected AbstractTrackable(Trackable<G> trackable) {
    this.clientObject = trackable.getClientObject();
    // copy fieds instead of using given trackable to avoid always increase level of proxying
    this.ruleKey = trackable.getRuleKey();
    this.severity = trackable.getSeverity();
    this.type = trackable.getType();
    this.message = trackable.getMessage();
    this.line = trackable.getLine();
    this.lineHash = trackable.getLineHash();
    this.textRange = trackable.getTextRange();
    this.creationDate = trackable.getCreationDate();
    this.serverIssueKey = trackable.getServerIssueKey();
    this.resolved = trackable.isResolved();
  }

  @Override
  public G getClientObject() {
    return clientObject;
  }

  @Override
  public String getRuleKey() {
    return ruleKey;
  }

  @Override
  public IssueSeverity getSeverity() {
    return severity;
  }

  @Override
  public RuleType getType() {
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
  public String getLineHash() {
    return lineHash;
  }

  @Override
  public TextRangeWithHash getTextRange() {
    return textRange;
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
}
