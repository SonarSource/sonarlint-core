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
package org.sonarsource.sonarlint.core.container.standalone;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.junit.Test;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ActiveRulesWithExclusionsTest {

  public static final String REPOSITORY = "squid";
  public static final String EXCLUDED_RULE = "S1135";
  public static final String NON_EXCLUDED_RULE = "S2187";

  private final ActiveRules activeRules = new FakeActiveRules(EXCLUDED_RULE, NON_EXCLUDED_RULE);

  private final ActiveRulesWithExclusions underTest = new ActiveRulesWithExclusions(activeRules, Collections.singleton(new RuleKey(REPOSITORY, EXCLUDED_RULE)));

  @Test
  public void find_returns_null_for_excluded_rule() {
    assertThat(underTest.find(org.sonar.api.rule.RuleKey.of(REPOSITORY, NON_EXCLUDED_RULE))).isNotNull();
    assertThat(underTest.find(org.sonar.api.rule.RuleKey.of(REPOSITORY, EXCLUDED_RULE))).isNull();
  }

  @Test
  public void findAll_omits_excluded_rule() {
    assertThat(activeRules.findAll().stream().map(r -> r.ruleKey().rule())).containsExactlyInAnyOrder(EXCLUDED_RULE, NON_EXCLUDED_RULE);
    assertThat(underTest.findAll().stream().map(r -> r.ruleKey().rule())).containsExactlyInAnyOrder(NON_EXCLUDED_RULE);
  }

  @Test
  public void findByRepository_omits_excluded_rule() {
    assertThat(activeRules.findByRepository("foo").stream().map(r -> r.ruleKey().rule())).containsExactlyInAnyOrder(EXCLUDED_RULE, NON_EXCLUDED_RULE);
    assertThat(underTest.findByRepository("foo").stream().map(r -> r.ruleKey().rule())).containsExactlyInAnyOrder(NON_EXCLUDED_RULE);
  }

  @Test
  public void findByLanguage_omits_excluded_rule() {
    assertThat(activeRules.findByLanguage("foo").stream().map(r -> r.ruleKey().rule())).containsExactlyInAnyOrder(EXCLUDED_RULE, NON_EXCLUDED_RULE);
    assertThat(underTest.findByLanguage("foo").stream().map(r -> r.ruleKey().rule())).containsExactlyInAnyOrder(NON_EXCLUDED_RULE);
  }

  @Test
  public void findByInternalKey_omits_excluded_rule() {
    assertThat(underTest.findByInternalKey(REPOSITORY, NON_EXCLUDED_RULE)).isNotNull();
    assertThat(underTest.findByInternalKey(REPOSITORY, EXCLUDED_RULE)).isNull();
  }

  private static class FakeActiveRules implements ActiveRules {
    private final Map<org.sonar.api.rule.RuleKey, ActiveRule> map = new HashMap<>();

    FakeActiveRules(String... rules) {
      for (String rule : rules) {
        ActiveRule activeRule = mock(ActiveRule.class);
        org.sonar.api.rule.RuleKey ruleKey = org.sonar.api.rule.RuleKey.of(REPOSITORY, rule);
        when(activeRule.ruleKey()).thenReturn(ruleKey);
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
  };
}
