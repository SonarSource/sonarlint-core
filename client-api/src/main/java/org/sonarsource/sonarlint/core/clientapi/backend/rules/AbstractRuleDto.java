/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.backend.rules;

import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.clientapi.common.IssueSeverity;
import org.sonarsource.sonarlint.core.clientapi.common.Language;
import org.sonarsource.sonarlint.core.clientapi.common.RuleType;

public abstract class AbstractRuleDto {
  private final String key;
  private final String name;
  private final IssueSeverity severity;
  private final RuleType type;
  private final CleanCodeAttributeDto cleanCodeAttributeDetails;
  private final List<ImpactDto> defaultImpacts;
  private final Language language;

  AbstractRuleDto(String key, String name, IssueSeverity severity, RuleType type,
    @Nullable CleanCodeAttributeDto cleanCodeAttributeDetails, List<ImpactDto> defaultImpacts,
    Language language) {
    this.key = key;
    this.name = name;
    this.severity = severity;
    this.type = type;
    this.cleanCodeAttributeDetails = cleanCodeAttributeDetails;
    this.defaultImpacts = defaultImpacts;
    this.language = language;
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public IssueSeverity getSeverity() {
    return severity;
  }

  public RuleType getType() {
    return type;
  }

  @CheckForNull
  public CleanCodeAttributeDto getCleanCodeAttributeDetails() {
    return cleanCodeAttributeDetails;
  }

  public List<ImpactDto> getDefaultImpacts() {
    return defaultImpacts;
  }

  public Language getLanguage() {
    return language;
  }
}
