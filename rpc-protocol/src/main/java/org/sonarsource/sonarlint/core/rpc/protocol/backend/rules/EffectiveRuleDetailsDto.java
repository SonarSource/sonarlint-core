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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.rules;

import com.google.gson.annotations.JsonAdapter;
import java.util.Collection;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherRuleDescriptionAdapterFactory;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.MQRModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;

public class EffectiveRuleDetailsDto {
  private final String key;
  private final String name;
  private final Either<StandardModeDetails, MQRModeDetails> severityDetails;
  private final Language language;
  private final VulnerabilityProbability vulnerabilityProbability;
  @JsonAdapter(EitherRuleDescriptionAdapterFactory.class)
  private final Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> description;
  private final Collection<EffectiveRuleParamDto> params;

  public EffectiveRuleDetailsDto(String key, String name, Either<StandardModeDetails, MQRModeDetails> severityDetails,
    Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> description, Collection<EffectiveRuleParamDto> params,
    Language language, @Nullable VulnerabilityProbability vulnerabilityProbability) {
    this.key = key;
    this.name = name;
    this.severityDetails = severityDetails;
    this.language = language;
    this.vulnerabilityProbability = vulnerabilityProbability;
    this.description = description;
    this.params = params;
  }

  public Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> getDescription() {
    return description;
  }

  public Collection<EffectiveRuleParamDto> getParams() {
    return params;
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public Either<StandardModeDetails, MQRModeDetails> getSeverityDetails() {
    return severityDetails;
  }

  @CheckForNull
  public IssueSeverity getSeverity() {
    return this.severityDetails.isLeft() ?
      this.severityDetails.getLeft().getSeverity() : null;
  }

  @CheckForNull
  public RuleType getType() {
    return this.severityDetails.isLeft() ?
      this.severityDetails.getLeft().getType() : null;
  }

  @CheckForNull
  public List<ImpactDto> getDefaultImpacts() {
    return this.severityDetails.isRight() ?
      this.severityDetails.getRight().getImpacts() : null;
  }

  @CheckForNull
  public CleanCodeAttribute getCleanCodeAttribute() {
    return this.severityDetails.isRight() ?
      this.severityDetails.getRight().getCleanCodeAttribute() : null;
  }

  public Language getLanguage() {
    return language;
  }

  public VulnerabilityProbability getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }
}
