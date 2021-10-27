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
package org.sonarsource.sonarlint.core;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;

public class ActiveRule {

  private final RuleKey ruleKey;
  private final String ruleName;
  private final String severity;
  private final String type;
  private final String languageKey;
  private Map<String, String> params = Collections.emptyMap();
  private String internalKey = null;
  private String templateRuleKey = null;

  public ActiveRule(RuleKey ruleKey, String type, String severity, String ruleName, String languageKey) {
    this.ruleKey = ruleKey;
    this.ruleName = ruleName;
    this.severity = severity;
    this.type = type;
    this.languageKey = languageKey;
  }

  public RuleKey getRuleKey() {
    return ruleKey;
  }

  public String getRuleName() {
    return ruleName;
  }

  public String getSeverity() {
    return severity;
  }

  public String getType() {
    return type;
  }

  public String getLanguageKey() {
    return languageKey;
  }

  public Map<String, String> getParams() {
    return params;
  }

  public void setParams(Map<String, String> params) {
    this.params = Map.copyOf(params);
  }

  public String getInternalKey() {
    return internalKey;
  }

  public void setInternalKey(String internalKey) {
    this.internalKey = internalKey;
  }

  public String getTemplateRuleKey() {
    return templateRuleKey;
  }

  public void setTemplateRuleKey(String templateRuleKey) {
    this.templateRuleKey = templateRuleKey;
  }

  @Override
  public int hashCode() {
    return Objects.hash(ruleKey);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ActiveRule)) {
      return false;
    }
    ActiveRule other = (ActiveRule) obj;
    return Objects.equals(ruleKey, other.ruleKey);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(ruleKey);
    if (!params.isEmpty()) {
      sb.append(params);
    }
    return sb.toString();
  }

}
