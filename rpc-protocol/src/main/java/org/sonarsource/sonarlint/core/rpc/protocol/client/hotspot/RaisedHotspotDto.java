/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueFlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.QuickFixDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

public class RaisedHotspotDto extends RaisedFindingDto {

  private final HotspotStatus status;
  private final VulnerabilityProbability vulnerabilityProbability;

  public RaisedHotspotDto(UUID id, @Nullable String serverKey, String ruleKey, String primaryMessage, IssueSeverity severity, RuleType type,
    CleanCodeAttribute cleanCodeAttribute, List<ImpactDto> impacts, Instant introductionDate, boolean isOnNewCode, boolean resolved, @Nullable TextRangeDto textRange,
    List<IssueFlowDto> flows, List<QuickFixDto> quickFixes, @Nullable String ruleDescriptionContextKey, @Nullable VulnerabilityProbability vulnerabilityProbability,
    HotspotStatus status) {
    super(id, serverKey, ruleKey, primaryMessage, severity, type, cleanCodeAttribute, impacts, introductionDate,
      isOnNewCode, resolved, textRange, flows,quickFixes, ruleDescriptionContextKey);
    this.vulnerabilityProbability = vulnerabilityProbability;
    this.status = status;
  }

  @CheckForNull
  public VulnerabilityProbability getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }

  public HotspotStatus getStatus() {
    return status;
  }
}
