/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis;

import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class ActiveRuleDto {

  private final String ruleKey;
  private final String languageKey;
  private final Map<String, String> params;
  private final String templateRuleKey;

  public ActiveRuleDto(String ruleKey, String languageKey, Map<String, String> params, @Nullable String templateRuleKey) {
    this.ruleKey = ruleKey;
    this.languageKey = languageKey;
    this.params = params;
    this.templateRuleKey = templateRuleKey;
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

  @CheckForNull
  public String getTemplateRuleKey() {
    return templateRuleKey;
  }
}
