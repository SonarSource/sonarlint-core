/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.concurrent.Immutable;
import org.sonar.api.rule.RuleKey;

/**
 * Utility class to apply user-defined exclusions and inclusions to all available rules.
 */
@Immutable
public class RuleFilter {

  private final CombinedActiveRules combinedActiveRules;
  private final Set<RuleKey> excludedRuleKeys;
  private final Set<RuleKey> includedRuleKeys;

  public RuleFilter(CombinedActiveRules combinedActiveRules,
    Collection<org.sonarsource.sonarlint.core.client.api.common.RuleKey> excludedRuleKeys,
    Collection<org.sonarsource.sonarlint.core.client.api.common.RuleKey> includedRuleKeys) {
    this.combinedActiveRules = combinedActiveRules;
    this.excludedRuleKeys = convert(excludedRuleKeys);
    this.includedRuleKeys = convert(includedRuleKeys);
  }

  /**
   * The conversion is necessary, because the client is not coupled to SonarQube API.
   */
  private static Set<RuleKey> convert(Collection<org.sonarsource.sonarlint.core.client.api.common.RuleKey> includedRuleKeys) {
    return includedRuleKeys.stream().map(r -> RuleKey.of(r.repository(), r.rule())).collect(Collectors.toSet());
  }

  boolean isActive(RuleKey ruleKey) {
    return combinedActiveRules.exists(ruleKey)
      && !excludedRuleKeys.contains(ruleKey)
      && (combinedActiveRules.isActiveByDefault(ruleKey)
        || includedRuleKeys.contains(ruleKey));
  }
}
