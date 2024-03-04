/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.client.api.standalone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.concurrent.Immutable;
import org.sonarsource.sonarlint.core.client.api.common.AbstractAnalysisConfiguration;
import org.sonarsource.sonarlint.core.commons.RuleKey;

@Immutable
public class StandaloneAnalysisConfiguration extends AbstractAnalysisConfiguration {

  private final Collection<RuleKey> excludedRules;
  private final Collection<RuleKey> includedRules;
  private final Map<RuleKey, Map<String, String>> ruleParameters;
  private final String toString;

  private StandaloneAnalysisConfiguration(Builder builder) {
    super(builder);
    this.excludedRules = builder.excludedRules;
    this.includedRules = builder.includedRules;
    this.ruleParameters = builder.ruleParameters;
    this.toString = generateToString();
  }

  public static Builder builder() {
    return new Builder();
  }

  public Collection<RuleKey> excludedRules() {
    return excludedRules;
  }

  public Collection<RuleKey> includedRules() {
    return includedRules;
  }

  public Map<RuleKey, Map<String, String>> ruleParameters() {
    return ruleParameters;
  }

  @Override
  public String toString() {
    return toString;
  }

  private String generateToString() {
    var sb = new StringBuilder();
    sb.append("[\n");
    generateToStringCommon(sb);
    sb.append("  excludedRules: ").append(excludedRules).append("\n");
    sb.append("  includedRules: ").append(includedRules).append("\n");
    sb.append("  ruleParameters: ").append(ruleParameters).append("\n");
    generateToStringInputFiles(sb);
    sb.append("]\n");
    return sb.toString();
  }

  public static final class Builder extends AbstractBuilder<Builder> {
    private final Collection<RuleKey> excludedRules = new ArrayList<>();
    private final Collection<RuleKey> includedRules = new ArrayList<>();
    private final Map<RuleKey, Map<String, String>> ruleParameters = new LinkedHashMap<>();

    private Builder() {
    }

    public Builder addExcludedRules(RuleKey... excludedRules) {
      Collections.addAll(this.excludedRules, excludedRules);
      return this;
    }

    public Builder addExcludedRules(Collection<? extends RuleKey> excludedRules) {
      this.excludedRules.addAll(excludedRules);
      return this;
    }

    public Builder addExcludedRule(RuleKey excludedRule) {
      this.excludedRules.add(excludedRule);
      return this;
    }

    public Builder addIncludedRules(RuleKey... includedRules) {
      Collections.addAll(this.includedRules, includedRules);
      return this;
    }

    public Builder addIncludedRules(Collection<? extends RuleKey> includedRules) {
      this.includedRules.addAll(includedRules);
      return this;
    }

    public Builder addIncludedRule(RuleKey includedRule) {
      this.includedRules.add(includedRule);
      return this;
    }

    public Builder addRuleParameter(RuleKey rule, String parameterKey, String parameterValue) {
      this.ruleParameters.computeIfAbsent(rule, k -> new LinkedHashMap<>()).put(parameterKey, parameterValue);
      return this;
    }

    public Builder addRuleParameters(RuleKey rule, Map<String, String> parameters) {
      parameters.forEach((k, v) -> this.addRuleParameter(rule, k, v));
      return this;
    }

    public Builder addRuleParameters(Map<RuleKey, Map<String, String>> parameters) {
      parameters.forEach(this::addRuleParameters);
      return this;
    }

    public StandaloneAnalysisConfiguration build() {
      return new StandaloneAnalysisConfiguration(this);
    }
  }
}
