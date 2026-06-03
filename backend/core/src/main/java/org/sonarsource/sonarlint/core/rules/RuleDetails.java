/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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

  private RuleDetails(Builder builder) {
    this.key = builder.key;
    this.language = builder.language;
    this.name = builder.name;
    this.htmlDescription = builder.htmlDescription;
    this.descriptionSectionsByKey = builder.descriptionSectionsByKey;
    this.defaultSeverity = builder.defaultSeverity;
    this.type = builder.type;
    this.cleanCodeAttribute = builder.cleanCodeAttribute;
    this.impacts = builder.impacts;
    this.params = builder.params;
    this.extendedDescription = builder.extendedDescription;
    this.educationPrincipleKeys = builder.educationPrincipleKeys;
    this.vulnerabilityProbability = builder.vulnerabilityProbability;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static RuleDetails from(SonarLintRuleDefinition ruleDefinition, @Nullable StandaloneRuleConfigDto ruleConfig) {
    return builder()
      .key(ruleDefinition.getKey())
      .language(ruleDefinition.getLanguage())
      .name(ruleDefinition.getName())
      .htmlDescription(ruleDefinition.getHtmlDescription())
      .descriptionSectionsByKey(ruleDefinition.getDescriptionSections().stream()
        .map(s -> new DescriptionSection(s.getKey(), s.getHtmlContent(), s.getContext().map(c -> new DescriptionSection.Context(c.getKey(), c.getDisplayName()))))
        .collect(Collectors.groupingBy(DescriptionSection::getKey)))
      .impacts(ruleDefinition.getDefaultImpacts())
      .defaultSeverity(ruleDefinition.getDefaultSeverity())
      .type(ruleDefinition.getType())
      .cleanCodeAttribute(ruleDefinition.getCleanCodeAttribute().orElse(CleanCodeAttribute.defaultCleanCodeAttribute()))
      .extendedDescription(null)
      .params(transformParams(ruleDefinition.getParams(), ruleConfig != null ? ruleConfig.getParamValueByKey() : Map.of()))
      .educationPrincipleKeys(ruleDefinition.getEducationPrincipleKeys())
      .vulnerabilityProbability(ruleDefinition.getVulnerabilityProbability().orElse(null))
      .build();
  }

  @NotNull
  private static List<EffectiveRuleParam> transformParams(Map<String, SonarLintRuleParamDefinition> ruleDefinitionParams, Map<String, String> ruleConfigParams) {
    return ruleDefinitionParams.values()
      .stream()
      .map(p -> new EffectiveRuleParam(p.name(), p.description(), ruleConfigParams.getOrDefault(p.key(), p.defaultValue()), p.defaultValue()))
      .toList();
  }

  public static RuleDetails merging(ServerActiveRule activeRuleFromStorage, ServerRule serverRule) {
    return builder()
      .key(activeRuleFromStorage.getRuleKey())
      .language(serverRule.getLanguage())
      .name(serverRule.getName())
      .htmlDescription(serverRule.getHtmlDesc())
      .descriptionSectionsByKey(serverRule.getDescriptionSections().stream()
        .map(s -> new DescriptionSection(s.getKey(), s.getHtmlContent(), s.getContext().map(c -> new DescriptionSection.Context(c.getKey(), c.getDisplayName()))))
        .collect(Collectors.groupingBy(DescriptionSection::getKey)))
      .impacts(serverRule.getImpacts())
      .defaultSeverity(Optional.ofNullable(activeRuleFromStorage.getSeverity()).orElse(serverRule.getSeverity()))
      .type(serverRule.getType())
      .cleanCodeAttribute(serverRule.getCleanCodeAttribute())
      .extendedDescription(serverRule.getHtmlNote())
      .params(Collections.emptyList())
      .educationPrincipleKeys(serverRule.getEducationPrincipleKeys())
      // TODO get vulnerability probability from storage?
      .vulnerabilityProbability(null)
      .build();
  }

  public static RuleDetails merging(ServerRule activeRuleFromServer, SonarLintRuleDefinition ruleDefFromPlugin, boolean skipCleanCodeTaxonomy) {
    var cleanCodeAttribute = skipCleanCodeTaxonomy ? null : ruleDefFromPlugin.getCleanCodeAttribute().orElse(CleanCodeAttribute.defaultCleanCodeAttribute());
    var defaultImpacts = skipCleanCodeTaxonomy ? Map.<SoftwareQuality, ImpactSeverity>of() : ruleDefFromPlugin.getDefaultImpacts();
    return builder()
      .key(ruleDefFromPlugin.getKey())
      .language(ruleDefFromPlugin.getLanguage())
      .name(ruleDefFromPlugin.getName())
      .htmlDescription(ruleDefFromPlugin.getHtmlDescription())
      .descriptionSectionsByKey(ruleDefFromPlugin.getDescriptionSections().stream()
        .map(s -> new DescriptionSection(s.getKey(), s.getHtmlContent(), s.getContext().map(c -> new DescriptionSection.Context(c.getKey(), c.getDisplayName()))))
        .collect(Collectors.groupingBy(DescriptionSection::getKey)))
      .impacts(defaultImpacts)
      .defaultSeverity(Optional.ofNullable(activeRuleFromServer.getSeverity()).orElse(ruleDefFromPlugin.getDefaultSeverity()))
      .type(ruleDefFromPlugin.getType())
      .cleanCodeAttribute(cleanCodeAttribute)
      .extendedDescription(activeRuleFromServer.getHtmlNote())
      .params(Collections.emptyList())
      .educationPrincipleKeys(ruleDefFromPlugin.getEducationPrincipleKeys())
      .vulnerabilityProbability(ruleDefFromPlugin.getVulnerabilityProbability().orElse(null))
      .build();
  }

  public static RuleDetails merging(ServerActiveRule activeRuleFromStorage, ServerRule serverRule, SonarLintRuleDefinition templateRuleDefFromPlugin,
    boolean skipCleanCodeTaxonomy) {
    var cleanCodeAttribute = skipCleanCodeTaxonomy ? null : templateRuleDefFromPlugin.getCleanCodeAttribute().orElse(CleanCodeAttribute.defaultCleanCodeAttribute());
    var defaultImpacts = skipCleanCodeTaxonomy ? Map.<SoftwareQuality, ImpactSeverity>of() : templateRuleDefFromPlugin.getDefaultImpacts();
    return builder()
      .key(activeRuleFromStorage.getRuleKey())
      .language(templateRuleDefFromPlugin.getLanguage())
      .name(serverRule.getName())
      .htmlDescription(serverRule.getHtmlDesc())
      .descriptionSectionsByKey(serverRule.getDescriptionSections().stream()
        .map(s -> new DescriptionSection(s.getKey(), s.getHtmlContent(), s.getContext().map(c -> new DescriptionSection.Context(c.getKey(), c.getDisplayName()))))
        .collect(Collectors.groupingBy(DescriptionSection::getKey)))
      .impacts(mergeImpacts(defaultImpacts, activeRuleFromStorage.getOverriddenImpacts()))
      .defaultSeverity(serverRule.getSeverity())
      .type(serverRule.getType())
      .cleanCodeAttribute(cleanCodeAttribute)
      .extendedDescription(serverRule.getHtmlNote())
      .params(Collections.emptyList())
      .educationPrincipleKeys(templateRuleDefFromPlugin.getEducationPrincipleKeys())
      .vulnerabilityProbability(templateRuleDefFromPlugin.getVulnerabilityProbability().orElse(null))
      .build();
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
    return builder()
      .key(serverActiveRuleDetails.getKey())
      .language(serverActiveRuleDetails.getLanguage())
      .name(serverActiveRuleDetails.getName())
      .htmlDescription(serverActiveRuleDetails.getHtmlDescription())
      .descriptionSectionsByKey(serverActiveRuleDetails.getDescriptionSectionsByKey())
      .impacts(softwareImpacts)
      .defaultSeverity(isMQRMode ? null : IssueSeverity.valueOf(raisedFindingDto.getSeverityMode().getLeft().getSeverity().toString()))
      .type(isMQRMode ? null : RuleType.valueOf(raisedFindingDto.getSeverityMode().getLeft().getType().toString()))
      .cleanCodeAttribute(isMQRMode ? CleanCodeAttribute.valueOf(raisedFindingDto.getSeverityMode().getRight().getCleanCodeAttribute().name()) : null)
      .extendedDescription(serverActiveRuleDetails.getExtendedDescription())
      .params(serverActiveRuleDetails.getParams())
      .educationPrincipleKeys(serverActiveRuleDetails.educationPrincipleKeys)
      .vulnerabilityProbability(serverActiveRuleDetails.getVulnerabilityProbability())
      .build();
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
    return builder()
      .key(serverActiveRuleDetails.getKey())
      .language(serverActiveRuleDetails.getLanguage())
      .name(serverActiveRuleDetails.getName())
      .htmlDescription(serverActiveRuleDetails.getHtmlDescription())
      .descriptionSectionsByKey(serverActiveRuleDetails.getDescriptionSectionsByKey())
      .impacts(softwareImpacts)
      .defaultSeverity(isMQRMode ? null : IssueSeverity.valueOf(taintVulnerabilityDto.getSeverityMode().getLeft().getSeverity().toString()))
      .type(isMQRMode ? null : RuleType.valueOf(taintVulnerabilityDto.getSeverityMode().getLeft().getType().toString()))
      .cleanCodeAttribute(isMQRMode ? CleanCodeAttribute.valueOf(taintVulnerabilityDto.getSeverityMode().getRight().getCleanCodeAttribute().name()) : null)
      .extendedDescription(serverActiveRuleDetails.getExtendedDescription())
      .params(serverActiveRuleDetails.getParams())
      .educationPrincipleKeys(serverActiveRuleDetails.educationPrincipleKeys)
      .vulnerabilityProbability(serverActiveRuleDetails.getVulnerabilityProbability())
      .build();
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

  public static class Builder {
    private String key;
    private SonarLanguage language;
    private String name;
    private String htmlDescription;
    private Map<String, List<DescriptionSection>> descriptionSectionsByKey;
    private IssueSeverity defaultSeverity;
    private RuleType type;
    private CleanCodeAttribute cleanCodeAttribute;
    private Map<SoftwareQuality, ImpactSeverity> impacts;
    private Collection<EffectiveRuleParam> params;
    private String extendedDescription;
    private Set<String> educationPrincipleKeys;
    private VulnerabilityProbability vulnerabilityProbability;

    private Builder() {
    }

    public Builder key(String key) {
      this.key = key;
      return this;
    }

    public Builder language(SonarLanguage language) {
      this.language = language;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder htmlDescription(String htmlDescription) {
      this.htmlDescription = htmlDescription;
      return this;
    }

    public Builder descriptionSectionsByKey(Map<String, List<DescriptionSection>> descriptionSectionsByKey) {
      this.descriptionSectionsByKey = descriptionSectionsByKey;
      return this;
    }

    public Builder defaultSeverity(@Nullable IssueSeverity defaultSeverity) {
      this.defaultSeverity = defaultSeverity;
      return this;
    }

    public Builder type(@Nullable RuleType type) {
      this.type = type;
      return this;
    }

    public Builder cleanCodeAttribute(@Nullable CleanCodeAttribute cleanCodeAttribute) {
      this.cleanCodeAttribute = cleanCodeAttribute;
      return this;
    }

    public Builder impacts(Map<SoftwareQuality, ImpactSeverity> impacts) {
      this.impacts = impacts;
      return this;
    }

    public Builder params(Collection<EffectiveRuleParam> params) {
      this.params = params;
      return this;
    }

    public Builder extendedDescription(@Nullable String extendedDescription) {
      this.extendedDescription = extendedDescription;
      return this;
    }

    public Builder educationPrincipleKeys(Set<String> educationPrincipleKeys) {
      this.educationPrincipleKeys = educationPrincipleKeys;
      return this;
    }

    public Builder vulnerabilityProbability(@Nullable VulnerabilityProbability vulnerabilityProbability) {
      this.vulnerabilityProbability = vulnerabilityProbability;
      return this;
    }

    public RuleDetails build() {
      return new RuleDetails(this);
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
