/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import java.util.Collections;
import java.util.function.Function;

import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.container.model.DefaultRuleDetails;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

public class StorageRuleDetailsReader implements Function<String, RuleDetails> {
  private StorageManager storageManager;

  public StorageRuleDetailsReader(StorageManager storageManager) {
    this.storageManager = storageManager;
  }

  @Override
  public RuleDetails apply(String ruleKeyStr) {
    Sonarlint.Rules rulesFromStorage = storageManager.readRulesFromStorage();
    RuleKey ruleKey = RuleKey.parse(ruleKeyStr);
    Sonarlint.Rules.Rule rule = rulesFromStorage.getRulesByKey().get(ruleKeyStr);
    if (rule == null) {
      throw new IllegalArgumentException("Unable to find rule with key " + ruleKey);
    }
    return new DefaultRuleDetails(ruleKeyStr, rule.getName(), rule.getHtmlDesc(), rule.getSeverity(), rule.getLang(), Collections.<String>emptySet(), rule.getHtmlNote());
  }
}
