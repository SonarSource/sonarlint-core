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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.MQRModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

public abstract class RaisedFindingDto {

  private final UUID id;
  @Nullable
  private final String serverKey;
  private final String ruleKey;
  private final String primaryMessage;
  private final Either<StandardModeDetails, MQRModeDetails> severityMode;
  @Deprecated(since = "10.9")
  private final IssueSeverity severity;
  @Deprecated(since = "10.9")
  private final RuleType type;
  @Deprecated(since = "10.9")
  private final CleanCodeAttribute cleanCodeAttribute;
  @Deprecated(since = "10.9")
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

  protected RaisedFindingDto(UUID id, @Nullable String serverKey, String ruleKey, String primaryMessage, Either<StandardModeDetails, MQRModeDetails> severityMode,
    IssueSeverity severity, RuleType type, CleanCodeAttribute cleanCodeAttribute, List<ImpactDto> impacts, Instant introductionDate, boolean isOnNewCode,
    boolean resolved, @Nullable TextRangeDto textRange, List<IssueFlowDto> flows, List<QuickFixDto> quickFixes, @Nullable String ruleDescriptionContextKey) {
    this.id = id;
    this.serverKey = serverKey;
    this.ruleKey = ruleKey;
    this.primaryMessage = primaryMessage;
    this.severityMode = severityMode;
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

  public Either<StandardModeDetails, MQRModeDetails> getSeverityMode() {
    return severityMode;
  }

  @Deprecated(since = "10.9")
  public IssueSeverity getSeverity() {
    return severity;
  }

  @Deprecated(since = "10.9")
  public RuleType getType() {
    return type;
  }

  @Deprecated(since = "10.9")
  public CleanCodeAttribute getCleanCodeAttribute() {
    return cleanCodeAttribute;
  }

  @Deprecated(since = "10.9")
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

  public RaisedFindingDtoBuilder builder() {
    return RaisedFindingDtoBuilder.from(this);
  }

  public static class RaisedFindingDtoBuilder {
    private final UUID id;
    private final String serverKey;
    private final String ruleKey;
    private final String primaryMessage;
    private Either<StandardModeDetails, MQRModeDetails> severityMode;
    @Deprecated(since = "10.9")
    private IssueSeverity severity;
    @Deprecated(since = "10.9")
    private RuleType type;
    @Deprecated(since = "10.9")
    private final CleanCodeAttribute cleanCodeAttribute;
    @Deprecated(since = "10.9")
    private final List<ImpactDto> impacts;
    private final Instant introductionDate;
    private final boolean isOnNewCode;
    private boolean resolved;
    private final TextRangeDto textRange;
    private final List<IssueFlowDto> flows;
    private final List<QuickFixDto> quickFixes;
    private final String ruleDescriptionContextKey;
    private HotspotStatus status;
    private final VulnerabilityProbability vulnerabilityProbability;

    private RaisedFindingDtoBuilder(UUID id, @Nullable String serverKey, String ruleKey, String primaryMessage, Either<StandardModeDetails, MQRModeDetails> severityMode,
      IssueSeverity severity, RuleType type, CleanCodeAttribute cleanCodeAttribute, List<ImpactDto> impacts, Instant introductionDate, boolean isOnNewCode,
      boolean resolved, @Nullable TextRangeDto textRange, List<IssueFlowDto> flows, List<QuickFixDto> quickFixes, @Nullable String ruleDescriptionContextKey,
      @Nullable HotspotStatus status, @Nullable VulnerabilityProbability vulnerabilityProbability) {
      this.id = id;
      this.serverKey = serverKey;
      this.ruleKey = ruleKey;
      this.primaryMessage = primaryMessage;
      this.severityMode = severityMode;
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
      this.status = status;
      this.vulnerabilityProbability = vulnerabilityProbability;
    }

    public static RaisedFindingDtoBuilder from(RaisedFindingDto dto) {
      HotspotStatus status = null;
      VulnerabilityProbability vulnerabilityProbability = null;
      if (dto instanceof RaisedHotspotDto) {
        status = ((RaisedHotspotDto) dto).getStatus();
        vulnerabilityProbability = ((RaisedHotspotDto) dto).getVulnerabilityProbability();
      }
      return new RaisedFindingDtoBuilder(dto.getId(), dto.getServerKey(), dto.getRuleKey(), dto.getPrimaryMessage(), dto.getSeverityMode(), dto.getSeverity(),
        dto.getType(), dto.getCleanCodeAttribute(), dto.getImpacts(), dto.getIntroductionDate(), dto.isOnNewCode(), dto.isResolved(), dto.getTextRange(),
        dto.getFlows(), dto.getQuickFixes(), dto.getRuleDescriptionContextKey(), status, vulnerabilityProbability);
    }

    public RaisedFindingDtoBuilder withResolution(boolean resolved) {
      this.resolved = resolved;
      return this;
    }

    public RaisedFindingDtoBuilder withStandardModeDetails(IssueSeverity severity, RuleType type) {
      this.severityMode = Either.forLeft(new StandardModeDetails(severity, type));
      return this;
    }

    public RaisedFindingDtoBuilder withMQRModeDetails(CleanCodeAttribute cleanCodeAttribute, List<ImpactDto> impacts) {
      this.severityMode = Either.forRight(new MQRModeDetails(cleanCodeAttribute, impacts));
      return this;
    }

    @Deprecated(since = "10.9")
    public RaisedFindingDtoBuilder withSeverity(IssueSeverity severity) {
      this.severity = severity;
      return this;
    }

    @Deprecated(since = "10.9")
    public RaisedFindingDtoBuilder withType(RuleType type) {
      this.type = type;
      return this;
    }

    public RaisedFindingDtoBuilder withHotspotStatus(HotspotStatus status) {
      this.status = status;
      return this;
    }

    public RaisedIssueDto buildIssue() {
      return new RaisedIssueDto(id, serverKey, ruleKey, primaryMessage, severityMode, severity, type, cleanCodeAttribute, impacts,
        introductionDate, isOnNewCode, resolved, textRange, flows, quickFixes, ruleDescriptionContextKey);
    }

    public RaisedHotspotDto buildHotspot() {
      return new RaisedHotspotDto(id, serverKey, ruleKey, primaryMessage, severityMode, severity, type, cleanCodeAttribute, impacts,
        introductionDate, isOnNewCode, resolved, textRange, flows, quickFixes, ruleDescriptionContextKey, vulnerabilityProbability, status);
    }
  }
}
