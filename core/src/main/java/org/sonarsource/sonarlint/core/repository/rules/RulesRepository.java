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

import com.google.common.eventbus.Subscribe;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.rules.RulesExtractionHelper;

public class RulesRepository {

  private final RulesExtractionHelper extractionHelper;
  private Map<String, SonarLintRuleDefinition> embeddedRulesByKey;
  private final Map<String, Map<String, SonarLintRuleDefinition>> rulesByKeyByConnectionId = new HashMap<>();
  private final Map<String, Map<String, String>> ruleKeyReplacementsByConnectionId = new HashMap<>();

  public RulesRepository(RulesExtractionHelper extractionHelper) {
    this.extractionHelper = extractionHelper;
  }

  public Collection<SonarLintRuleDefinition> getEmbeddedRules() {
    lazyInit();
    return embeddedRulesByKey.values();
  }

  public Optional<SonarLintRuleDefinition> getEmbeddedRule(String ruleKey) {
    lazyInit();
    return Optional.ofNullable(embeddedRulesByKey.get(ruleKey));
  }

  private synchronized void lazyInit() {
    if (embeddedRulesByKey == null) {
      this.embeddedRulesByKey = byKey(extractionHelper.extractEmbeddedRules());
    }
  }

  public Collection<SonarLintRuleDefinition> getRules(String connectionId) {
    lazyInit(connectionId);
    return rulesByKeyByConnectionId.getOrDefault(connectionId, Map.of()).values();
  }

  public Optional<SonarLintRuleDefinition> getRule(String connectionId, String ruleKey) {
    lazyInit(connectionId);
    var connectionRules = rulesByKeyByConnectionId.get(connectionId);
    return Optional.ofNullable(connectionRules.get(ruleKey))
      .or(() -> Optional.ofNullable(connectionRules.get(ruleKeyReplacementsByConnectionId.get(connectionId).get(ruleKey))));
  }

  private synchronized void lazyInit(String connectionId) {
    var rulesByKey = rulesByKeyByConnectionId.get(connectionId);
    if (rulesByKey == null) {
      setRules(connectionId, extractionHelper.extractRulesForConnection(connectionId));
    }
  }

  private void setRules(String connectionId, Collection<SonarLintRuleDefinition> rules) {
    var rulesByKey = byKey(rules);
    var ruleKeyReplacements = new HashMap<String, String>();
    rules.forEach(rule -> rule.getDeprecatedKeys().forEach(deprecatedKey -> ruleKeyReplacements.put(deprecatedKey, rule.getKey())));
    rulesByKeyByConnectionId.put(connectionId, rulesByKey);
    ruleKeyReplacementsByConnectionId.put(connectionId, ruleKeyReplacements);
  }

  private static Map<String, SonarLintRuleDefinition> byKey(Collection<SonarLintRuleDefinition> rules) {
    return rules.stream()
      .collect(Collectors.toMap(SonarLintRuleDefinition::getKey, r -> r));
  }

  @Subscribe
  public void connectionRemoved(ConnectionConfigurationRemovedEvent e) {
    evictAll(e.getRemovedConnectionId());
  }

  private void evictAll(String connectionId) {
    rulesByKeyByConnectionId.remove(connectionId);
    ruleKeyReplacementsByConnectionId.remove(connectionId);
  }
}
