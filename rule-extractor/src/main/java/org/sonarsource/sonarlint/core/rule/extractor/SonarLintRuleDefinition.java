/*
 * SonarLint Core - Rule Extractor
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
package org.sonarsource.sonarlint.core.rule.extractor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Param;
import org.sonar.markdown.Markdown;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;

import static java.util.stream.Collectors.toSet;
import static org.sonarsource.sonarlint.core.rule.extractor.SecurityStandards.fromSecurityStandards;

public class SonarLintRuleDefinition {

  private final String key;
  private final String name;
  private final IssueSeverity defaultSeverity;
  private final RuleType type;
  private final String description;
  private final List<SonarLintRuleDescriptionSection> descriptionSections;
  private final Map<String, SonarLintRuleParamDefinition> params;
  private final Map<String, String> defaultParams = new HashMap<>();
  private final boolean isActiveByDefault;
  private final Language language;
  private final String[] tags;
  private final Set<String> deprecatedKeys;
  private final Set<String> educationPrincipleKeys;
  private final Optional<String> internalKey;
  // Relevant for Hotspot rules only
  private final Optional<VulnerabilityProbability> vulnerabilityProbability;

  public SonarLintRuleDefinition(RulesDefinition.Rule rule) {
    this.key = RuleKey.of(rule.repository().key(), rule.key()).toString();
    this.name = rule.name();
    this.defaultSeverity = IssueSeverity.valueOf(rule.severity());
    this.type = RuleType.valueOf(rule.type().name());
    var htmlDescription = rule.htmlDescription() != null ? rule.htmlDescription() : Markdown.convertToHtml(rule.markdownDescription());
    if (rule.type() == org.sonar.api.rules.RuleType.SECURITY_HOTSPOT) {
      this.description = null;
      this.descriptionSections = LegacyHotspotRuleDescriptionSectionsGenerator.extractDescriptionSectionsFromHtml(htmlDescription);
    } else {
      this.description = htmlDescription;
      this.descriptionSections = rule.ruleDescriptionSections().stream().map(s -> new SonarLintRuleDescriptionSection(s.getKey(), s.getHtmlContent(),
        s.getContext().map(c -> new SonarLintRuleDescriptionSection.Context(c.getKey(), c.getDisplayName())))).collect(Collectors.toList());
    }

    this.isActiveByDefault = rule.activatedByDefault();
    this.language = Language.forKey(rule.repository().language()).orElseThrow(() -> new IllegalStateException("Unknown language with key: " + rule.repository().language()));
    this.tags = rule.tags().toArray(new String[0]);
    this.deprecatedKeys = rule.deprecatedRuleKeys().stream().map(RuleKey::toString).collect(toSet());
    this.educationPrincipleKeys = rule.educationPrincipleKeys();
    this.vulnerabilityProbability =
      rule.type() == org.sonar.api.rules.RuleType.SECURITY_HOTSPOT ?
        Optional.of(fromSecurityStandards(rule.securityStandards()).getSlCategory().getVulnerability()) : Optional.empty();
    Map<String, SonarLintRuleParamDefinition> builder = new HashMap<>();
    for (Param param : rule.params()) {
      var paramDefinition = new SonarLintRuleParamDefinition(param);
      builder.put(param.key(), paramDefinition);
      var defaultValue = paramDefinition.defaultValue();
      if (defaultValue != null) {
        defaultParams.put(param.key(), defaultValue);
      }
    }
    params = Collections.unmodifiableMap(builder);
    this.internalKey = Optional.ofNullable(rule.internalKey());
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public IssueSeverity getDefaultSeverity() {
    return defaultSeverity;
  }

  public RuleType getType() {
    return type;
  }

  public Map<String, SonarLintRuleParamDefinition> getParams() {
    return params;
  }

  public Map<String, String> getDefaultParams() {
    return defaultParams;
  }

  public boolean isActiveByDefault() {
    return isActiveByDefault;
  }

  public String getHtmlDescription() {
    return description;
  }

  public List<SonarLintRuleDescriptionSection> getDescriptionSections() {
    return descriptionSections;
  }

  public Language getLanguage() {
    return language;
  }

  public String[] getTags() {
    return tags;
  }

  public Set<String> getDeprecatedKeys() {
    return deprecatedKeys;
  }

  public Set<String> getEducationPrincipleKeys() {
    return educationPrincipleKeys;
  }

  public Optional<String> getInternalKey() {
    return internalKey;
  }

  public Optional<VulnerabilityProbability> getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }
}
