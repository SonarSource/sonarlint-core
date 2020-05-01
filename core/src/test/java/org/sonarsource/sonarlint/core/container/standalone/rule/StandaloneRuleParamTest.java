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

import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.sonar.api.server.rule.RuleParamType;
import org.sonar.api.server.rule.RulesDefinition;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class StandaloneRuleParamTest {

  @Parameters(name="Plugin API {0} => Client API {1}, multiple={2}, values={3}")
  public static Iterable<Object[]> data() {
    return asList(new Object[][] {
      {RuleParamType.STRING,
        StandaloneRuleParamType.STRING, false, emptyList()},
      {RuleParamType.TEXT,
        StandaloneRuleParamType.TEXT, false, emptyList()},
      {RuleParamType.INTEGER,
        StandaloneRuleParamType.INTEGER, false, emptyList()},
      {RuleParamType.FLOAT,
        StandaloneRuleParamType.FLOAT, false, emptyList()},
      {RuleParamType.BOOLEAN,
        StandaloneRuleParamType.BOOLEAN, false, emptyList()},
      {RuleParamType.singleListOfValues("polop", "palap"),
        StandaloneRuleParamType.STRING, false, asList("polop", "palap")},
      {RuleParamType.multipleListOfValues("polop", "palap"),
        StandaloneRuleParamType.STRING, true, asList("polop", "palap")},
      {RuleParamType.parse("INTEGER,values=\"1,2,3\",multiple=true"),
        StandaloneRuleParamType.INTEGER, true, asList("1", "2", "3")},
      {RuleParamType.parse("FLOAT,values=\"1.0,2.0,3.0\""),
        StandaloneRuleParamType.FLOAT, false, asList("1.0", "2.0", "3.0")},
      {RuleParamType.parse("unknown"),
        StandaloneRuleParamType.STRING, false, emptyList()}
    });
  }

  private final RuleParamType apiType;
  private final StandaloneRuleParamType expectedType;
  private final boolean expectedMultiple;
  private final List<String> expectedValues;

  public StandaloneRuleParamTest(RuleParamType apiType, StandaloneRuleParamType expectedType, boolean expectedMultiple, List<String> expectedValues) {
    this.apiType = apiType;
    this.expectedType = expectedType;
    this.expectedMultiple = expectedMultiple;
    this.expectedValues = expectedValues;
  }

  @Test
  public void shouldConvertApiType() {
    RulesDefinition.Param apiParam = mock(RulesDefinition.Param.class);
    when(apiParam.type()).thenReturn(apiType);
    StandaloneRuleParam param = new StandaloneRuleParam(apiParam);
    assertThat(param.type()).isEqualTo(expectedType);
    assertThat(param.multiple()).isEqualTo(expectedMultiple);
    assertThat(param.possibleValues()).isEqualTo(expectedValues);
  }
}
