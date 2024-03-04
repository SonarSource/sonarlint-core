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

import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.Language;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractRuleDto {
  private final String key;
  private final String name;
  private final IssueSeverity severity;
  private final RuleType type;
  private final CleanCodeAttribute cleanCodeAttribute;
  private final Map<SoftwareQuality, ImpactSeverity> defaultImpacts;
  private final Language language;

  AbstractRuleDto(String key, String name, IssueSeverity severity, RuleType type,
    @Nullable CleanCodeAttribute cleanCodeAttribute, Map<SoftwareQuality, ImpactSeverity> defaultImpacts,
    Language language) {
    this.key = key;
    this.name = name;
    this.severity = severity;
    this.type = type;
    this.cleanCodeAttribute = cleanCodeAttribute;
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

  public Optional<CleanCodeAttribute> getCleanCodeAttribute() {
    return Optional.ofNullable(cleanCodeAttribute);
  }

  public Map<SoftwareQuality, ImpactSeverity> getDefaultImpacts() {
    return defaultImpacts;
  }

  public Language getLanguage() {
    return language;
  }
}
