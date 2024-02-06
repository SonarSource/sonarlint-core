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

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class EffectiveRuleParamDto {
  private final String name;
  private final String description;
  private final String value;
  private final String defaultValue;

  public EffectiveRuleParamDto(String name, String description, @Nullable String value, @Nullable String defaultValue) {
    this.name = name;
    this.description = description;
    this.value = value;
    this.defaultValue = defaultValue;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  /**
   * Get the effective value, as would be used by the analyzer
   */
  @CheckForNull
  public String getValue() {
    return value;
  }

  /**
   * Get the default value as defined in the rule's definition
   */
  @CheckForNull
  public String getDefaultValue() {
    return defaultValue;
  }
}
