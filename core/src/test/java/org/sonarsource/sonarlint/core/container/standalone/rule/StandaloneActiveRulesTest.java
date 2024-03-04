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
package org.sonarsource.sonarlint.core.container.standalone.rule;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StandaloneActiveRulesTest {

  private static final String REPOSITORY = "squid";

  private static final String INACTIVE_RULE = "INACTIVE_RULE";
  private static final String INACTIVE_INCLUDED_RULE = "INACTIVE_INCLUDED_RULE";
  private static final String ACTIVE_RULE = "ACTIVE_RULE";
  private static final String ACTIVE_EXCLUDED_RULE = "ACTIVE_EXCLUDED_RULE";

  private final ActiveRules underTest;

  public StandaloneActiveRulesTest() {
    StandaloneActiveRules standaloneActiveRules = new StandaloneActiveRules(asList(mockActiveRule(ACTIVE_RULE, true),
      mockActiveRule(ACTIVE_EXCLUDED_RULE, true),
      mockActiveRule(INACTIVE_RULE, false),
      mockActiveRule(INACTIVE_INCLUDED_RULE, false)));

    String activeRuleKey = new RuleKey(REPOSITORY, ACTIVE_EXCLUDED_RULE).toString();
    Set<String> excluded = Collections.singleton(activeRuleKey);
    Set<String> included = Collections.singleton(new RuleKey(REPOSITORY, INACTIVE_INCLUDED_RULE).toString());
    Map<String, Map<String, String>> params = Collections.emptyMap();
    underTest = standaloneActiveRules.filtered(excluded, included, params);
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
    assertThat(underTest.findByLanguage(Language.JAVA.getLanguageKey()).stream().map(r -> r.ruleKey().rule())).containsExactlyInAnyOrder(ACTIVE_RULE, INACTIVE_INCLUDED_RULE);
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

  private static StandaloneRule mockActiveRule(String rule, boolean activeByDefault) {
    StandaloneRule activeRule = mock(StandaloneRule.class);
    org.sonar.api.rule.RuleKey ruleKey = org.sonar.api.rule.RuleKey.of(REPOSITORY, rule);
    when(activeRule.key()).thenReturn(ruleKey);
    when(activeRule.getKey()).thenReturn(ruleKey.toString());
    when(activeRule.getLanguage()).thenReturn(Language.JAVA);
    when(activeRule.internalKey()).thenReturn(rule);
    when(activeRule.isActiveByDefault()).thenReturn(activeByDefault);
    return activeRule;
  }
}
