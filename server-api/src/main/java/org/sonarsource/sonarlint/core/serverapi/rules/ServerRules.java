/*
 * SonarLint Server API
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
package org.sonarsource.sonarlint.core.serverapi.rules;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public class ServerRules {
  private final List<Rule> allRules;
  private final Map<String, List<ActiveRule>> activeRulesByQualityProfileKey;

  public ServerRules(List<Rule> allRules, Map<String, List<ActiveRule>> activeRulesByQualityProfileKey) {
    this.allRules = allRules;
    this.activeRulesByQualityProfileKey = activeRulesByQualityProfileKey;
  }

  public List<Rule> getAll() {
    return allRules;
  }

  public Map<String, List<ActiveRule>> getActiveRulesByQualityProfileKey() {
    return activeRulesByQualityProfileKey;
  }

  public static class Rule {

    private final String ruleKey;
    private final String name;
    private final String severity;
    private final String lang;
    private final String internalKey;
    private final String htmlDesc;
    private final String htmlNote;
    private final boolean isTemplate;
    private final String templateKey;
    private final String type;

    public Rule(String ruleKey, String name, String severity, String lang, String internalKey, String htmlDesc, String htmlNote, boolean isTemplate,
      String templateKey, @Nullable String type) {
      this.ruleKey = ruleKey;
      this.name = name;
      this.severity = severity;
      this.lang = lang;
      this.internalKey = internalKey;
      this.htmlDesc = htmlDesc;
      this.htmlNote = htmlNote;
      this.isTemplate = isTemplate;
      this.templateKey = templateKey;
      this.type = type == null ? "" : type;
    }

    public String getRuleKey() {
      return ruleKey;
    }

    public String getName() {
      return name;
    }

    public String getSeverity() {
      return severity;
    }

    public String getLang() {
      return lang;
    }

    public String getInternalKey() {
      return internalKey;
    }

    public String getHtmlDesc() {
      return htmlDesc;
    }

    public String getHtmlNote() {
      return htmlNote;
    }

    public boolean isTemplate() {
      return isTemplate;
    }

    public String getTemplateKey() {
      return templateKey;
    }

    public String getType() {
      return type;
    }

  }
  public static class ActiveRule {
    private final String ruleKey;
    private final String severity;
    private final List<Param> params;

    public ActiveRule(String ruleKey, String severity, List<Param> params) {
      this.ruleKey = ruleKey;
      this.severity = severity;
      this.params = params;
    }

    public String getSeverity() {
      return severity;
    }

    public List<Param> getParams() {
      return params;
    }

    public String getRuleKey() {
      return ruleKey;
    }

    public static class Param {

      private final String key;
      private final String value;

      public Param(String key, String value) {
        this.key = key;
        this.value = value;
      }

      public String getKey() {
        return key;
      }

      public String getValue() {
        return value;
      }
    }
  }
}
