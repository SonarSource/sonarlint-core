/*
 * SonarLint Core - Rule Extractor
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.rule.extractor;

import java.util.Collections;
import java.util.List;
import javax.annotation.CheckForNull;
import org.sonar.api.server.rule.RuleParamType;
import org.sonar.api.server.rule.RulesDefinition.Param;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class SonarLintRuleParamDefinition {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final String key;
  private final String name;
  private final String description;
  private final String defaultValue;
  private final SonarLintRuleParamType type;
  private final boolean multiple;
  private final List<String> possibleValues;

  public SonarLintRuleParamDefinition(Param param) {
    this.key = param.key();
    this.name = param.name();
    this.description = param.description();
    this.defaultValue = param.defaultValue();
    var apiType = param.type();
    this.type = from(apiType);
    this.multiple = apiType.multiple();
    this.possibleValues = Collections.unmodifiableList(apiType.values());
  }

  private static SonarLintRuleParamType from(RuleParamType apiType) {
    try {
      return SonarLintRuleParamType.valueOf(apiType.type());
    } catch (IllegalArgumentException unknownType) {
      LOG.warn("Unknown parameter type: " + apiType.type());
      return SonarLintRuleParamType.STRING;
    }
  }

  public String key() {
    return key;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  @CheckForNull
  public String defaultValue() {
    return defaultValue;
  }

  public SonarLintRuleParamType type() {
    return type;
  }

  public boolean multiple() {
    return multiple;
  }

  public List<String> possibleValues() {
    return possibleValues;
  }
}
