/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.rules;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto;

public class StandaloneRulesConfigurationChanged {
  private final Map<String, StandaloneRuleConfigDto> standaloneRuleConfig;

  StandaloneRulesConfigurationChanged(Map<String, StandaloneRuleConfigDto> standaloneRuleConfig) {
    this.standaloneRuleConfig = standaloneRuleConfig;
  }

  public boolean isOnlyDeactivated() {
    return standaloneRuleConfig.values().stream()
      .noneMatch(StandaloneRuleConfigDto::isActive);
  }

  public List<String> getDeactivatedRules() {
    return standaloneRuleConfig.entrySet().stream()
      .filter(entry -> !entry.getValue().isActive())
      .map(Map.Entry::getKey)
      .collect(Collectors.toList());
  }
}
