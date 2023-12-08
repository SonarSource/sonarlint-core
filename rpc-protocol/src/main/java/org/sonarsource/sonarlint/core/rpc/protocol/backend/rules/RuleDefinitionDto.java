/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.rules;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttributeCategory;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;

public class RuleDefinitionDto extends AbstractRuleDto {
  private final Map<String, RuleParamDefinitionDto> paramsByKey;
  private final boolean isActiveByDefault;

  public RuleDefinitionDto(String key, String name, IssueSeverity defaultSeverity, RuleType type,
    @Nullable CleanCodeAttribute cleanCodeAttribute, @Nullable CleanCodeAttributeCategory cleanCodeAttributeCategory, List<ImpactDto> defaultImpacts,
    Map<String, RuleParamDefinitionDto> paramsByKey, boolean isActiveByDefault, Language language, @Nullable VulnerabilityProbability vulnerabilityProbability) {
    super(key, name, defaultSeverity, type, cleanCodeAttribute, cleanCodeAttributeCategory, defaultImpacts, language, vulnerabilityProbability);
    this.paramsByKey = paramsByKey;
    this.isActiveByDefault = isActiveByDefault;
  }

  public Map<String, RuleParamDefinitionDto> getParamsByKey() {
    return paramsByKey;
  }

  public boolean isActiveByDefault() {
    return isActiveByDefault;
  }
}
