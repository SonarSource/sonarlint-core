/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.rules;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

  public static final String DEFAULT_SECTION = "default";

  public static ActiveRuleDetails from(SonarLintRuleDefinition ruleDefinition) {
    return new ActiveRuleDetails(
      ruleDefinition.getKey(),
      ruleDefinition.getLanguage(),
      ruleDefinition.getName(),
      ruleDefinition.getHtmlDescription(),
      ruleDefinition.getDescriptionSections().stream()
        .map(s -> new DescriptionSection(s.getKey(), s.getHtmlContent(), s.getContext().map(c -> new DescriptionSection.Context(c.getKey(), c.getDisplayName()))))
        .collect(Collectors.groupingBy(DescriptionSection::getKey)),
      ruleDefinition.getDefaultSeverity(),
      ruleDefinition.getType(),
      null,
      ruleDefinition.getParams().values().stream().map(p -> new ActiveRuleParam(p.name(), p.description(), p.defaultValue())).collect(Collectors.toList()),
      ruleDefinition.getEducationPrincipleKeys());
  }

  public static ActiveRuleDetails merging(ServerActiveRule activeRuleFromStorage, ServerRule serverRule) {
    return new ActiveRuleDetails(activeRuleFromStorage.getRuleKey(), serverRule.getLanguage(), serverRule.getName(), serverRule.getHtmlDesc(),
      serverRule.getDescriptionSections().stream()
        .map(s -> new DescriptionSection(s.getKey(), s.getHtmlContent(), s.getContext().map(c -> new DescriptionSection.Context(c.getKey(), c.getDisplayName()))))
        .collect(Collectors.groupingBy(DescriptionSection::getKey)),
      Optional.ofNullable(activeRuleFromStorage.getSeverity()).orElse(serverRule.getSeverity()),
      serverRule.getType(), serverRule.getHtmlNote(), Collections.emptyList(),
      serverRule.getEducationPrincipleKeys());
  }

  public static ActiveRuleDetails merging(ServerRule activeRuleFromServer, SonarLintRuleDefinition ruleDefFromPlugin) {
    return new ActiveRuleDetails(ruleDefFromPlugin.getKey(), ruleDefFromPlugin.getLanguage(), ruleDefFromPlugin.getName(), ruleDefFromPlugin.getHtmlDescription(),
      ruleDefFromPlugin.getDescriptionSections().stream()
        .map(s -> new DescriptionSection(s.getKey(), s.getHtmlContent(), s.getContext().map(c -> new DescriptionSection.Context(c.getKey(), c.getDisplayName()))))
        .collect(Collectors.groupingBy(DescriptionSection::getKey)),
      Optional.ofNullable(activeRuleFromServer.getSeverity()).orElse(ruleDefFromPlugin.getDefaultSeverity()), ruleDefFromPlugin.getType(),
      activeRuleFromServer.getHtmlNote(), Collections.emptyList(), ruleDefFromPlugin.getEducationPrincipleKeys());
  }

  public static ActiveRuleDetails merging(ServerActiveRule activeRuleFromStorage, ServerRule serverRule, SonarLintRuleDefinition templateRuleDefFromPlugin) {
    return new ActiveRuleDetails(
      activeRuleFromStorage.getRuleKey(),
      templateRuleDefFromPlugin.getLanguage(),
      serverRule.getName(),
      serverRule.getHtmlDesc(),
      serverRule.getDescriptionSections().stream()
        .map(s -> new DescriptionSection(s.getKey(), s.getHtmlContent(), s.getContext().map(c -> new DescriptionSection.Context(c.getKey(), c.getDisplayName()))))
        .collect(Collectors.groupingBy(DescriptionSection::getKey)),
      serverRule.getSeverity(),
      templateRuleDefFromPlugin.getType(),
      serverRule.getHtmlNote(),
      Collections.emptyList(), templateRuleDefFromPlugin.getEducationPrincipleKeys());
  }

  private final String key;
  private final Language language;
  private final String name;
  private final String htmlDescription;
  private final Map<String, List<DescriptionSection>> descriptionSectionsByKey;
  private final IssueSeverity defaultSeverity;
  private final RuleType type;
  private final Collection<ActiveRuleParam> params;
  private final String extendedDescription;
  private final Set<String> educationPrincipleKeys;

  public ActiveRuleDetails(String key, Language language, String name, String htmlDescription, Map<String, List<DescriptionSection>> descriptionSectionsByKey,
    IssueSeverity defaultSeverity, RuleType type, @Nullable String extendedDescription, Collection<ActiveRuleParam> params, Set<String> educationPrincipleKeys) {
    this.key = key;
    this.language = language;
    this.name = name;
    this.htmlDescription = htmlDescription;
    this.descriptionSectionsByKey = descriptionSectionsByKey;
    this.defaultSeverity = defaultSeverity;
    this.type = type;
    this.params = params;
    this.extendedDescription = extendedDescription;
    this.educationPrincipleKeys = educationPrincipleKeys;
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

  public boolean hasMonolithicDescription() {
    return descriptionSectionsByKey.isEmpty() || hasOnlyDefaultSection();
  }

  private boolean hasOnlyDefaultSection() {
    return descriptionSectionsByKey.size() == 1 && descriptionSectionsByKey.containsKey(DEFAULT_SECTION);
  }

  public Map<String, List<DescriptionSection>> getDescriptionSectionsByKey() {
    return descriptionSectionsByKey;
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

  public Set<String> getCleanCodePrincipleKeys() {
    return educationPrincipleKeys;
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

  public static class DescriptionSection {
    private final String key;
    private final String htmlContent;
    private final Optional<Context> context;

    public DescriptionSection(String key, String htmlContent, Optional<Context> context) {
      this.key = key;
      this.htmlContent = htmlContent;
      this.context = context;
    }

    public String getKey() {
      return key;
    }

    public String getHtmlContent() {
      return htmlContent;
    }

    public Optional<Context> getContext() {
      return context;
    }

    public static class Context {
      private final String key;
      private final String displayName;

      public Context(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
      }

      public String getKey() {
        return key;
      }

      public String getDisplayName() {
        return displayName;
      }
    }

  }
}
