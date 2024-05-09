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
package org.sonarsource.sonarlint.core;

import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.analysis.RuleDetailsForAnalysis;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetRuleDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rules.RuleDetailsAdapter;
import org.sonarsource.sonarlint.core.tracking.TrackedIssue;

import static org.sonarsource.sonarlint.core.tracking.TextRangeUtils.toTextRangeDto;

public class DtoMapper {

  private DtoMapper() {
    // util
  }

  public static GetRuleDetailsResponse toRuleDetailsResponse(RuleDetailsForAnalysis ruleDetails) {
    return new GetRuleDetailsResponse(
      RuleDetailsAdapter.adapt(ruleDetails.getSeverity()),
      RuleDetailsAdapter.adapt(ruleDetails.getType()),
      RuleDetailsAdapter.adapt(ruleDetails.getCleanCodeAttribute()),
      ruleDetails.getImpacts().entrySet().stream().map(entry -> new ImpactDto(RuleDetailsAdapter.adapt(entry.getKey()), RuleDetailsAdapter.adapt(entry.getValue())))
        .collect(Collectors.toList()), RuleDetailsAdapter.adapt(ruleDetails.getVulnerabilityProbability()));
  }

  public static RaisedIssueDto toRaisedIssueDto(TrackedIssue issue) {
    return new RaisedIssueDto(issue.getId(), issue.getServerKey(), issue.getRuleKey(), issue.getMessage(),
      RuleDetailsAdapter.adapt(issue.getSeverity()),
      RuleDetailsAdapter.adapt(issue.getType()),
      RuleDetailsAdapter.adapt(issue.getCleanCodeAttribute()), RuleDetailsAdapter.toDto(issue.getImpacts()),
      issue.getIntroductionDate(), issue.isOnNewCode(), issue.isResolved(),
      toTextRangeDto(issue.getTextRangeWithHash()),
      issue.getFlows().stream().map(RuleDetailsAdapter::adapt).collect(Collectors.toList()),
      issue.getQuickFixes().stream().map(RuleDetailsAdapter::adapt).collect(Collectors.toList()),
      issue.getRuleDescriptionContextKey());
  }

  public static RaisedHotspotDto toRaisedHotspotDto(TrackedIssue issue) {
    return new RaisedHotspotDto(issue.getId(), issue.getServerKey(), issue.getRuleKey(), issue.getMessage(),
      RuleDetailsAdapter.adapt(issue.getSeverity()),
      RuleDetailsAdapter.adapt(issue.getType()),
      RuleDetailsAdapter.adapt(issue.getCleanCodeAttribute()), RuleDetailsAdapter.toDto(issue.getImpacts()),
      issue.getIntroductionDate(), issue.isOnNewCode(), issue.isResolved(),
      toTextRangeDto(issue.getTextRangeWithHash()),
      issue.getFlows().stream().map(RuleDetailsAdapter::adapt).collect(Collectors.toList()),
      issue.getQuickFixes().stream().map(RuleDetailsAdapter::adapt).collect(Collectors.toList()),
      issue.getRuleDescriptionContextKey(), RuleDetailsAdapter.adapt(issue.getVulnerabilityProbability()));
  }


}
