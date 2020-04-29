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
import javax.annotation.CheckForNull;
import org.sonar.api.batch.rule.RuleParam;
import org.sonar.api.server.rule.RuleParamType;
import org.sonar.api.server.rule.RulesDefinition.Param;

public class StandaloneRuleParam implements RuleParam {

  private final String key;
  private final String description;
  private final String defaultValue;
  private final StandaloneRuleParamType type;
  private final List<String> possibleValues;

  public StandaloneRuleParam(Param param) {
    this.key = param.key();
    this.description = param.description();
    this.defaultValue = param.defaultValue();
    RuleParamType apiType = param.type();
    this.type = StandaloneRuleParamType.from(apiType);
    this.possibleValues = apiType.values();
  }

  @Override
  public String key() {
    return key;
  }

  @Override
  public String description() {
    return description;
  }

  @CheckForNull
  public String defaultValue() {
    return defaultValue;
  }

  public StandaloneRuleParamType type() {
    return type;
  }

  public List<String> possibleValues() {
    return possibleValues;
  }
}
