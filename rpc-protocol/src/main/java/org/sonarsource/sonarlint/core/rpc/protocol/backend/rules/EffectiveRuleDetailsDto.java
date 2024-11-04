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
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherRuleDescriptionAdapterFactory;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttributeCategory;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;

public class EffectiveRuleDetailsDto extends AbstractRuleDto {

  @JsonAdapter(EitherRuleDescriptionAdapterFactory.class)
  private final Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> description;
  private final Collection<EffectiveRuleParamDto> params;

  public EffectiveRuleDetailsDto(String key, String name, @Nullable IssueSeverity severity, @Nullable RuleType type,
    @Nullable CleanCodeAttribute cleanCodeAttribute, @Nullable CleanCodeAttributeCategory cleanCodeAttributeCategory, List<ImpactDto> defaultImpacts,
    Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> description, Collection<EffectiveRuleParamDto> params,
    Language language, @Nullable VulnerabilityProbability vulnerabilityProbability) {
    super(key, name, severity, type, cleanCodeAttribute, cleanCodeAttributeCategory, defaultImpacts, language, vulnerabilityProbability);
    this.description = description;
    this.params = params;
  }

  public Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> getDescription() {
    return description;
  }

  public Collection<EffectiveRuleParamDto> getParams() {
    return params;
  }
}
