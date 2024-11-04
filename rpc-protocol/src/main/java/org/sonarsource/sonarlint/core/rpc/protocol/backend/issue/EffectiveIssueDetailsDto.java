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
import java.util.Collection;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherRuleDescriptionAdapterFactory;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherStandardOrMQRModeAdapterFactory;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleParamDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleMonolithicDescriptionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleSplitDescriptionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.MQRModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;

public class EffectiveIssueDetailsDto {
  private final String ruleKey;
  private final String name;
  private final Language language;
  private final VulnerabilityProbability vulnerabilityProbability;
  @JsonAdapter(EitherRuleDescriptionAdapterFactory.class)
  private final Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> description;
  private final Collection<EffectiveRuleParamDto> params;
  @JsonAdapter(EitherStandardOrMQRModeAdapterFactory.class)
  private final Either<StandardModeDetails, MQRModeDetails> severityDetails;
  private final String ruleDescriptionContextKey;

  public EffectiveIssueDetailsDto(String ruleKey, String name, Language language, @Nullable VulnerabilityProbability vulnerabilityProbability,
    Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> description,
    Collection<EffectiveRuleParamDto> params, Either<StandardModeDetails, MQRModeDetails> severityDetails,
    @Nullable String ruleDescriptionContextKey) {
    this.ruleKey = ruleKey;
    this.name = name;
    this.language = language;
    this.vulnerabilityProbability = vulnerabilityProbability;
    this.description = description;
    this.params = params;
    this.severityDetails = severityDetails;
    this.ruleDescriptionContextKey = ruleDescriptionContextKey;
  }

  public Either<StandardModeDetails, MQRModeDetails> getSeverityDetails() {
    return severityDetails;
  }

  @CheckForNull
  public String getRuleDescriptionContextKey() {
    return ruleDescriptionContextKey;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  public String getName() {
    return name;
  }

  public Language getLanguage() {
    return language;
  }

  public VulnerabilityProbability getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }

  public Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> getDescription() {
    return description;
  }

  public Collection<EffectiveRuleParamDto> getParams() {
    return params;
  }
}
