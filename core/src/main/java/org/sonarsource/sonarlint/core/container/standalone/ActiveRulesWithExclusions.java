/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.standalone;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.rule.RuleKey;

class ActiveRulesWithExclusions implements ActiveRules {

  private final ActiveRules activeRules;
  private final Set<RuleKey> exclusions;

  ActiveRulesWithExclusions(ActiveRules activeRules, Collection<org.sonarsource.sonarlint.core.client.api.common.RuleKey> ruleKeys) {
    this.activeRules = activeRules;
    this.exclusions = ruleKeys.stream().map(rk -> RuleKey.of(rk.repository(), rk.rule())).collect(Collectors.toSet());
  }

  @CheckForNull
  @Override
  public ActiveRule find(RuleKey ruleKey) {
    if (exclusions.contains(ruleKey)) {
      return null;
    }
    return activeRules.find(ruleKey);
  }

  @Override
  public Collection<ActiveRule> findAll() {
    return filterExcluded(activeRules.findAll());
  }

  @Override
  public Collection<ActiveRule> findByRepository(String s) {
    return filterExcluded(activeRules.findByRepository(s));
  }

  @Override
  public Collection<ActiveRule> findByLanguage(String s) {
    return filterExcluded(activeRules.findByLanguage(s));
  }

  @CheckForNull
  @Override
  public ActiveRule findByInternalKey(String repository, String internalKey) {
    ActiveRule rule = activeRules.findByInternalKey(repository, internalKey);
    if (rule != null && exclusions.contains(rule.ruleKey())) {
      return null;
    }
    return rule;
  }

  private Collection<ActiveRule> filterExcluded(Collection<ActiveRule> rules) {
    return rules.stream().filter(r -> !exclusions.contains(r.ruleKey())).collect(Collectors.toSet());
  }
}
