/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.rules;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

public class GetEffectiveRuleDetailsParams {

  private final String configurationScopeId;
  private final String ruleKey;

  @Nullable
  private final String contextKey;

  /**
   * @param configurationScopeId the configuration scope id (see spec/glossary.adoc for more details)
   * @param ruleKey the key of the rule
   * @deprecated use {@link GetEffectiveRuleDetailsParams}
   */
  @Deprecated(since = "8.13", forRemoval = true)
  public GetEffectiveRuleDetailsParams(String configurationScopeId, String ruleKey) {
    this(configurationScopeId, ruleKey, null);
  }

  public GetEffectiveRuleDetailsParams(String configurationScopeId, String ruleKey, @Nullable String contextKey) {
    this.configurationScopeId = requireNonNull(configurationScopeId);
    this.ruleKey = requireNonNull(ruleKey);
    this.contextKey = contextKey;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  @Nullable
  public String getContextKey() {
    return contextKey;
  }
}
