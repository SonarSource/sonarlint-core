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
package org.sonarsource.sonarlint.core.container.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sonar.api.batch.rule.Rule;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.rule.RuleKey;

public class SonarLintRules implements Rules {

  private final Map<String, List<Rule>> rulesByRepository = new HashMap<>();
  private final Map<String, Map<String, List<Rule>>> rulesByRepositoryAndInternalKey = new HashMap<>();
  private final Map<RuleKey, Rule> rulesByRuleKey = new HashMap<>();

  @Override
  public Rule find(RuleKey ruleKey) {
    return rulesByRuleKey.get(ruleKey);
  }

  @Override
  public Collection<Rule> findAll() {
    return rulesByRepository.values().stream().flatMap(List::stream).collect(Collectors.toList());
  }

  @Override
  public Collection<Rule> findByRepository(String repository) {
    return rulesByRepository.getOrDefault(repository, Collections.emptyList());
  }

  @Override
  public Collection<Rule> findByInternalKey(String repository, String internalKey) {
    return rulesByRepositoryAndInternalKey
      .getOrDefault(repository, Collections.emptyMap())
      .getOrDefault(internalKey, Collections.emptyList());
  }

  public void add(Rule r) {
    rulesByRuleKey.put(r.key(), r);
    rulesByRepository.computeIfAbsent(r.key().repository(), x -> new ArrayList<>()).add(r);
    addToTable(rulesByRepositoryAndInternalKey, r);
  }

  private static void addToTable(Map<String, Map<String, List<Rule>>> rulesByRepositoryAndInternalKeyBuilder, Rule r) {
    if (r.internalKey() == null) {
      return;
    }

    rulesByRepositoryAndInternalKeyBuilder
      .computeIfAbsent(r.key().repository(), x -> new HashMap<>())
      .computeIfAbsent(r.internalKey(), x -> new ArrayList<>())
      .add(r);
  }
}
