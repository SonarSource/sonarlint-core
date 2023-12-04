/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.sonarapi;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.rule.RuleKey;

public class ActiveRuleAdapter implements ActiveRule {

  private final org.sonarsource.sonarlint.core.analysis.api.ActiveRule activeRule;

  public ActiveRuleAdapter(org.sonarsource.sonarlint.core.analysis.api.ActiveRule activeRule) {
    this.activeRule = activeRule;
  }

  @Override
  public RuleKey ruleKey() {
    return RuleKey.parse(activeRule.getRuleKey());
  }

  @Override
  public String severity() {
    throw new UnsupportedOperationException("severity not supported in SonarLint");
  }

  @Override
  public String language() {
    return activeRule.getLanguageKey();
  }

  @Override
  public String param(String key) {
    return params().get(key);
  }

  @Override
  public Map<String, String> params() {
    return activeRule.getParams();
  }

  @Override
  public String internalKey() {
    // This is a hack for old versions of CFamily (https://github.com/SonarSource/sonar-cpp/pull/1598)
    return ruleKey().rule();
  }

  @Override
  public String templateRuleKey() {
    var templateRuleKey = activeRule.getTemplateRuleKey();
    if (!StringUtils.isEmpty(templateRuleKey)) {
      // The SQ plugin API expect template rule key to be only the "rule" part of the key (without the repository key)
      var ruleKey = RuleKey.parse(templateRuleKey);
      return ruleKey.rule();
    }
    return null;
  }

  @Override
  public String qpKey() {
    throw new UnsupportedOperationException("qpKey not supported in SonarLint");
  }

}
