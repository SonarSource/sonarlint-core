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
package org.sonarsource.sonarlint.core.container.storage;

import java.util.Collection;
import java.util.Locale;
import org.sonar.api.batch.rule.RuleParam;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rules.RuleType;
import org.sonarsource.sonarlint.core.container.analysis.SonarLintRule;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerRules;

public class StorageRuleAdapter implements SonarLintRule {

  private final ServerRules.Rule rule;

  public StorageRuleAdapter(ServerRules.Rule rule) {
    this.rule = rule;
  }

  @Override
  public RuleKey key() {
    return RuleKey.parse(rule.getRuleKey());
  }

  @Override
  public String name() {
    return rule.getName();
  }

  @Override
  public String description() {
    return rule.getHtmlDesc();
  }

  @Override
  public String internalKey() {
    return rule.getInternalKey();
  }

  @Override
  public String severity() {
    return rule.getSeverity();
  }

  @Override
  public RuleType type() {
    return RuleType.valueOf(rule.getType().toUpperCase(Locale.ENGLISH));
  }

  @Override
  public RuleParam param(String paramKey) {
    throw new UnsupportedOperationException("param");
  }

  @Override
  public Collection<RuleParam> params() {
    throw new UnsupportedOperationException("param");
  }

  @Override
  public RuleStatus status() {
    throw new UnsupportedOperationException("status");
  }

}
