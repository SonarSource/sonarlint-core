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
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.rule.RuleKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SanitizedActiveRulesFilterTest {

  private static final String REPOSITORY = "squid";

  private static final RuleKey INACTIVE_RULE = RuleKey.of(REPOSITORY, "INACTIVE_RULE");
  private static final RuleKey ACTIVE_RULE = RuleKey.of(REPOSITORY, "ACTIVE_RULE");

  private StandaloneActiveRules standaloneActiveRules;

  @Before
  public void before() {
    standaloneActiveRules = mock(StandaloneActiveRules.class);
    when(standaloneActiveRules.isActiveByDefault(ACTIVE_RULE)).thenReturn(true);
  }

  @Test
  public void ignore_when_excluded_and_included_inactive() {
    SanitizedActiveRulesFilter filter = sanitizedActiveRulesFilterBuilder().exclude(INACTIVE_RULE).include(INACTIVE_RULE).build();
    assertThat(filter.excluded()).isEmpty();
    assertThat(filter.included()).isEmpty();
  }

  @Test
  public void exclude_when_excluded_and_included_active() {
    SanitizedActiveRulesFilter filter = sanitizedActiveRulesFilterBuilder().exclude(ACTIVE_RULE).include(ACTIVE_RULE).build();
    assertThat(filter.excluded()).containsOnly(ACTIVE_RULE);
    assertThat(filter.included()).isEmpty();
  }

  @Test
  public void ignore_when_excluded_inactive() {
    SanitizedActiveRulesFilter filter = sanitizedActiveRulesFilterBuilder().exclude(INACTIVE_RULE).build();
    assertThat(filter.excluded()).isEmpty();
    assertThat(filter.included()).isEmpty();
  }

  @Test
  public void ignore_when_included_active() {
    SanitizedActiveRulesFilter filter = sanitizedActiveRulesFilterBuilder().include(ACTIVE_RULE).build();
    assertThat(filter.excluded()).isEmpty();
    assertThat(filter.included()).isEmpty();
  }

  @Test
  public void include_when_included_inactive() {
    SanitizedActiveRulesFilter filter = sanitizedActiveRulesFilterBuilder().include(INACTIVE_RULE).build();
    assertThat(filter.excluded()).isEmpty();
    assertThat(filter.included()).containsOnly(INACTIVE_RULE);
  }

  @Test
  public void exclude_when_excluded_active() {
    SanitizedActiveRulesFilter filter = sanitizedActiveRulesFilterBuilder().exclude(ACTIVE_RULE).build();
    assertThat(filter.excluded()).containsOnly(ACTIVE_RULE);
    assertThat(filter.included()).isEmpty();
  }

  private class Builder {
    Collection<org.sonarsource.sonarlint.core.client.api.common.RuleKey> excluded = new ArrayList<>();
    Collection<org.sonarsource.sonarlint.core.client.api.common.RuleKey> included = new ArrayList<>();

    public Builder exclude(RuleKey key) {
      excluded.add(convert(key));
      return this;
    }

    public Builder include(RuleKey key) {
      included.add(convert(key));
      return this;
    }

    public SanitizedActiveRulesFilter build() {
      return new SanitizedActiveRulesFilter(standaloneActiveRules, excluded, included);
    }

    private org.sonarsource.sonarlint.core.client.api.common.RuleKey convert(RuleKey key) {
      return new org.sonarsource.sonarlint.core.client.api.common.RuleKey(key.repository(), key.rule());
    }
  }

  private Builder sanitizedActiveRulesFilterBuilder() {
    return new Builder();
  }
}
