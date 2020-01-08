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

import java.util.Map;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.RuleParam;
import org.sonar.api.rule.RuleKey;

import static java.util.stream.Collectors.toMap;

public class StandaloneActiveRuleAdapter implements ActiveRule {

  private final StandaloneRule rule;

  public StandaloneActiveRuleAdapter(StandaloneRule rule) {
    this.rule = rule;
  }

  @Override
  public RuleKey ruleKey() {
    return rule.key();
  }

  @Override
  public String severity() {
    return rule.severity();
  }

  @Override
  public String language() {
    return rule.getLanguageKey();
  }

  @Override
  public String param(String key) {
    return rule.param(key).defaultValue();
  }

  @Override
  public Map<String, String> params() {
    return rule.params().stream()
      .filter(p -> ((StandaloneRuleParam) p).defaultValue() != null)
      .collect(toMap(RuleParam::key, p -> ((StandaloneRuleParam) p).defaultValue()));
  }

  @Override
  public String internalKey() {
    return rule.internalKey();
  }

  @Override
  public String templateRuleKey() {
    return null;
  }

  @Override
  public String qpKey() {
    throw new UnsupportedOperationException("qpKey");
  }

}
