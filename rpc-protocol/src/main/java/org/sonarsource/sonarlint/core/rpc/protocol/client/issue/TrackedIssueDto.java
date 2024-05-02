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
package org.sonarsource.sonarlint.core.rpc.protocol.client.issue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

public class TrackedIssueDto {
  private final UUID id;
  @Nullable
  private final String serverKey;
  private final String ruleKey;
  private final String primaryMessage;
  private final IssueSeverity severity;
  private final RuleType type;
  private final CleanCodeAttribute cleanCodeAttribute;
  private final List<ImpactDto> impacts;
  private final Instant introductionDate;
  private final boolean isOnNewCode;
  private final boolean resolved;
  @Nullable
  private final TextRangeDto textRange;
  private final List<IssueFlowDto> flows;
  private final List<QuickFixDto> quickFixes;
  @Nullable
  private final String ruleDescriptionContextKey;
  @Nullable
  private final VulnerabilityProbability vulnerabilityProbability;

  public TrackedIssueDto(UUID id, @Nullable String serverKey, String ruleKey, String primaryMessage, IssueSeverity severity, RuleType type, CleanCodeAttribute cleanCodeAttribute,
    List<ImpactDto> impacts, Instant introductionDate, boolean isOnNewCode, boolean resolved, @Nullable TextRangeDto textRange, List<IssueFlowDto> flows,
    List<QuickFixDto> quickFixes, @Nullable String ruleDescriptionContextKey, @Nullable VulnerabilityProbability vulnerabilityProbability) {
    this.id = id;
    this.serverKey = serverKey;
    this.ruleKey = ruleKey;
    this.primaryMessage = primaryMessage;
    this.severity = severity;
    this.type = type;
    this.cleanCodeAttribute = cleanCodeAttribute;
    this.impacts = impacts;
    this.introductionDate = introductionDate;
    this.isOnNewCode = isOnNewCode;
    this.resolved = resolved;
    this.textRange = textRange;
    this.flows = flows;
    this.quickFixes = quickFixes;
    this.ruleDescriptionContextKey = ruleDescriptionContextKey;
    this.vulnerabilityProbability = vulnerabilityProbability;
  }

  public UUID getId() {
    return id;
  }

  @CheckForNull
  public String getServerKey() {
    return serverKey;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  public String getPrimaryMessage() {
    return primaryMessage;
  }

  public IssueSeverity getSeverity() {
    return severity;
  }

  public RuleType getType() {
    return type;
  }

  public CleanCodeAttribute getCleanCodeAttribute() {
    return cleanCodeAttribute;
  }

  public List<ImpactDto> getImpacts() {
    return impacts;
  }

  public Instant getIntroductionDate() {
    return introductionDate;
  }

  public boolean isOnNewCode() {
    return isOnNewCode;
  }

  public boolean isResolved() {
    return resolved;
  }

  @CheckForNull
  public TextRangeDto getTextRange() {
    return textRange;
  }

  public List<IssueFlowDto> getFlows() {
    return flows;
  }

  public List<QuickFixDto> getQuickFixes() {
    return quickFixes;
  }

  @CheckForNull
  public String getRuleDescriptionContextKey() {
    return ruleDescriptionContextKey;
  }

  @CheckForNull
  public VulnerabilityProbability getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }
}
