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
package org.sonarsource.sonarlint.core.container.standalone.rule;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import org.junit.Test;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StandaloneActiveRulesTest {

  private static final String REPOSITORY = "squid";
  private static final String LANGUAGE = "java";

  private static final String INACTIVE_RULE = "INACTIVE_RULE";
  private static final String INACTIVE_INCLUDED_RULE = "INACTIVE_INCLUDED_RULE";
  private static final String ACTIVE_RULE = "ACTIVE_RULE";
  private static final String ACTIVE_EXCLUDED_RULE = "ACTIVE_EXCLUDED_RULE";

  private final ActiveRules underTest;

  public StandaloneActiveRulesTest() {
    ActiveRules activeRules = new FakeActiveRules(ACTIVE_RULE, ACTIVE_EXCLUDED_RULE);
    ActiveRules inactiveRules = new FakeActiveRules(INACTIVE_RULE, INACTIVE_INCLUDED_RULE);
    StandaloneActiveRules standaloneActiveRules = new StandaloneActiveRules(activeRules, inactiveRules, Collections.emptyMap());

    Set<String> excluded = Collections.singleton(new RuleKey(REPOSITORY, ACTIVE_EXCLUDED_RULE).toString());
    Set<String> included = Collections.singleton(new RuleKey(REPOSITORY, INACTIVE_INCLUDED_RULE).toString());
    underTest = standaloneActiveRules.filtered(excluded, included);
  }

  @Test
  public void find_returns_null_for_excluded_or_inactive_rule() {
    // should not find
    assertThat(underTest.find(org.sonar.api.rule.RuleKey.of(REPOSITORY, ACTIVE_EXCLUDED_RULE))).isNull();
    assertThat(underTest.find(org.sonar.api.rule.RuleKey.of(REPOSITORY, INACTIVE_RULE))).isNull();

    // should find
    assertThat(underTest.find(org.sonar.api.rule.RuleKey.of(REPOSITORY, ACTIVE_RULE))).isNotNull();
    assertThat(underTest.find(org.sonar.api.rule.RuleKey.of(REPOSITORY, INACTIVE_INCLUDED_RULE))).isNotNull();
  }

  @Test
  public void findAll_omits_excluded_rule_and_includes_included_rule() {
    assertThat(underTest.findAll().stream().map(r -> r.ruleKey().rule())).containsExactlyInAnyOrder(ACTIVE_RULE, INACTIVE_INCLUDED_RULE);
  }

  @Test
  public void findByRepository_omits_excluded_rule_and_includes_included_rule() {
    assertThat(underTest.findByRepository(REPOSITORY).stream().map(r -> r.ruleKey().rule())).containsExactlyInAnyOrder(ACTIVE_RULE, INACTIVE_INCLUDED_RULE);
  }

  @Test
  public void findByLanguage_omits_excluded_rule_and_includes_included_rule() {
    assertThat(underTest.findByLanguage(LANGUAGE).stream().map(r -> r.ruleKey().rule())).containsExactlyInAnyOrder(ACTIVE_RULE, INACTIVE_INCLUDED_RULE);
  }

  @Test
  public void findByInternalKey_omits_excluded_rule_and_includes_included_rule() {
    // should not find
    assertThat(underTest.findByInternalKey(REPOSITORY, ACTIVE_EXCLUDED_RULE)).isNull();
    assertThat(underTest.findByInternalKey(REPOSITORY, INACTIVE_RULE)).isNull();

    // should find
    assertThat(underTest.findByInternalKey(REPOSITORY, ACTIVE_RULE)).isNotNull();
    assertThat(underTest.findByInternalKey(REPOSITORY, INACTIVE_INCLUDED_RULE)).isNotNull();
  }

  private static class FakeActiveRules implements ActiveRules {
    private final Map<org.sonar.api.rule.RuleKey, ActiveRule> map = new HashMap<>();

    FakeActiveRules(String... rules) {
      for (String rule : rules) {
        ActiveRule activeRule = mock(ActiveRule.class);
        org.sonar.api.rule.RuleKey ruleKey = org.sonar.api.rule.RuleKey.of(REPOSITORY, rule);
        when(activeRule.ruleKey()).thenReturn(ruleKey);
        when(activeRule.language()).thenReturn(LANGUAGE);
        when(activeRule.internalKey()).thenReturn(rule);
        map.put(ruleKey, activeRule);
      }
    }

    @CheckForNull
    @Override
    public ActiveRule find(org.sonar.api.rule.RuleKey ruleKey) {
      return map.get(ruleKey);
    }

    @Override
    public Collection<ActiveRule> findAll() {
      return map.values();
    }

    @Override
    public Collection<ActiveRule> findByRepository(String s) {
      return map.values();
    }

    @Override
    public Collection<ActiveRule> findByLanguage(String s) {
      return map.values();
    }

    @CheckForNull
    @Override
    public ActiveRule findByInternalKey(String repository, String internalKey) {
      return map.get(org.sonar.api.rule.RuleKey.of(repository, internalKey));
    }
  }
}
