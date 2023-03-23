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

import java.util.Map;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;

public class RuleDefinitionDto {
  private final String key;
  private final String name;
  private final IssueSeverity defaultSeverity;
  private final RuleType type;
  private final Map<String, RuleParamDefinitionDto> paramsByKey;
  private final boolean isActiveByDefault;
  private final Language language;

  public RuleDefinitionDto(String key, String name, IssueSeverity defaultSeverity, RuleType type, Map<String, RuleParamDefinitionDto> paramsByKey, boolean isActiveByDefault,
    Language language) {
    this.key = key;
    this.name = name;
    this.defaultSeverity = defaultSeverity;
    this.type = type;
    this.paramsByKey = paramsByKey;
    this.isActiveByDefault = isActiveByDefault;
    this.language = language;
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public IssueSeverity getDefaultSeverity() {
    return defaultSeverity;
  }

  public RuleType getType() {
    return type;
  }

  public Map<String, RuleParamDefinitionDto> getParamsByKey() {
    return paramsByKey;
  }

  public boolean isActiveByDefault() {
    return isActiveByDefault;
  }

  public Language getLanguage() {
    return language;
  }
}
