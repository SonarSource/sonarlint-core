/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.rules;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleParamDefinition;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.common.ImpactPayload;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerRule;

public class RuleDetails {

  public static final String DEFAULT_SECTION = "default";

  private final String key;
  private final SonarLanguage language;
  private final String name;
  private final String htmlDescription;
  private final Map<String, List<DescriptionSection>> descriptionSectionsByKey;
  private final IssueSeverity defaultSeverity;
  private final RuleType type;
  private final CleanCodeAttribute cleanCodeAttribute;
  private final Map<SoftwareQuality, ImpactSeverity> impacts;
  private final Collection<EffectiveRuleParam> params;
  private final String extendedDescription;
  private final Set<String> educationPrincipleKeys;
  private final VulnerabilityProbability vulnerabilityProbability;

  public RuleDetails(String key, SonarLanguage language, String name, String htmlDescription, Map<String, List<DescriptionSection>> descriptionSectionsByKey,
    Map<SoftwareQuality, ImpactSeverity> impacts, @Nullable IssueSeverity defaultSeverity, @Nullable RuleType type, @Nullable CleanCodeAttribute cleanCodeAttribute,
    @Nullable String extendedDescription, Collection<EffectiveRuleParam> params, Set<String> educationPrincipleKeys,
    @Nullable VulnerabilityProbability vulnerabilityProbability) {
    this.key = key;
    this.language = language;
    this.name = name;
    this.htmlDescription = htmlDescription;
    this.descriptionSectionsByKey = descriptionSectionsByKey;
    this.defaultSeverity = defaultSeverity;
    this.type = type;
    this.cleanCodeAttribute = cleanCodeAttribute;
    this.impacts = impacts;
    this.params = params;
    this.extendedDescription = extendedDescription;
    this.educationPrincipleKeys = educationPrincipleKeys;
    this.vulnerabilityProbability = vulnerabilityProbability;
  }

  public static RuleDetails from(SonarLintRuleDefinition ruleDefinition, @Nullable StandaloneRuleConfigDto ruleConfig) {
    return new RuleDetails(
      ruleDefinition.getKey(),
      ruleDefinition.getLanguage(),
      ruleDefinition.getName(),
      ruleDefinition.getHtmlDescription(),
      ruleDefinition.getDescriptionSections().stream()
        .map(s -> new DescriptionSection(s.getKey(), s.getHtmlContent(), s.getContext().map(c -> new DescriptionSection.Context(c.getKey(), c.getDisplayName()))))
        .collect(Collectors.groupingBy(DescriptionSection::getKey)),
      ruleDefinition.getDefaultImpacts(),
      ruleDefinition.getDefaultSeverity(),
      ruleDefinition.getType(),
      ruleDefinition.getCleanCodeAttribute().orElse(CleanCodeAttribute.defaultCleanCodeAttribute()),
      null,
      transformParams(ruleDefinition.getParams(), ruleConfig != null ? ruleConfig.getParamValueByKey() : Map.of()),
      ruleDefinition.getEducationPrincipleKeys(), ruleDefinition.getVulnerabilityProbability().orElse(null));
  }

  @NotNull
  private static List<EffectiveRuleParam> transformParams(Map<String, SonarLintRuleParamDefinition> ruleDefinitionParams, Map<String, String> ruleConfigParams) {
    return ruleDefinitionParams.values()
      .stream()
      .map(p -> new EffectiveRuleParam(p.name(), p.description(), ruleConfigParams.getOrDefault(p.key(), p.defaultValue()), p.defaultValue()))
      .toList();
  }

  public static RuleDetails merging(ServerActiveRule activeRuleFromStorage, ServerRule serverRule) {
    return new RuleDetails(activeRuleFromStorage.getRuleKey(), serverRule.getLanguage(), serverRule.getName(), serverRule.getHtmlDesc(),
      serverRule.getDescriptionSections().stream()
        .map(s -> new DescriptionSection(s.getKey(), s.getHtmlContent(), s.getContext().map(c -> new DescriptionSection.Context(c.getKey(), c.getDisplayName()))))
        .collect(Collectors.groupingBy(DescriptionSection::getKey)),
      serverRule.getImpacts(),
      Optional.ofNullable(activeRuleFromStorage.getSeverity()).orElse(serverRule.getSeverity()),
      serverRule.getType(),
      serverRule.getCleanCodeAttribute(),
      serverRule.getHtmlNote(), Collections.emptyList(),
      serverRule.getEducationPrincipleKeys(),
      null); // TODO get vulnerability probability from storage?
  }

