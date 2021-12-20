/*
 * SonarLint Core - Rule Extractor
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
package org.sonarsource.sonarlint.core.rule.extractor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Param;
import org.sonarsource.sonarlint.core.commons.Language;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

public class SonarLintRuleDefinition {

  private final String key;
  private final String name;
  private final String severity;
  private final String type;
  private final String description;
  private final Map<String, SonarLintRuleParamDefinition> params;
  private final Map<String, String> defaultParams = new HashMap<>();
  private final boolean isActiveByDefault;
  private final Language language;
  private final String[] tags;
  private final Set<String> deprecatedKeys;

  public SonarLintRuleDefinition(RulesDefinition.Rule rule) {
    this.key = RuleKey.of(rule.repository().key(), rule.key()).toString();
    this.name = rule.name();
    this.severity = rule.severity();
    this.type = rule.type().toString();
    this.description = requireNonNull(rule.htmlDescription(), "HTML description is mandatory in SonarLint");
    this.isActiveByDefault = rule.activatedByDefault();
    this.language = Language.forKey(rule.repository().language()).orElseThrow(() -> new IllegalStateException("Unknown language with key: " + rule.repository().language()));
    this.tags = rule.tags().toArray(new String[0]);
    this.deprecatedKeys = rule.deprecatedRuleKeys().stream().map(RuleKey::toString).collect(toSet());

    Map<String, SonarLintRuleParamDefinition> builder = new HashMap<>();
    for (Param param : rule.params()) {
      var paramDefinition = new SonarLintRuleParamDefinition(param);
      builder.put(param.key(), paramDefinition);
      String defaultValue = paramDefinition.defaultValue();
      if (defaultValue != null) {
        defaultParams.put(param.key(), defaultValue);
      }
    }
    params = Collections.unmodifiableMap(builder);
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public String getSeverity() {
    return severity;
  }

  public String getType() {
    return type;
  }

  public Map<String, SonarLintRuleParamDefinition> getParams() {
    return params;
  }

  public Map<String, String> getDefaultParams() {
    return defaultParams;
  }

  public boolean isActiveByDefault() {
    return isActiveByDefault;
  }

  public String getHtmlDescription() {
    return description;
  }

  public Language getLanguage() {
    return language;
  }

  public String[] getTags() {
    return tags;
  }

  public Set<String> getDeprecatedKeys() {
    return deprecatedKeys;
  }

  @Override
  public int hashCode() {
    return Objects.hash(key);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SonarLintRuleDefinition)) {
      return false;
    }
    SonarLintRuleDefinition other = (SonarLintRuleDefinition) obj;
    return Objects.equals(key, other.key);
  }

}
