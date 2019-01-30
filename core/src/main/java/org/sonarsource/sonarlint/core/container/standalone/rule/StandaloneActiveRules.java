/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.standalone.rule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;

public class StandaloneActiveRules {
  public final ActiveRules activeRules;
  private final ActiveRules inactiveRules;
  private final Map<String, RuleDetails> ruleDetails;

  StandaloneActiveRules(ActiveRules activeRules, ActiveRules inactiveRules, Map<String, RuleDetails> ruleDetails) {
    this.activeRules = activeRules;
    this.inactiveRules = inactiveRules;
    this.ruleDetails = ruleDetails;
  }

  public ActiveRules filtered(Set<String> excludedRules, Set<String> includedRules) {
    Collection<ActiveRule> filteredActiveRules = new ArrayList<>();

    filteredActiveRules.addAll(activeRules.findAll().stream()
      .filter(r -> !excludedRules.contains(r.ruleKey().toString()))
      .collect(Collectors.toList()));
    filteredActiveRules.addAll(inactiveRules.findAll().stream()
      .filter(r -> includedRules.contains(r.ruleKey().toString()))
      .collect(Collectors.toList()));

    return new DefaultActiveRules(filteredActiveRules);
  }

  boolean isActiveByDefault(RuleKey ruleKey) {
    return activeRules.find(ruleKey) != null;
  }

  public Collection<ActiveRule> activeRulesByDefault() {
    return activeRules.findAll();
  }

  public RuleDetails ruleDetails(String ruleKeyStr) {
    return ruleDetails.get(ruleKeyStr);
  }

  public Collection<RuleDetails> allRuleDetails() {
    return ruleDetails.values();
  }
}
