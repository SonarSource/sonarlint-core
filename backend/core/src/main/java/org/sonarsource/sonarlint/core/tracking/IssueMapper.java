/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.RawIssue;
import org.sonarsource.sonarlint.core.commons.IssueStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;

import static org.sonarsource.sonarlint.core.tracking.TextRangeUtils.getLineWithHash;
import static org.sonarsource.sonarlint.core.tracking.TextRangeUtils.getTextRangeWithHash;

public class IssueMapper {

  private static final Map<IssueStatus, ResolutionStatus> STATUS_MAPPING = statusMapping();

  private IssueMapper() {
    // utils
  }

  public static TrackedIssue toTrackedIssue(RawIssue issue, Instant introductionDate) {
    return new TrackedIssue(UUID.randomUUID(), issue.getMessage(), introductionDate, false, issue.getSeverity(),
      issue.getRuleType(), issue.getRuleKey(), getTextRangeWithHash(issue.getTextRange(),
      issue.getClientInputFile()), getLineWithHash(issue.getTextRange(),
      issue.getClientInputFile()), null, issue.getImpacts(), issue.getFlows(), issue.getQuickFixes(),
      issue.getVulnerabilityProbability(), null, null, issue.getRuleDescriptionContextKey(), issue.getCleanCodeAttribute(), issue.getFileUri());
  }

  public static ResolutionStatus mapStatus(@Nullable IssueStatus status) {
    return STATUS_MAPPING.get(status);
  }

  private static EnumMap<IssueStatus, ResolutionStatus> statusMapping() {
    return new EnumMap<>(Map.of(
      IssueStatus.ACCEPT, ResolutionStatus.ACCEPT,
      IssueStatus.FALSE_POSITIVE, ResolutionStatus.FALSE_POSITIVE,
      IssueStatus.WONT_FIX, ResolutionStatus.WONT_FIX
    ));
  }
}
