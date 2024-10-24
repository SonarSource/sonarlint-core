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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.issue;

import com.google.gson.annotations.JsonAdapter;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherStandardOrMRAdapterFactory;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.MQRModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;

public class EffectiveIssueDetailsDto {
  private final EffectiveRuleDetailsDto effectiveRuleDetails;
  @JsonAdapter(EitherStandardOrMRAdapterFactory.class)
  private final Either<StandardModeDetails, MQRModeDetails> severityDetails;
  private final String ruleDescriptionContextKey;

  public EffectiveIssueDetailsDto(EffectiveRuleDetailsDto effectiveRuleDetails, Either<StandardModeDetails, MQRModeDetails> severityDetails,
    @Nullable String ruleDescriptionContextKey) {
    this.effectiveRuleDetails = effectiveRuleDetails;
    this.severityDetails = severityDetails;
    this.ruleDescriptionContextKey = ruleDescriptionContextKey;
  }

  public EffectiveRuleDetailsDto getEffectiveRuleDetails() {
    return effectiveRuleDetails;
  }

  public Either<StandardModeDetails, MQRModeDetails> getSeverityDetails() {
    return severityDetails;
  }

  @CheckForNull
  public String getRuleDescriptionContextKey() {
    return ruleDescriptionContextKey;
  }
}
