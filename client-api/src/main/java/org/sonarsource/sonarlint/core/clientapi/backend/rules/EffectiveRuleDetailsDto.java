/*
 * SonarLint Core - Client API
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.clientapi.backend.rules;

import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;

public class EffectiveRuleDetailsDto extends AbstractRuleDto {
  private final Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> description;
  private final Collection<EffectiveRuleParamDto> params;

  public EffectiveRuleDetailsDto(String key, String name, IssueSeverity severity, RuleType type,
    @Nullable CleanCodeAttribute cleanCodeAttribute, Map<SoftwareQuality, ImpactSeverity> defaultImpacts,
    Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> description, Collection<EffectiveRuleParamDto> params,
    Language language) {
    super(key, name, severity, type, cleanCodeAttribute, defaultImpacts, language);
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
