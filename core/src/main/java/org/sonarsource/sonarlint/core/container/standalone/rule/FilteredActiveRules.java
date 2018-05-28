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
package org.sonarsource.sonarlint.core.container.standalone.rule;

import java.util.Collection;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.rule.RuleKey;

/**
 * Provide active rules based on combined rules and active rules, with user filters applied.
 */
public class FilteredActiveRules implements ActiveRules {

  private final CombinedActiveRules combinedActiveRules;
  private final RuleFilter ruleFilter;

  public FilteredActiveRules(CombinedActiveRules combinedActiveRules, RuleFilter ruleFilter) {
    this.combinedActiveRules = combinedActiveRules;
    this.ruleFilter = ruleFilter;
  }

  @CheckForNull
  @Override
  public ActiveRule find(RuleKey ruleKey) {
    if (!ruleFilter.isActive(ruleKey)) {
      return null;
    }
    return combinedActiveRules.find(ruleKey);
  }

  @Override
  public Collection<ActiveRule> findAll() {
    return applyFilter(combinedActiveRules.findAll());
  }

  @Override
  public Collection<ActiveRule> findByRepository(String s) {
    return applyFilter(combinedActiveRules.findByRepository(s));
  }

  @Override
  public Collection<ActiveRule> findByLanguage(String s) {
    return applyFilter(combinedActiveRules.findByLanguage(s));
  }

  @CheckForNull
  @Override
  public ActiveRule findByInternalKey(String repository, String internalKey) {
    ActiveRule rule = combinedActiveRules.findByInternalKey(repository, internalKey);
    if (rule == null || !ruleFilter.isActive(rule.ruleKey())) {
      return null;
    }
    return rule;
  }

  private Collection<ActiveRule> applyFilter(Collection<ActiveRule> rules) {
    return rules.stream()
      .filter(r -> ruleFilter.isActive(r.ruleKey()))
      .collect(Collectors.toList());
  }
}
