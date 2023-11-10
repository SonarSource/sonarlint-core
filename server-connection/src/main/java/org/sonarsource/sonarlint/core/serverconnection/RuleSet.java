/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;

public class RuleSet {
  private final Collection<ServerActiveRule> rules;
  private final Map<String, ServerActiveRule> rulesByKey;
  private final String lastModified;

  public RuleSet(Collection<ServerActiveRule> rules, String lastModified) {
    this.rules = rules;
    this.rulesByKey = rules.stream().collect(Collectors.toMap(ServerActiveRule::getRuleKey, Function.identity()));
    this.lastModified = lastModified;
  }

  public Collection<ServerActiveRule> getRules() {
    return rules;
  }

  public Map<String, ServerActiveRule> getRulesByKey() {
    return rulesByKey;
  }

  public String getLastModified() {
    return lastModified;
  }
}
