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

import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.LocallyTrackedIssueDto;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;

public class LocallyTrackedIssueTrackable implements Trackable {
  private final LocallyTrackedIssueDto locallyTrackedIssue;

  public LocallyTrackedIssueTrackable(LocallyTrackedIssueDto locallyTrackedIssue) {
    this.locallyTrackedIssue = locallyTrackedIssue;
  }

  public LocallyTrackedIssueDto getLocallyTrackedIssue() {
    return locallyTrackedIssue;
  }

  @Override
  public Object getClientObject() {
    return null;
  }

  @Override
  public String getRuleKey() {
    return locallyTrackedIssue.getRuleKey();
  }

  @Override
  public IssueSeverity getSeverity() {
    return null;
  }

  @Override
  public String getMessage() {
    return locallyTrackedIssue.getMessage();
  }

  @Nullable
  @Override
  public RuleType getType() {
    return null;
  }

  @Nullable
  @Override
  public Integer getLine() {
    return locallyTrackedIssue.getLineWithHash().getNumber();
  }

  @Nullable
  @Override
  public String getLineHash() {
    return locallyTrackedIssue.getLineWithHash().getHash();
  }

  @Nullable
  @Override
  public TextRangeWithHash getTextRange() {
    var issueRange = locallyTrackedIssue.getTextRangeWithHash();
    return new TextRangeWithHash(issueRange.getStartLine(), issueRange.getStartLineOffset(), issueRange.getEndLine(), issueRange.getEndLineOffset(), issueRange.getHash());
  }

  @Nullable
  @Override
  public Long getCreationDate() {
    return null;
  }

  @Nullable
  @Override
  public String getServerIssueKey() {
    return locallyTrackedIssue.getKey();
  }

  @Override
  public boolean isResolved() {
    return false;
  }

  @Nullable
  @Override
  public HotspotReviewStatus getReviewStatus() {
    return null;
  }
}
