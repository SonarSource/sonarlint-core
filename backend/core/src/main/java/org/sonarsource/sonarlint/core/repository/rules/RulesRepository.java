/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.repository.rules;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.rules.RulesExtractionHelper;
import org.sonarsource.sonarlint.core.serverconnection.ServerSettings;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.storage.StorageService;

public class RulesRepository {

  private final SonarLintLogger logger = SonarLintLogger.get();

  private final RulesExtractionHelper extractionHelper;
  private Map<String, SonarLintRuleDefinition> embeddedRulesByKey;
  private final Map<String, Map<String, SonarLintRuleDefinition>> rulesByKeyByConnectionId = new HashMap<>();
  private final Map<String, Map<String, String>> ruleKeyReplacementsByConnectionId = new HashMap<>();
  private final StorageService storageService;

  public RulesRepository(RulesExtractionHelper extractionHelper, StorageService storageService) {
    this.extractionHelper = extractionHelper;
    this.storageService = storageService;
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

  public Optional<SonarLintRuleDefinition> getRule(String connectionId, String ruleKey) {
    lazyInit(connectionId);
    var connectionRules = rulesByKeyByConnectionId.get(connectionId);
    return Optional.ofNullable(connectionRules.get(ruleKey))
      .or(() -> Optional.ofNullable(connectionRules.get(ruleKeyReplacementsByConnectionId.get(connectionId).get(ruleKey))));
  }

  private synchronized void lazyInit(String connectionId) {
    var rulesByKey = rulesByKeyByConnectionId.get(connectionId);
    if (rulesByKey == null) {
      var serverSettings = storageService.connection(connectionId).serverInfo().read().map(StoredServerInfo::globalSettings);
      setRules(connectionId, extractionHelper.extractRulesForConnection(connectionId, serverSettings.map(ServerSettings::globalSettings).orElseGet(Map::of)));
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

  public void evictFor(String connectionId) {
    logger.debug("Evict cached rules definitions for connection '{}'", connectionId);
    rulesByKeyByConnectionId.remove(connectionId);
    ruleKeyReplacementsByConnectionId.remove(connectionId);
  }
}
