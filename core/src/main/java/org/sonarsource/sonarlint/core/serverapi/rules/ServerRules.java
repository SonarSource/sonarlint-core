/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.rules;

import java.util.Map;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

public class ServerRules {
  private final Sonarlint.Rules allRules;
  private final Map<String, Sonarlint.ActiveRules> activeRulesByQualityProfile;

  public ServerRules(Sonarlint.Rules allRules, Map<String, Sonarlint.ActiveRules> activeRulesByQualityProfile) {
    this.allRules = allRules;
    this.activeRulesByQualityProfile = activeRulesByQualityProfile;
  }

  public Sonarlint.Rules getAll() {
    return allRules;
  }

  public Map<String, Sonarlint.ActiveRules> getActiveRulesByQualityProfile() {
    return activeRulesByQualityProfile;
  }
}
