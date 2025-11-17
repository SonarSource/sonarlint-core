/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core;

import org.sonarsource.sonarlint.core.commons.NewCodeDefinition;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.MQRModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;
import org.sonarsource.sonarlint.core.rules.RuleDetailsAdapter;
import org.sonarsource.sonarlint.core.tracking.TrackedIssue;

import static java.util.Objects.requireNonNull;
import static org.sonarsource.sonarlint.core.tracking.TextRangeUtils.toTextRangeDto;

public class DtoMapper {

  private DtoMapper() {
    // util
  }

  public static RaisedIssueDto toRaisedIssueDto(TrackedIssue issue, NewCodeDefinition newCodeDefinition, boolean isMQRMode, boolean isAiCodeFixable) {
    return new RaisedIssueDto(issue.getId(), issue.getServerKey(), issue.getRuleKey(), issue.getMessage(),
      isMQRMode ? Either.forRight(new MQRModeDetails(RuleDetailsAdapter.adapt(issue.getCleanCodeAttribute()), RuleDetailsAdapter.toDto(issue.getImpacts())))
        : Either.forLeft(new StandardModeDetails(RuleDetailsAdapter.adapt(issue.getSeverity()), RuleDetailsAdapter.adapt(issue.getType()))),
      requireNonNull(issue.getIntroductionDate()), newCodeDefinition.isOnNewCode(issue.getIntroductionDate()), issue.isResolved(),
      toTextRangeDto(issue.getTextRangeWithHash()),
      issue.getFlows().stream().map(RuleDetailsAdapter::adapt).toList(),
      issue.getQuickFixes().stream().map(RuleDetailsAdapter::adapt).toList(),
      issue.getRuleDescriptionContextKey(), isAiCodeFixable,
      issue.getResolutionStatus());
  }

  public static RaisedHotspotDto toRaisedHotspotDto(TrackedIssue issue, NewCodeDefinition newCodeDefinition, boolean isMQRMode) {
    var status = issue.getHotspotStatus();
    status = status != null ? status : HotspotStatus.TO_REVIEW;
    var vp = RuleDetailsAdapter.adapt(issue.getVulnerabilityProbability());
    if (vp == null) {
      // this should not normally happen because all hotspots supposed to have the vulnerability probability set
      throw new IllegalStateException("Vulnerability probability should be set for security hotspots");
    }
    return new RaisedHotspotDto(issue.getId(), issue.getServerKey(), issue.getRuleKey(), issue.getMessage(),
      isMQRMode && !issue.getImpacts().isEmpty() ?
        Either.forRight(new MQRModeDetails(RuleDetailsAdapter.adapt(issue.getCleanCodeAttribute()), RuleDetailsAdapter.toDto(issue.getImpacts())))
        : Either.forLeft(new StandardModeDetails(RuleDetailsAdapter.adapt(issue.getSeverity()), RuleDetailsAdapter.adapt(issue.getType()))),
      requireNonNull(issue.getIntroductionDate()), newCodeDefinition.isOnNewCode(issue.getIntroductionDate()), issue.isResolved(),
      toTextRangeDto(issue.getTextRangeWithHash()),
      issue.getFlows().stream().map(RuleDetailsAdapter::adapt).toList(),
      issue.getQuickFixes().stream().map(RuleDetailsAdapter::adapt).toList(),
      issue.getRuleDescriptionContextKey(), vp, status);
  }

}
