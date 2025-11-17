/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

public class RuleDefinitionDto {
  private final String key;
  private final String name;
  private final CleanCodeAttribute cleanCodeAttribute;
  private final List<ImpactDto> softwareImpacts;
  private final Language language;
  private final Map<String, RuleParamDefinitionDto> paramsByKey;
  private final boolean isActiveByDefault;

  public RuleDefinitionDto(String key, String name, CleanCodeAttribute cleanCodeAttribute, List<ImpactDto> softwareImpacts,
    Map<String, RuleParamDefinitionDto> paramsByKey, boolean isActiveByDefault,
    Language language) {
    this.key = key;
    this.name = name;
    this.cleanCodeAttribute = cleanCodeAttribute;
    this.softwareImpacts = softwareImpacts;
    this.language = language;
    this.paramsByKey = paramsByKey;
    this.isActiveByDefault = isActiveByDefault;
  }

  public Map<String, RuleParamDefinitionDto> getParamsByKey() {
    return paramsByKey;
  }

  public boolean isActiveByDefault() {
    return isActiveByDefault;
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public CleanCodeAttribute getCleanCodeAttribute() {
    return cleanCodeAttribute;
  }

  public List<ImpactDto> getSoftwareImpacts() {
    return softwareImpacts;
  }

  public Language getLanguage() {
    return language;
  }
}
