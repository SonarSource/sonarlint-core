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
package org.sonarsource.sonarlint.core.client.api.standalone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.concurrent.Immutable;
import org.sonarsource.sonarlint.core.client.api.common.AbstractAnalysisConfiguration;

@Immutable
public class StandaloneAnalysisConfiguration extends AbstractAnalysisConfiguration {

  private final Collection<String> excludedRules;
  private final Collection<String> includedRules;
  private final Map<String, Map<String, String>> ruleParametersByRuleKeys;
  private final String toString;

  private StandaloneAnalysisConfiguration(Builder builder) {
    super(builder);
    this.excludedRules = builder.excludedRules;
    this.includedRules = builder.includedRules;
    this.ruleParametersByRuleKeys = builder.ruleParameters;
    this.toString = generateToString();
  }

  public static Builder builder() {
    return new Builder();
  }

  public Collection<String> excludedRules() {
    return excludedRules;
  }

  public Collection<String> includedRules() {
    return includedRules;
  }

  public Map<String, Map<String, String>> ruleParameters() {
    return ruleParametersByRuleKeys;
  }

  @Override
  public String toString() {
    return toString;
  }

  private String generateToString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[\n");
    generateToStringCommon(sb);
    sb.append("  excludedRules: ").append(excludedRules).append("\n");
    sb.append("  includedRules: ").append(includedRules).append("\n");
    sb.append("  ruleParameters: ").append(ruleParametersByRuleKeys).append("\n");
    generateToStringInputFiles(sb);
    sb.append("]\n");
    return sb.toString();
  }

  public static final class Builder extends AbstractBuilder<Builder> {
    private final Collection<String> excludedRules = new ArrayList<>();
    private final Collection<String> includedRules = new ArrayList<>();
    private final Map<String, Map<String, String>> ruleParameters = new LinkedHashMap<>();

    private Builder() {
    }

    public Builder addExcludedRules(String... excludedRuleKeys) {
      Collections.addAll(this.excludedRules, excludedRuleKeys);
      return this;
    }

    public Builder addExcludedRules(Collection<String> excludedRuleKeys) {
      this.excludedRules.addAll(excludedRuleKeys);
      return this;
    }

    public Builder addExcludedRule(String excludedRuleKey) {
      this.excludedRules.add(excludedRuleKey);
      return this;
    }

    public Builder addIncludedRules(String... includedRuleKeys) {
      Collections.addAll(this.includedRules, includedRuleKeys);
      return this;
    }

    public Builder addIncludedRules(Collection<String> includedRuleKeys) {
      this.includedRules.addAll(includedRuleKeys);
      return this;
    }

    public Builder addIncludedRule(String includedRuleKey) {
      this.includedRules.add(includedRuleKey);
      return this;
    }

    public Builder addRuleParameter(String ruleKey, String parameterKey, String parameterValue) {
      this.ruleParameters.computeIfAbsent(ruleKey, k -> new LinkedHashMap<>()).put(parameterKey, parameterValue);
      return this;
    }

    public Builder addRuleParameters(String ruleKey, Map<String, String> parameters) {
      parameters.forEach((k, v) -> this.addRuleParameter(ruleKey, k, v));
      return this;
    }

    public Builder addRuleParameters(Map<String, Map<String, String>> parametersByRuleKeys) {
      parametersByRuleKeys.forEach(this::addRuleParameters);
      return this;
    }

    public StandaloneAnalysisConfiguration build() {
      return new StandaloneAnalysisConfiguration(this);
    }
  }
}
