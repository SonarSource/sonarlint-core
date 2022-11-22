/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.rules;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerRule;

public class ActiveRuleDetails {

  public static ActiveRuleDetails from(SonarLintRuleDefinition ruleDefinition) {
    return new ActiveRuleDetails(
      ruleDefinition.getKey(),
      ruleDefinition.getLanguage(),
      ruleDefinition.getName(),
      ruleDefinition.getHtmlDescription(),
      ruleDefinition.getDefaultSeverity(),
      ruleDefinition.getType(),
      null,
      ruleDefinition.getParams().values().stream().map(p -> new ActiveRuleParam(p.name(), p.description(), p.defaultValue())).collect(Collectors.toList()));
  }

  public static ActiveRuleDetails merging(ServerActiveRule activeRuleFromStorage, ServerRule serverRule) {
    return new ActiveRuleDetails(activeRuleFromStorage.getRuleKey(), serverRule.getLanguage(), serverRule.getName(), serverRule.getHtmlDesc(),
      Optional.ofNullable(activeRuleFromStorage.getSeverity()).orElse(serverRule.getSeverity()),
      serverRule.getType(), serverRule.getHtmlNote(), Collections.emptyList());
  }

  public static ActiveRuleDetails merging(ServerRule activeRuleFromServer, SonarLintRuleDefinition ruleDefFromPlugin) {
    return new ActiveRuleDetails(ruleDefFromPlugin.getKey(), ruleDefFromPlugin.getLanguage(), ruleDefFromPlugin.getName(), ruleDefFromPlugin.getHtmlDescription(),
      Optional.ofNullable(activeRuleFromServer.getSeverity()).orElse(ruleDefFromPlugin.getDefaultSeverity()), ruleDefFromPlugin.getType(),
      activeRuleFromServer.getHtmlNote(), Collections.emptyList());
  }

  public static ActiveRuleDetails merging(ServerActiveRule activeRuleFromStorage, ServerRule serverRule, SonarLintRuleDefinition templateRuleDefFromPlugin) {
    return new ActiveRuleDetails(
      activeRuleFromStorage.getRuleKey(),
      templateRuleDefFromPlugin.getLanguage(),
      serverRule.getName(),
      serverRule.getHtmlDesc(),
      serverRule.getSeverity(),
      templateRuleDefFromPlugin.getType(),
      serverRule.getHtmlNote(),
      Collections.emptyList());
  }

  private final String key;
  private final Language language;
  private final String name;
  private final String htmlDescription;
  private final IssueSeverity defaultSeverity;
  private final RuleType type;
  private final Collection<ActiveRuleParam> params;
  private final String extendedDescription;

  public ActiveRuleDetails(String key, Language language, String name, String htmlDescription, IssueSeverity defaultSeverity, RuleType type, @Nullable String extendedDescription,
    Collection<ActiveRuleParam> params) {
    this.key = key;
    this.language = language;
    this.name = name;
    this.htmlDescription = htmlDescription;
    this.defaultSeverity = defaultSeverity;
    this.type = type;
    this.params = params;
    this.extendedDescription = extendedDescription;
  }

  public String getKey() {
    return key;
  }

  public Language getLanguage() {
    return language;
  }

  public String getName() {
    return name;
  }

  public String getHtmlDescription() {
    return htmlDescription;
  }

  public IssueSeverity getDefaultSeverity() {
    return defaultSeverity;
  }

  public RuleType getType() {
    return type;
  }

  public Collection<ActiveRuleParam> getParams() {
    return params;
  }

  @CheckForNull
  public String getExtendedDescription() {
    return extendedDescription;
  }

  public static class ActiveRuleParam {
    private final String name;
    private final String description;
    private final String defaultValue;

    public ActiveRuleParam(String name, String description, @Nullable String defaultValue) {
      this.name = name;
      this.description = description;
      this.defaultValue = defaultValue;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    @CheckForNull
    public String getDefaultValue() {
      return defaultValue;
    }
  }
}