  public static RuleDetails merging(ServerRule activeRuleFromServer, SonarLintRuleDefinition ruleDefFromPlugin, boolean skipCleanCodeTaxonomy) {
    var cleanCodeAttribute = skipCleanCodeTaxonomy ? null : ruleDefFromPlugin.getCleanCodeAttribute().orElse(CleanCodeAttribute.defaultCleanCodeAttribute());
    var defaultImpacts = skipCleanCodeTaxonomy ? Map.<SoftwareQuality, ImpactSeverity>of() : ruleDefFromPlugin.getDefaultImpacts();
    return new RuleDetails(ruleDefFromPlugin.getKey(), ruleDefFromPlugin.getLanguage(), ruleDefFromPlugin.getName(), ruleDefFromPlugin.getHtmlDescription(),
      ruleDefFromPlugin.getDescriptionSections().stream()
        .map(s -> new DescriptionSection(s.getKey(), s.getHtmlContent(), s.getContext().map(c -> new DescriptionSection.Context(c.getKey(), c.getDisplayName()))))
        .collect(Collectors.groupingBy(DescriptionSection::getKey)),
      defaultImpacts,
      Optional.ofNullable(activeRuleFromServer.getSeverity()).orElse(ruleDefFromPlugin.getDefaultSeverity()), ruleDefFromPlugin.getType(),
      cleanCodeAttribute,
      activeRuleFromServer.getHtmlNote(), Collections.emptyList(), ruleDefFromPlugin.getEducationPrincipleKeys(), ruleDefFromPlugin.getVulnerabilityProbability().orElse(null));
  }

  public static RuleDetails merging(ServerActiveRule activeRuleFromStorage, ServerRule serverRule, SonarLintRuleDefinition templateRuleDefFromPlugin,
    boolean skipCleanCodeTaxonomy) {
    var cleanCodeAttribute = skipCleanCodeTaxonomy ? null : templateRuleDefFromPlugin.getCleanCodeAttribute().orElse(CleanCodeAttribute.defaultCleanCodeAttribute());
    var defaultImpacts = skipCleanCodeTaxonomy ? Map.<SoftwareQuality, ImpactSeverity>of() : templateRuleDefFromPlugin.getDefaultImpacts();
    return new RuleDetails(
      activeRuleFromStorage.getRuleKey(),
      templateRuleDefFromPlugin.getLanguage(),
      serverRule.getName(),
      serverRule.getHtmlDesc(),
      serverRule.getDescriptionSections().stream()
        .map(s -> new DescriptionSection(s.getKey(), s.getHtmlContent(), s.getContext().map(c -> new DescriptionSection.Context(c.getKey(), c.getDisplayName()))))
        .collect(Collectors.groupingBy(DescriptionSection::getKey)),
      mergeImpacts(defaultImpacts, activeRuleFromStorage.getOverriddenImpacts()),
      serverRule.getSeverity(),
      serverRule.getType(),
      cleanCodeAttribute,
      serverRule.getHtmlNote(),
      Collections.emptyList(), templateRuleDefFromPlugin.getEducationPrincipleKeys(), templateRuleDefFromPlugin.getVulnerabilityProbability().orElse(null));
  }

  public static Map<SoftwareQuality, ImpactSeverity> mergeImpacts(Map<SoftwareQuality, ImpactSeverity> defaultImpacts,
    List<ImpactPayload> overriddenImpacts) {
    var mergedImpacts = new EnumMap<SoftwareQuality, ImpactSeverity>(SoftwareQuality.class);
    if (!defaultImpacts.isEmpty()) {
      mergedImpacts = new EnumMap<>(defaultImpacts);
    }

    for (var impact : overriddenImpacts) {
      var quality = SoftwareQuality.valueOf(impact.getSoftwareQuality());
      var severity = ImpactSeverity.mapSeverity(impact.getSeverity());
      mergedImpacts.put(quality, severity);
    }

    return Collections.unmodifiableMap(mergedImpacts);
  }

