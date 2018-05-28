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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.Rule;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition;

/**
 * A cache of ActiveRule instances, combining Rules and ActiveRules.
 */
public class CombinedActiveRules implements ActiveRules {

  private final Collection<StandaloneActiveRule> all = new HashSet<>();
  private final Collection<RuleKey> activeRuleKeysByDefault;

  private final Map<RuleKey, ActiveRule> activeRulesByRuleKey = new HashMap<>();
  private final Map<String, Collection<ActiveRule>> activeRulesByRepository = new HashMap<>();
  private final Map<String, Collection<ActiveRule>> activeRulesByLanguage = new HashMap<>();
  private final Map<String, Map<String, ActiveRule>> activeRulesByInternalKey = new HashMap<>();

  public CombinedActiveRules(RulesDefinition.Context rulesDefinition, Rules rules, ActiveRules activeRules) {
    this.activeRuleKeysByDefault = activeRules.findAll().stream().map(ActiveRule::ruleKey).collect(Collectors.toSet());

    Map<RuleKey, String> activeRuleNames = new HashMap<>();
    Map<RuleKey, String> activeRuleDescriptions = new HashMap<>();

    rules.findAll().stream()
      .filter(rule -> activeRuleKeysByDefault.contains(rule.key()))
      .forEach(rule -> {
        activeRuleNames.put(rule.key(), rule.name());
        activeRuleDescriptions.put(rule.key(), rule.description());
      });

    activeRules.findAll().stream()
      .map(ar -> StandaloneActiveRule.of(ar, activeRuleNames.get(ar.ruleKey()), activeRuleDescriptions.get(ar.ruleKey())))
      .forEach(this::populateCaches);

    rules.findAll().stream()
      .filter(rule -> !activeRulesByRuleKey.containsKey(rule.key()))
      .map(rule -> StandaloneActiveRule.of(rule, rulesDefinition.repository(rule.key().repository()).language()))
      .forEach(this::populateCaches);
  }

  private void populateCaches(StandaloneActiveRule activeRule) {
    all.add(activeRule);
    activeRulesByRuleKey.put(activeRule.ruleKey(), activeRule);
    activeRulesByRepository.computeIfAbsent(activeRule.ruleKey().repository(), key -> new HashSet<>()).add(activeRule);
    activeRulesByLanguage.computeIfAbsent(activeRule.language(), key -> new HashSet<>()).add(activeRule);
    activeRulesByInternalKey.computeIfAbsent(activeRule.ruleKey().repository(), key -> new HashMap<>())
      .put(activeRule.internalKey(), activeRule);
  }

  @CheckForNull
  @Override
  public ActiveRule find(RuleKey ruleKey) {
    return activeRulesByRuleKey.get(ruleKey);
  }

  @Override
  public Collection<ActiveRule> findAll() {
    return new ArrayList<>(all);
  }

  @Override
  public Collection<ActiveRule> findByRepository(String repository) {
    return new ArrayList<>(activeRulesByRepository.getOrDefault(repository, Collections.emptyList()));
  }

  @Override
  public Collection<ActiveRule> findByLanguage(String language) {
    return new ArrayList<>(activeRulesByLanguage.getOrDefault(language, Collections.emptyList()));
  }

  @CheckForNull
  @Override
  public ActiveRule findByInternalKey(String repository, String internalKey) {
    return activeRulesByInternalKey.getOrDefault(repository, Collections.emptyMap()).get(internalKey);
  }

  public boolean exists(RuleKey ruleKey) {
    return activeRulesByRuleKey.containsKey(ruleKey);
  }

  public boolean isActiveByDefault(RuleKey ruleKey) {
    return activeRuleKeysByDefault.contains(ruleKey);
  }

  public Collection<StandaloneActiveRule> findAllStandalone() {
    return new ArrayList<>(all);
  }

  public interface StandaloneActiveRule extends ActiveRule {

    String name();

    String description();

    static StandaloneActiveRule of(ActiveRule activeRule, String name, String description) {
      return new StandaloneActiveRule() {
        @Override
        public String name() {
          return name;
        }

        @Override
        public String description() {
          return description;
        }

        @Override
        public RuleKey ruleKey() {
          return activeRule.ruleKey();
        }

        @Override
        public String severity() {
          return activeRule.severity();
        }

        @Override
        public String language() {
          return activeRule.language();
        }

        @CheckForNull
        @Override
        public String param(String key) {
          return activeRule.param(key);
        }

        @Override
        public Map<String, String> params() {
          return activeRule.params();
        }

        @CheckForNull
        @Override
        public String internalKey() {
          return activeRule.internalKey();
        }

        @CheckForNull
        @Override
        public String templateRuleKey() {
          return activeRule.templateRuleKey();
        }
      };
    }

    static StandaloneActiveRule of(Rule rule, String language) {
      return new StandaloneActiveRule() {
        @Override
        public String name() {
          return rule.name();
        }

        @Override
        public String description() {
          return rule.description();
        }

        @Override
        public RuleKey ruleKey() {
          return rule.key();
        }

        @Override
        public String severity() {
          return rule.severity();
        }

        @Override
        public String language() {
          return language;
        }

        @CheckForNull
        @Override
        public String param(String key) {
          return null;
        }

        @Override
        public Map<String, String> params() {
          return Collections.emptyMap();
        }

        @CheckForNull
        @Override
        public String internalKey() {
          return rule.internalKey();
        }

        @CheckForNull
        @Override
        public String templateRuleKey() {
          return null;
        }
      };
    }
  }
}
