/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2020 SonarSource SA
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

import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.sonar.api.server.rule.RuleParamType;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class StandaloneRuleParamTypeTest {

  @Parameters
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
      {RuleParamType.STRING, StandaloneRuleParamType.STRING},
      {RuleParamType.TEXT, StandaloneRuleParamType.TEXT},
      {RuleParamType.INTEGER, StandaloneRuleParamType.INTEGER},
      {RuleParamType.FLOAT, StandaloneRuleParamType.FLOAT},
      {RuleParamType.BOOLEAN, StandaloneRuleParamType.BOOLEAN},
      {RuleParamType.singleListOfValues("polop", "palap"), StandaloneRuleParamType.SINGLE_SELECT_LIST},
      {RuleParamType.multipleListOfValues("polop", "palap"), StandaloneRuleParamType.MULTI_SELECT_LIST},
      {RuleParamType.parse("unknown"), StandaloneRuleParamType.STRING}
    });
  }

  private final RuleParamType apiType;
  private final StandaloneRuleParamType expectedType;

  public StandaloneRuleParamTypeTest(RuleParamType apiType, StandaloneRuleParamType expectedType) {
    this.apiType = apiType;
    this.expectedType = expectedType;
  }

  @Test
  public void shouldConvertApiType() {
    assertThat(StandaloneRuleParamType.from(apiType)).isEqualTo(expectedType);
  }
}
