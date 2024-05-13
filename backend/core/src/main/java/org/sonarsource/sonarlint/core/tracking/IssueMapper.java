/*
 * SonarLint Core - Implementation
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

package org.sonarsource.sonarlint.core.tracking;

import java.util.UUID;
import org.sonarsource.sonarlint.core.analysis.RawIssue;

import static org.sonarsource.sonarlint.core.tracking.TextRangeUtils.getLineWithHash;
import static org.sonarsource.sonarlint.core.tracking.TextRangeUtils.getTextRangeWithHash;

public class IssueMapper {

  private IssueMapper() {
    // utils
  }

  public static TrackedIssue toTrackedIssue(RawIssue issue) {
    return new TrackedIssue(UUID.randomUUID(), issue.getMessage(), null, false, issue.getSeverity(),
      issue.getRuleType(), issue.getRuleKey(), true, getTextRangeWithHash(issue.getTextRange(),
      issue.getClientInputFile()), getLineWithHash(issue.getTextRange(),
      issue.getClientInputFile()), null, issue.getImpacts(), issue.getFlows(), issue.getQuickFixes(),
      issue.getVulnerabilityProbability(), null,  issue.getRuleDescriptionContextKey(), issue.getCleanCodeAttribute(), issue.getFileUri());
  }

}
