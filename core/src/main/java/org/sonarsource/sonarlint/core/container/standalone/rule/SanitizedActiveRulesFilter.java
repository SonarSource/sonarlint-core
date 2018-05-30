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
import org.sonar.api.rule.RuleKey;

/**
 * Sanitized exclusion and inclusion filters:
 *
 * - When a key is both excluded and included -> exclude it
 * - When a key is excluded even though it's excluded by default -> drop the redundant exclusion
 * - When a key is included even though it's included by default -> drop the redundant inclusion
 */
public class SanitizedActiveRulesFilter {
  private final Set<RuleKey> included;
  private final Set<RuleKey> excluded;

  public SanitizedActiveRulesFilter(StandaloneActiveRules standaloneActiveRules,
    Collection<org.sonarsource.sonarlint.core.client.api.common.RuleKey> excludedRuleKeys,
    Collection<org.sonarsource.sonarlint.core.client.api.common.RuleKey> includedRuleKeys) {

    this.included = includedRuleKeys.stream()
      .filter(rk -> !excludedRuleKeys.contains(rk))
      .map(SanitizedActiveRulesFilter::convert)
      .filter(rk -> !standaloneActiveRules.isActiveByDefault(rk))
      .collect(Collectors.toSet());

    this.excluded = excludedRuleKeys.stream()
      .map(SanitizedActiveRulesFilter::convert)
      .filter(standaloneActiveRules::isActiveByDefault)
      .collect(Collectors.toSet());
  }

  /**
   * The conversion is necessary, because the client is not coupled to SonarQube API.
   */
  private static RuleKey convert(org.sonarsource.sonarlint.core.client.api.common.RuleKey ruleKey) {
    return RuleKey.of(ruleKey.repository(), ruleKey.rule());
  }

  public Collection<RuleKey> excluded() {
    return excluded;
  }

  public Collection<RuleKey> included() {
    return included;
  }
}
