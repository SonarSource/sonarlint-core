/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.push;

import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.push.ServerEvent;

public class RuleSetChangedEvent implements ServerEvent {
  private final List<String> projectKeys;
  private final List<ActiveRule> activatedRules;
  private final List<String> deactivatedRules;

  public RuleSetChangedEvent(List<String> projectKeys, List<ActiveRule> activatedRules, List<String> deactivatedRules) {
    this.projectKeys = projectKeys;
    this.activatedRules = activatedRules;
    this.deactivatedRules = deactivatedRules;
  }

  public List<String> getProjectKeys() {
    return projectKeys;
  }

  public List<ActiveRule> getActivatedRules() {
    return activatedRules;
  }

  public List<String> getDeactivatedRules() {
    return deactivatedRules;
  }

  public static class ActiveRule {
    private final String key;
    private final String languageKey;
    private final IssueSeverity severity;
    private final Map<String, String> parameters;
    private final String templateKey;

    public ActiveRule(String key, String languageKey, IssueSeverity severity, Map<String, String> parameters, @Nullable String templateKey) {
      this.key = key;
      this.languageKey = languageKey;
      this.severity = severity;
      this.parameters = parameters;
      this.templateKey = templateKey;
    }

    public String getKey() {
      return key;
    }

    public String getLanguageKey() {
      return languageKey;
    }

    public IssueSeverity getSeverity() {
      return severity;
    }

    public Map<String, String> getParameters() {
      return parameters;
    }

    @CheckForNull
    public String getTemplateKey() {
      return templateKey;
    }
  }
}
