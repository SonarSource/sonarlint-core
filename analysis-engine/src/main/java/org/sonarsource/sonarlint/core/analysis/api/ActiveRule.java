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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class ActiveRule {

  private final String ruleKey;
  private final String languageKey;
  private Map<String, String> params = Collections.emptyMap();
  private String templateRuleKey = null;

  public ActiveRule(String ruleKey, String languageKey) {
    this.ruleKey = ruleKey;
    this.languageKey = languageKey;
  }

  public String getRuleKey() {
    return ruleKey;
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

  @CheckForNull
  public String getTemplateRuleKey() {
    return templateRuleKey;
  }

  public void setTemplateRuleKey(@Nullable String templateRuleKey) {
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