  public static RuleDetails merging(RuleDetails serverActiveRuleDetails, RaisedFindingDto raisedFindingDto) {
    var isMQRMode = raisedFindingDto.getSeverityMode().isRight();
    var softwareImpacts = new EnumMap<SoftwareQuality, ImpactSeverity>(SoftwareQuality.class);
    if (isMQRMode) {
      raisedFindingDto.getSeverityMode().getRight().getImpacts().forEach(
        i -> softwareImpacts.put(SoftwareQuality.valueOf(i.getSoftwareQuality().name()),
          ImpactSeverity.valueOf(i.getImpactSeverity().name()))
      );
    }
    return new RuleDetails(serverActiveRuleDetails.getKey(),
      serverActiveRuleDetails.getLanguage(),
      serverActiveRuleDetails.getName(),
      serverActiveRuleDetails.getHtmlDescription(),
      serverActiveRuleDetails.getDescriptionSectionsByKey(),
      softwareImpacts,
      isMQRMode ? null : IssueSeverity.valueOf(raisedFindingDto.getSeverityMode().getLeft().getSeverity().toString()),
      isMQRMode ? null : RuleType.valueOf(raisedFindingDto.getSeverityMode().getLeft().getType().toString()),
      isMQRMode ? CleanCodeAttribute.valueOf(raisedFindingDto.getSeverityMode().getRight().getCleanCodeAttribute().name()) : null,
      serverActiveRuleDetails.getExtendedDescription(),
      serverActiveRuleDetails.getParams(),
      serverActiveRuleDetails.educationPrincipleKeys,
      serverActiveRuleDetails.getVulnerabilityProbability());
  }

  public static RuleDetails merging(RuleDetails serverActiveRuleDetails, TaintVulnerabilityDto taintVulnerabilityDto) {
    var isMQRMode = taintVulnerabilityDto.getSeverityMode().isRight();
    EnumMap<SoftwareQuality, ImpactSeverity> softwareImpacts = new EnumMap<>(SoftwareQuality.class);
    if (isMQRMode) {
      taintVulnerabilityDto.getSeverityMode().getRight().getImpacts().forEach(
        i -> softwareImpacts.put(SoftwareQuality.valueOf(i.getSoftwareQuality().name()),
          ImpactSeverity.valueOf(i.getImpactSeverity().name()))
      );
    }
    return new RuleDetails(serverActiveRuleDetails.getKey(),
      serverActiveRuleDetails.getLanguage(),
      serverActiveRuleDetails.getName(),
      serverActiveRuleDetails.getHtmlDescription(),
      serverActiveRuleDetails.getDescriptionSectionsByKey(),
      softwareImpacts,
      isMQRMode ? null : IssueSeverity.valueOf(taintVulnerabilityDto.getSeverityMode().getLeft().getSeverity().toString()),
      isMQRMode ? null : RuleType.valueOf(taintVulnerabilityDto.getSeverityMode().getLeft().getType().toString()),
      isMQRMode ? CleanCodeAttribute.valueOf(taintVulnerabilityDto.getSeverityMode().getRight().getCleanCodeAttribute().name()) : null,
      serverActiveRuleDetails.getExtendedDescription(),
      serverActiveRuleDetails.getParams(),
      serverActiveRuleDetails.educationPrincipleKeys,
      serverActiveRuleDetails.getVulnerabilityProbability());
  }

  public String getKey() {
    return key;
  }

  public SonarLanguage getLanguage() {
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

  @CheckForNull
  public IssueSeverity getDefaultSeverity() {
    return defaultSeverity;
  }

  @CheckForNull
  public RuleType getType() {
    return type;
  }

  public Optional<CleanCodeAttribute> getCleanCodeAttribute() {
    return Optional.ofNullable(cleanCodeAttribute);
  }

  public Map<SoftwareQuality, ImpactSeverity> getImpacts() {
    return impacts;
  }

  public Collection<EffectiveRuleParam> getParams() {
    return params;
  }

  public Set<String> getCleanCodePrincipleKeys() {
    return educationPrincipleKeys;
  }

  @CheckForNull
  public String getExtendedDescription() {
    return extendedDescription;
  }

  public VulnerabilityProbability getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }

  public static class EffectiveRuleParam {
    private final String name;
    private final String description;
    @Nullable
    private final String value;
    @Nullable
    private final String defaultValue;

    public EffectiveRuleParam(String name, String description, @Nullable String value, @Nullable String defaultValue) {
      this.name = name;
      this.description = description;
      this.value = value;
      this.defaultValue = defaultValue;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    @CheckForNull
    public String getValue() {
      return value;
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
