/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) SonarSource Sàrl
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
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.MQRModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

public class RaisedIssueDto extends RaisedFindingDto {

  private final boolean isAiCodeFixable;
  private final ResolutionStatus resolutionStatus;

  private RaisedIssueDto(Builder builder) {
    super(builder.id, builder.serverKey, builder.ruleKey, builder.primaryMessage, builder.severityMode,
      builder.introductionDate, builder.isOnNewCode, builder.resolved, builder.textRange, builder.flows,
      builder.quickFixes, builder.ruleDescriptionContextKey);
    this.isAiCodeFixable = builder.isAiCodeFixable;
    this.resolutionStatus = builder.resolutionStatus;
  }

  public boolean isAiCodeFixable() {
    return isAiCodeFixable;
  }

  public Builder builder() {
    return Builder.from(this);
  }

  public ResolutionStatus getResolutionStatus() {
    return resolutionStatus;
  }

  public static class Builder {
    private UUID id;
    private String serverKey;
    private String ruleKey;
    private String primaryMessage;
    private Either<StandardModeDetails, MQRModeDetails> severityMode;
    private Instant introductionDate;
    private boolean isOnNewCode;
    private boolean resolved;
    private TextRangeDto textRange;
    private List<IssueFlowDto> flows;
    private List<QuickFixDto> quickFixes;
    private String ruleDescriptionContextKey;
    private boolean isAiCodeFixable;
    private ResolutionStatus resolutionStatus;

    public Builder() {
      // default constructor
    }

    public static Builder from(RaisedIssueDto dto) {
      var builder = new Builder();
      builder.id = dto.getId();
      builder.serverKey = dto.getServerKey();
      builder.ruleKey = dto.getRuleKey();
      builder.primaryMessage = dto.getPrimaryMessage();
      builder.severityMode = dto.getSeverityMode();
      builder.introductionDate = dto.getIntroductionDate();
      builder.isOnNewCode = dto.isOnNewCode();
      builder.resolved = dto.isResolved();
      builder.textRange = dto.getTextRange();
      builder.flows = dto.getFlows();
      builder.quickFixes = dto.getQuickFixes();
      builder.ruleDescriptionContextKey = dto.getRuleDescriptionContextKey();
      builder.isAiCodeFixable = dto.isAiCodeFixable();
      builder.resolutionStatus = dto.getResolutionStatus();
      return builder;
    }

    public Builder setId(UUID id) {
      this.id = id;
      return this;
    }

    public Builder setServerKey(@Nullable String serverKey) {
      this.serverKey = serverKey;
      return this;
    }

    public Builder setRuleKey(String ruleKey) {
      this.ruleKey = ruleKey;
      return this;
    }

    public Builder setPrimaryMessage(String primaryMessage) {
      this.primaryMessage = primaryMessage;
      return this;
    }

    public Builder setSeverityMode(Either<StandardModeDetails, MQRModeDetails> severityMode) {
      this.severityMode = severityMode;
      return this;
    }

    public Builder setIntroductionDate(Instant introductionDate) {
      this.introductionDate = introductionDate;
      return this;
    }

    public Builder setIsOnNewCode(boolean isOnNewCode) {
      this.isOnNewCode = isOnNewCode;
      return this;
    }

    public Builder setTextRange(@Nullable TextRangeDto textRange) {
      this.textRange = textRange;
      return this;
    }

    public Builder setFlows(List<IssueFlowDto> flows) {
      this.flows = flows;
      return this;
    }

    public Builder setQuickFixes(List<QuickFixDto> quickFixes) {
      this.quickFixes = quickFixes;
      return this;
    }

    public Builder setRuleDescriptionContextKey(@Nullable String ruleDescriptionContextKey) {
      this.ruleDescriptionContextKey = ruleDescriptionContextKey;
      return this;
    }

    public Builder setIsAiCodeFixable(boolean isAiCodeFixable) {
      this.isAiCodeFixable = isAiCodeFixable;
      return this;
    }

    public Builder setResolutionStatus(@Nullable ResolutionStatus resolutionStatus) {
      this.resolutionStatus = resolutionStatus;
      return this;
    }

    public Builder withResolution(boolean resolved) {
      this.resolved = resolved;
      return this;
    }

    public Builder withStandardModeDetails(IssueSeverity severity, RuleType type) {
      this.severityMode = Either.forLeft(new StandardModeDetails(severity, type));
      return this;
    }

    public Builder withMQRModeDetails(CleanCodeAttribute cleanCodeAttribute, List<ImpactDto> impacts) {
      this.severityMode = Either.forRight(new MQRModeDetails(cleanCodeAttribute, impacts));
      return this;
    }

    public RaisedIssueDto buildIssue() {
      return new RaisedIssueDto(this);
    }
  }
}
