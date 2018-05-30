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
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;

public class StandaloneActiveRules {
  public final ActiveRules activeRules;
  private final ActiveRules inactiveRules;
  private final Map<String, RuleDetails> ruleDetails;

  StandaloneActiveRules(ActiveRules activeRules, ActiveRules inactiveRules, Map<String, RuleDetails> ruleDetails) {
    this.activeRules = activeRules;
    this.inactiveRules = inactiveRules;
    this.ruleDetails = ruleDetails;
  }

  public ActiveRules filtered(Collection<RuleKey> excluded, Collection<RuleKey> included) {
    if (included.isEmpty() && excluded.isEmpty()) {
      return activeRules;
    }

    return filtered(convert(excluded), convert(included));
  }

  /**
   * The conversion is necessary, because the client is not coupled to SonarQube API.
   */
  private static Set<org.sonar.api.rule.RuleKey> convert(Collection<RuleKey> includedRuleKeys) {
    return includedRuleKeys.stream().map(r -> org.sonar.api.rule.RuleKey.of(r.repository(), r.rule())).collect(Collectors.toSet());
  }

  private ActiveRules filtered(Set<org.sonar.api.rule.RuleKey> excluded, Set<org.sonar.api.rule.RuleKey> included) {
    // accept from active those that are not explicitly excluded
    Predicate<ActiveRule> filterActive = ar -> !excluded.contains(ar.ruleKey());

    // accept from inactive those that are explicitly included
    Predicate<ActiveRule> filterInactive = ar -> included.contains(ar.ruleKey());

    return new ActiveRules() {
      @CheckForNull
      @Override
      public ActiveRule find(org.sonar.api.rule.RuleKey ruleKey) {
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
    };
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

  public Collection<RuleDetails> getAllRuleDetails() {
    return ruleDetails.values();
  }
}
