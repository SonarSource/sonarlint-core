/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.rules;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.common.ImpactPayload;

public class ServerActiveRule {
  private final String ruleKey;
  private final IssueSeverity severity;
  private final Map<String, String> params;
  private final String templateKey;
  private final List<ImpactPayload> overriddenImpacts;

  public ServerActiveRule(String ruleKey, IssueSeverity severity, Map<String, String> params, @Nullable String templateKey, List<ImpactPayload> overriddenImpacts) {
    this.ruleKey = ruleKey;
    this.severity = severity;
    this.params = params;
    this.templateKey = templateKey;
    this.overriddenImpacts = overriddenImpacts;
  }

  public List<ImpactPayload> getOverriddenImpacts() {
    return overriddenImpacts;
  }

  public IssueSeverity getSeverity() {
    return severity;
  }

  public Map<String, String> getParams() {
    return params;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  public String getTemplateKey() {
    return templateKey;
  }
}
