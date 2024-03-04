/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.tracking;

import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;

public class LocalOnlyIssueTrackable implements Trackable {

  private final LocalOnlyIssue localOnlyIssue;

  public LocalOnlyIssueTrackable(LocalOnlyIssue localOnlyIssue) {
    this.localOnlyIssue = localOnlyIssue;
  }

  public LocalOnlyIssue getLocalOnlyIssue() {
    return localOnlyIssue;
  }

  @Override
  public Object getClientObject() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getRuleKey() {
    return localOnlyIssue.getRuleKey();
  }

  @Override
  public IssueSeverity getSeverity() {
    return null;
  }

  @Override
  public RuleType getType() {
    return null;
  }

  @Override
  public String getMessage() {
    return localOnlyIssue.getMessage();
  }

  @Override
  public Integer getLine() {
    var line = localOnlyIssue.getLineWithHash();
    return line == null ? null : line.getNumber();
  }

  @Override
  public String getLineHash() {
    var line = localOnlyIssue.getLineWithHash();
    return line == null ? null : line.getHash();
  }

  @Override
  public TextRangeWithHash getTextRange() {
    return localOnlyIssue.getTextRangeWithHash();
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
    return localOnlyIssue.getResolution() != null;
  }

  @Override
  public HotspotReviewStatus getReviewStatus() {
    return null;
  }
}
