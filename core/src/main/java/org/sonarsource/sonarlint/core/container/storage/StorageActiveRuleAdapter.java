/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.storage;

import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules.Rule;

public class StorageActiveRuleAdapter implements ActiveRule {

  private final org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules.ActiveRule activeRule;
  private final Rule storageRule;

  public StorageActiveRuleAdapter(org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules.ActiveRule activeRule, Rule storageRule) {
    this.activeRule = activeRule;
    this.storageRule = storageRule;
  }

  @Override
  public RuleKey ruleKey() {
    return RuleKey.of(activeRule.getRepo(), activeRule.getKey());
  }

  @Override
  public String severity() {
    return activeRule.getSeverity();
  }

  @Override
  public String language() {
    return storageRule.getLang();
  }

  @Override
  public String param(String key) {
    return activeRule.getParamsMap().get(key);
  }

  @Override
  public Map<String, String> params() {
    return activeRule.getParamsMap();
  }

  @Override
  public String internalKey() {
    return storageRule.getInternalKey();
  }

  @Override
  public String templateRuleKey() {
    if (!StringUtils.isEmpty(storageRule.getTemplateKey())) {
      RuleKey templateRuleKey = RuleKey.parse(storageRule.getTemplateKey());
      return templateRuleKey.rule();
    }
    return null;
  }

  @Override
  public String qpKey() {
    throw new UnsupportedOperationException("qpKey");
  }

}
