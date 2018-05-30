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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.CheckForNull;
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

  public ActiveRules filtered(SanitizedActiveRulesFilter filter) {
    Collection<RuleKey> excluded = filter.excluded();
    Collection<RuleKey> included = filter.included();

    if (included.isEmpty() && excluded.isEmpty()) {
      return activeRules;
    }

    return new FilteredActiveRules(excluded, included);
  }

  boolean isActiveByDefault(RuleKey ruleKey) {
    return activeRules.find(ruleKey) != null;
  }

  public Collection<ActiveRule> activeRulesByDefault() {
    return activeRules.findAll();
  }

  public List<ActiveRule> all() {
    List<ActiveRule> all = new ArrayList<>();
    all.addAll(activeRules.findAll());
    all.addAll(inactiveRules.findAll());
    return all;
  }

  public RuleDetails ruleDetails(String ruleKeyStr) {
    return ruleDetails.get(ruleKeyStr);
  }

  public Collection<RuleDetails> allRuleDetails() {
    return ruleDetails.values();
  }

  class FilteredActiveRules implements ActiveRules {

    final Collection<RuleKey> excluded;
    final Collection<RuleKey> included;

    // accept from active those that are not explicitly excluded
    final Predicate<ActiveRule> filterActive;

    // accept from inactive those that are explicitly included
    final Predicate<ActiveRule> filterInactive;

    FilteredActiveRules(Collection<RuleKey> excluded, Collection<RuleKey> included) {
      this.excluded = excluded;
      this.included = included;
      this.filterActive = ar -> !excluded.contains(ar.ruleKey());
      this.filterInactive = ar -> included.contains(ar.ruleKey());
    }

    @CheckForNull
    @Override
    public ActiveRule find(RuleKey ruleKey) {
      if (excluded.contains(ruleKey)) {
        return null;
      }
      ActiveRule activeRule = activeRules.find(ruleKey);
      if (activeRule != null) {
        return activeRule;
      }
      if (included.contains(ruleKey)) {
        return inactiveRules.find(ruleKey);
      }
      return null;
    }

    @Override
    public Collection<ActiveRule> findAll() {
      Collection<ActiveRule> result = new ArrayList<>();
      activeRules.findAll().stream().filter(filterActive).forEach(result::add);
      inactiveRules.findAll().stream().filter(filterInactive).forEach(result::add);
      return result;
    }

    @Override
    public Collection<ActiveRule> findByRepository(String repository) {
      Collection<ActiveRule> result = new ArrayList<>();
      activeRules.findByRepository(repository).stream().filter(filterActive).forEach(result::add);
      inactiveRules.findByRepository(repository).stream().filter(filterInactive).forEach(result::add);
      return result;
    }

    @Override
    public Collection<ActiveRule> findByLanguage(String language) {
      Collection<ActiveRule> result = new ArrayList<>();
      activeRules.findByLanguage(language).stream().filter(filterActive).forEach(result::add);
      inactiveRules.findByLanguage(language).stream().filter(filterInactive).forEach(result::add);
      return result;
    }

    @CheckForNull
    @Override
    public ActiveRule findByInternalKey(String repository, String internalKey) {
      ActiveRule activeRule = activeRules.findByInternalKey(repository, internalKey);
      if (activeRule != null && excluded.contains(activeRule.ruleKey())) {
        return null;
      }
      if (activeRule != null) {
        return activeRule;
      }
      ActiveRule inactiveRule = inactiveRules.findByInternalKey(repository, internalKey);
      if (inactiveRule != null && (excluded.contains(inactiveRule.ruleKey()) || !included.contains(inactiveRule.ruleKey()))) {
        return null;
      }
      return inactiveRule;
    }
  }
}


