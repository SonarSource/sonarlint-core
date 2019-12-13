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
package org.sonarsource.sonarlint.core.container.global;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.rule.RuleKey;

public class DefaultActiveRules implements ActiveRules {
  private final Collection<ActiveRule> allActiveRules;
  private final Map<String, List<ActiveRule>> activeRulesByRepository = new HashMap<>();
  private final Map<String, List<ActiveRule>> activeRulesByLanguage = new HashMap<>();
  private final Map<String, Map<String, ActiveRule>> activeRulesByRepositoryAndKey = new HashMap<>();
  private final Map<String, Map<String, ActiveRule>> activeRulesByRepositoryAndInternalKey = new HashMap<>();

  public DefaultActiveRules(Collection<ActiveRule> activeRules) {
    allActiveRules = activeRules;
    for (ActiveRule r : allActiveRules) {
      if (r.internalKey() != null) {
        activeRulesByRepositoryAndInternalKey.computeIfAbsent(r.ruleKey().repository(), x -> new HashMap<>()).put(r.internalKey(), r);
      }
      activeRulesByRepositoryAndKey.computeIfAbsent(r.ruleKey().repository(), x -> new HashMap<>()).put(r.ruleKey().rule(), r);
      activeRulesByRepository.computeIfAbsent(r.ruleKey().repository(), x -> new ArrayList<>()).add(r);
      activeRulesByLanguage.computeIfAbsent(r.language(), x -> new ArrayList<>()).add(r);
    }
  }

  @Override
  public ActiveRule find(RuleKey ruleKey) {
    return activeRulesByRepositoryAndKey.getOrDefault(ruleKey.repository(), Collections.emptyMap())
      .get(ruleKey.rule());
  }

  @Override
  public Collection<ActiveRule> findAll() {
    return allActiveRules;
  }

  @Override
  public Collection<ActiveRule> findByRepository(String repository) {
    return activeRulesByRepository.getOrDefault(repository, Collections.emptyList());
  }

  @Override
  public Collection<ActiveRule> findByLanguage(String language) {
    return activeRulesByLanguage.getOrDefault(language, Collections.emptyList());
  }

  @Override
  public ActiveRule findByInternalKey(String repository, String internalKey) {
    return activeRulesByRepositoryAndInternalKey.containsKey(repository) ? activeRulesByRepositoryAndInternalKey.get(repository).get(internalKey) : null;
  }

}
