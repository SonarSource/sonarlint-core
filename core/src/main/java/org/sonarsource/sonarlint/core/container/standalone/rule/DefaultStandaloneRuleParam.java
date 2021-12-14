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

import java.util.List;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParam;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParamType;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleParamDefinition;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleParamType;

public class DefaultStandaloneRuleParam implements StandaloneRuleParam {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final String key;
  private final String name;
  private final String description;
  private final String defaultValue;
  private final StandaloneRuleParamType type;
  private final boolean multiple;
  private final List<String> possibleValues;

  public DefaultStandaloneRuleParam(SonarLintRuleParamDefinition param) {
    this.key = param.key();
    this.name = param.name();
    this.description = param.description();
    this.defaultValue = param.defaultValue();
    SonarLintRuleParamType apiType = param.type();
    this.type = from(apiType);
    this.multiple = param.multiple();
    this.possibleValues = List.copyOf(param.possibleValues());
  }

  private static StandaloneRuleParamType from(SonarLintRuleParamType apiType) {
    try {
      return StandaloneRuleParamType.valueOf(apiType.name());
    } catch (IllegalArgumentException unknownType) {
      LOG.warn("Unknown parameter type: " + apiType.name());
      return StandaloneRuleParamType.STRING;
    }
  }

  @Override
  public String key() {
    return key;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  @CheckForNull
  public String defaultValue() {
    return defaultValue;
  }

  @Override
  public StandaloneRuleParamType type() {
    return type;
  }

  @Override
  public boolean multiple() {
    return multiple;
  }

  @Override
  public List<String> possibleValues() {
    return possibleValues;
  }
}
