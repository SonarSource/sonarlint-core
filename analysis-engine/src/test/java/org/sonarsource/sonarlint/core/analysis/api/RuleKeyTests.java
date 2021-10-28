/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.api;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.analysis.api.RuleKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleKeyTests {

  @Test
  void test_ruleKey_accessors() {
    String repository = "squid";
    String rule = "1181";

    RuleKey ruleKey = new RuleKey(repository, rule);
    assertThat(ruleKey.repository()).isEqualTo(repository);
    assertThat(ruleKey.rule()).isEqualTo(rule);
    assertThat(ruleKey).hasToString(repository + ":" + rule);
  }

  @Test
  void ruleKey_equals_and_hashcode() {
    String repository = "squid";
    String rule = "1181";

    RuleKey ruleKey1 = new RuleKey(repository, rule);
    RuleKey ruleKey2 = new RuleKey(repository, rule);
    assertThat(ruleKey1)
      .isEqualTo(ruleKey1)
      .isEqualTo(ruleKey2)
      .hasSameHashCodeAs(ruleKey2)
      .isNotEqualTo(null)
      .isNotEqualTo(new RuleKey(repository, rule + "x"))
      .isNotEqualTo(new RuleKey(repository + "x", rule));
  }

  @Test
  void ruleKey_equals_to_its_parsed_from_toString() {
    String repository = "squid";
    String rule = "1181";

    RuleKey ruleKey1 = new RuleKey(repository, rule);
    RuleKey ruleKey2 = RuleKey.parse(ruleKey1.toString());
    assertThat(ruleKey2).isEqualTo(ruleKey1);
  }

  @Test
  void parse_throws_for_illegal_format() {
    assertThrows(IllegalArgumentException.class, () -> {
      RuleKey.parse("foo");
    });
  }
}
