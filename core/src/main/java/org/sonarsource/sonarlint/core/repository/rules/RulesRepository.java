/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.repository.rules;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;

public class RulesRepository {
  private Map<String, SonarLintRuleDefinition> embeddedRulesByKey;
  private final Map<String, Map<String, SonarLintRuleDefinition>> rulesByKeyByConnectionId = new HashMap<>();
  private final Map<String, Map<String, String>> ruleKeyReplacementsByConnectionId = new HashMap<>();

  public void setEmbeddedRules(Collection<SonarLintRuleDefinition> embeddedRules) {
    this.embeddedRulesByKey = byKey(embeddedRules);
  }

  @CheckForNull
  public Collection<SonarLintRuleDefinition> getEmbeddedRules() {
    return embeddedRulesByKey == null ? null : embeddedRulesByKey.values();
  }

  public Optional<SonarLintRuleDefinition> getEmbeddedRule(String ruleKey) {
    return Optional.ofNullable(embeddedRulesByKey.get(ruleKey));
  }

  @CheckForNull
  public Collection<SonarLintRuleDefinition> getRules(String connectionId) {
    var rulesByKey = rulesByKeyByConnectionId.get(connectionId);
    return rulesByKey == null ? null : rulesByKey.values();
  }

  public void setRules(String connectionId, Collection<SonarLintRuleDefinition> rules) {
    var rulesByKey = byKey(rules);
    var ruleKeyReplacements = new HashMap<String, String>();
    rules.forEach(rule -> rule.getDeprecatedKeys().forEach(deprecatedKey -> ruleKeyReplacements.put(deprecatedKey, rule.getKey())));
    rulesByKeyByConnectionId.put(connectionId, rulesByKey);
    ruleKeyReplacementsByConnectionId.put(connectionId, ruleKeyReplacements);
  }

  public Optional<SonarLintRuleDefinition> getRule(String connectionId, String ruleKey) {
    var connectionRules = rulesByKeyByConnectionId.get(connectionId);
    return Optional.ofNullable(connectionRules.get(ruleKey))
      .or(() -> Optional.ofNullable(connectionRules.get(ruleKeyReplacementsByConnectionId.get(connectionId).get(ruleKey))));
  }

  private static Map<String, SonarLintRuleDefinition> byKey(Collection<SonarLintRuleDefinition> rules) {
    return rules.stream()
      .collect(Collectors.toMap(SonarLintRuleDefinition::getKey, r -> r));
  }
}
