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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.container.global.DefaultActiveRules;

import static java.util.stream.Collectors.toList;

public class StandaloneActiveRules {
  private Map<String, StandaloneRule> rulesByKey;

  public StandaloneActiveRules(List<StandaloneRule> rules) {
    rulesByKey = rules.stream().collect(Collectors.toMap(r -> r.key().toString(), r -> r));
  }

  public ActiveRules filtered(Set<String> excludedRules, Set<String> includedRules) {
    Collection<StandaloneRule> filteredActiveRules = new ArrayList<>();

    filteredActiveRules.addAll(rulesByKey.values().stream()
      .filter(StandaloneRule::isActiveByDefault)
      .filter(r -> !excludedRules.contains(r.getKey()))
      .collect(Collectors.toList()));
    filteredActiveRules.addAll(rulesByKey.values().stream()
      .filter(r -> !r.isActiveByDefault())
      .filter(r -> includedRules.contains(r.getKey()))
      .collect(Collectors.toList()));

    return new DefaultActiveRules(filteredActiveRules.stream().map(StandaloneActiveRuleAdapter::new).collect(toList()));
  }

  boolean isActiveByDefault(RuleKey ruleKey) {
    return rulesByKey.get(ruleKey.toString()).isActiveByDefault();
  }

  @CheckForNull
  public RuleDetails ruleDetails(String ruleKeyStr) {
    return rulesByKey.get(ruleKeyStr);
  }

  public Collection<RuleDetails> allRuleDetails() {
    return rulesByKey.values().stream().map(r -> (RuleDetails) r).collect(toList());
  }

  public Collection<String> getActiveRuleKeys() {
    return rulesByKey.values().stream()
      .filter(StandaloneRule::isActiveByDefault)
      .map(StandaloneRule::getKey)
      .collect(toList());
  }
}
