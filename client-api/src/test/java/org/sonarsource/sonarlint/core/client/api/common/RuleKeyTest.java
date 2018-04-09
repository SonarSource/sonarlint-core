/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.client.api.common;

import org.junit.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class RuleKeyTest {

  @Test
  public void test_ruleKey_accessors() {
    String repository = "squid";
    String rule = "1181";

    RuleKey ruleKey = new RuleKey(repository, rule);
    assertThat(ruleKey.repository()).isEqualTo(repository);
    assertThat(ruleKey.rule()).isEqualTo(rule);
    assertThat(ruleKey.toString()).isEqualTo(repository + ":" + rule);
  }

  @Test
  public void ruleKey_is_hashable() {
    String repository = "squid";
    String rule = "1181";

    RuleKey ruleKey1 = new RuleKey(repository, rule);
    RuleKey ruleKey2 = new RuleKey(repository, rule);
    assertThat(ruleKey1).isEqualTo(ruleKey1);
    assertThat(ruleKey1).isEqualTo(ruleKey2);
    assertThat(ruleKey1.hashCode()).isEqualTo(ruleKey2.hashCode());

    assertThat(ruleKey1).isNotEqualTo(null);
    assertThat(ruleKey1).isNotEqualTo(new RuleKey(repository, rule + "x"));
    assertThat(ruleKey1).isNotEqualTo(new RuleKey(repository + "x", rule));
  }
}
