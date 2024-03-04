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

import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class RuleParamDefinitionDto {
  private final String key;
  private final String name;
  private final String description;
  private final String defaultValue;
  private final RuleParamType type;
  private final boolean multiple;
  private final List<String> possibleValues;

  public RuleParamDefinitionDto(String key, String name, String description, @Nullable String defaultValue, RuleParamType type, boolean multiple, List<String> possibleValues) {
    this.key = key;
    this.name = name;
    this.description = description;
    this.defaultValue = defaultValue;
    this.type = type;
    this.multiple = multiple;
    this.possibleValues = possibleValues;
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  @CheckForNull
  public String getDefaultValue() {
    return defaultValue;
  }

  public RuleParamType getType() {
    return type;
  }

  public boolean isMultiple() {
    return multiple;
  }

  public List<String> getPossibleValues() {
    return possibleValues;
  }
}
