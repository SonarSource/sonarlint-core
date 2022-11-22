/*
 * SonarLint Core - Client API
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.clientapi.rules;

import java.util.Collection;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;

public class ActiveRuleDetailsDto {
  private final String key;
  private final String name;
  private final IssueSeverity severity;
  private final RuleType type;
  private final String description;
  private final String extendedDescription;
  private final Collection<ActiveRuleParamDto> params;
  private final Language language;

  public ActiveRuleDetailsDto(String key, String name, IssueSeverity severity, RuleType type, String description, @Nullable String extendedDescription,
    Collection<ActiveRuleParamDto> params, Language language) {
    this.key = key;
    this.name = name;
    this.severity = severity;
    this.type = type;
    this.description = description;
    this.extendedDescription = extendedDescription;
    this.params = params;
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

  public String getDescription() {
    return description;
  }

  @CheckForNull
  public String getExtendedDescription() {
    return extendedDescription;
  }

  public Collection<ActiveRuleParamDto> getParams() {
    return params;
  }

  public Language getLanguage() {
    return language;
  }

}
