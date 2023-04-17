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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.EffectiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.EffectiveRuleParamDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleContextualSectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleContextualSectionWithDefaultContextKeyDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleDescriptionTabDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleMonolithicDescriptionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleNonContextualSectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleSplitDescriptionDto;
import org.sonarsource.sonarlint.core.commons.RuleType;

class RuleDetailsAdapter {
  public static final String INTRODUCTION_SECTION_KEY = "introduction";
  public static final String ROOT_CAUSE_SECTION_KEY = "root_cause";
  public static final String ASSESS_THE_PROBLEM_SECTION_KEY = "assess_the_problem";
  public static final String HOW_TO_FIX_SECTION_KEY = "how_to_fix";
  public static final String RESOURCES_SECTION_KEY = "resources";
  private static final String DEFAULT_CONTEXT_KEY = "others";
  private static final String DEFAULT_CONTEXT_DISPLAY_NAME = "Others";
  private static final List<String> SECTION_KEYS_ORDERED = List.of(ROOT_CAUSE_SECTION_KEY, ASSESS_THE_PROBLEM_SECTION_KEY,
    HOW_TO_FIX_SECTION_KEY, RESOURCES_SECTION_KEY);

  public static EffectiveRuleDetailsDto transform(RuleDetails ruleDetails, @Nullable String contextKey) {
    return new EffectiveRuleDetailsDto(
      ruleDetails.getKey(),
      ruleDetails.getName(),
      ruleDetails.getDefaultSeverity(),
      ruleDetails.getType(),
      transformDescriptions(ruleDetails, contextKey),
      transform(ruleDetails.getParams()),
      ruleDetails.getLanguage());
  }

  static Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> transformDescriptions(RuleDetails ruleDetails, @Nullable String contextKey) {
    if (ruleDetails.hasMonolithicDescription()) {
      return Either.forLeft(transformMonolithicDescription(ruleDetails));
    }
    return Either.forRight(transformSplitDescription(ruleDetails, contextKey));
  }

  private static RuleMonolithicDescriptionDto transformMonolithicDescription(RuleDetails ruleDetails) {
    var htmlSnippets = new ArrayList<String>();
    htmlSnippets.add(ruleDetails.getHtmlDescription());
    htmlSnippets.add(ruleDetails.getExtendedDescription());
    htmlSnippets.add(getCleanCodePrinciplesContent(ruleDetails.getCleanCodePrincipleKeys()));
    return new RuleMonolithicDescriptionDto(concat(htmlSnippets));
  }

  private static String getCleanCodePrinciplesContent(Set<String> cleanCodePrincipleKeys) {
    var principles = cleanCodePrincipleKeys.stream().sorted(Comparator.naturalOrder()).map(CleanCodePrinciples::getContent).collect(Collectors.toList());
    return (principles.stream().anyMatch(StringUtils::isNotBlank) ? "<h3>Clean Code Principles</h3>\n" : "") + concat(principles);
  }

  private static RuleSplitDescriptionDto transformSplitDescription(RuleDetails ruleDetails, @Nullable String contextKey) {
    var sectionsByKey = ruleDetails.getDescriptionSectionsByKey();

    var tabbedSections = new ArrayList<>(transformSectionsButIntroductionToTabs(ruleDetails, contextKey));
    addMoreInfoTabIfNeeded(ruleDetails, tabbedSections);
    return new RuleSplitDescriptionDto(extractIntroductionFromSections(sectionsByKey), tabbedSections);
  }

  @org.jetbrains.annotations.Nullable
  private static String extractIntroductionFromSections(Map<String, List<RuleDetails.DescriptionSection>> sectionsByKey) {
    var introductionSections = sectionsByKey.get(INTRODUCTION_SECTION_KEY);
    String introductionHtmlContent = null;
    if (introductionSections != null && !introductionSections.isEmpty()) {
      // assume there is only one introduction section
      introductionHtmlContent = introductionSections.get(0).getHtmlContent();
    }
    return introductionHtmlContent;
  }

  private static void addMoreInfoTabIfNeeded(RuleDetails ruleDetails, ArrayList<RuleDescriptionTabDto> tabbedSections) {
    if (!ruleDetails.getDescriptionSectionsByKey().containsKey(RESOURCES_SECTION_KEY)) {
      var htmlSnippets = new ArrayList<String>();
      htmlSnippets.add(ruleDetails.getExtendedDescription());
      htmlSnippets.add(getCleanCodePrinciplesContent(ruleDetails.getCleanCodePrincipleKeys()));
      var content = concat(htmlSnippets);
      if (StringUtils.isNotBlank(content)) {
        tabbedSections
          .add(new RuleDescriptionTabDto(getTabTitle(ruleDetails, RESOURCES_SECTION_KEY), Either.forLeft(new RuleNonContextualSectionDto(content))));
      }
    }
  }

  private static Collection<RuleDescriptionTabDto> transformSectionsButIntroductionToTabs(RuleDetails ruleDetails, @Nullable String contextKey) {
    var tabbedSections = new ArrayList<RuleDescriptionTabDto>();
    var sectionsByKey = ruleDetails.getDescriptionSectionsByKey();
    SECTION_KEYS_ORDERED.forEach(sectionKey -> {
      if (sectionsByKey.containsKey(sectionKey)) {
        var tabContents = sectionsByKey.get(sectionKey);
        Either<RuleNonContextualSectionDto, RuleContextualSectionWithDefaultContextKeyDto> content;
        var foundMatchingContext = tabContents.stream().anyMatch(c -> c.getContext().isPresent() && c.getContext().get().getKey().equals(contextKey));
        if (tabContents.size() == 1 && tabContents.get(0).getContext().isEmpty()) {
          content = buildNonContextualSectionDto(ruleDetails, tabContents.get(0));
        } else {
          // if there is more than one section, they should all have a context (verified in sonar-plugin-api)
          var contextualSectionContents = tabContents.stream().map(s -> {
            var context = s.getContext().get();
            return new RuleContextualSectionDto(getTabContent(s, ruleDetails.getExtendedDescription(), ruleDetails.getCleanCodePrincipleKeys()), context.getKey(),
              context.getDisplayName());
          })
            .sorted(Comparator.comparing(RuleContextualSectionDto::getDisplayName))
            .collect(Collectors.toList());
          contextualSectionContents.add(
            new RuleContextualSectionDto(OthersSectionHtmlContent.getHtmlContent(),
              DEFAULT_CONTEXT_KEY, DEFAULT_CONTEXT_DISPLAY_NAME));
          content = Either.forRight(new RuleContextualSectionWithDefaultContextKeyDto(foundMatchingContext ? contextKey : DEFAULT_CONTEXT_KEY, contextualSectionContents));
        }
        tabbedSections.add(new RuleDescriptionTabDto(getTabTitle(ruleDetails, sectionKey), content));
      }
    });
    return tabbedSections;
  }

  private static String getTabTitle(RuleDetails ruleDetails, String sectionKey) {
    if (ROOT_CAUSE_SECTION_KEY.equals(sectionKey)) {
      return RuleType.SECURITY_HOTSPOT.equals(ruleDetails.getType()) ? "What's the risk?" : "Why is this an issue?";
    }
    if (ASSESS_THE_PROBLEM_SECTION_KEY.equals(sectionKey)) {
      return "Assess the risk";
    }
    if (HOW_TO_FIX_SECTION_KEY.equals(sectionKey)) {
      return "How can I fix it?";
    }
    return "More Info";
  }

  private static String concat(Collection<String> htmlSnippets) {
    return htmlSnippets.stream()
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.joining());
  }

  private static String getTabContent(RuleDetails.DescriptionSection section, @Nullable String extendedDescription, Set<String> educationPrincipleKeys) {
    var htmlSnippets = new ArrayList<String>();
    htmlSnippets.add(section.getHtmlContent());
    if (RESOURCES_SECTION_KEY.equals(section.getKey())) {
      htmlSnippets.add(extendedDescription);
      htmlSnippets.add(getCleanCodePrinciplesContent(educationPrincipleKeys));
    }
    return concat(htmlSnippets);
  }

  private static Collection<EffectiveRuleParamDto> transform(Collection<RuleDetails.EffectiveRuleParam> params) {
    var builder = new ArrayList<EffectiveRuleParamDto>();
    for (var param : params) {
      builder.add(new EffectiveRuleParamDto(
        param.getName(),
        param.getDescription(),
        param.getValue(),
        param.getDefaultValue()));
    }
    return builder;
  }

  private RuleDetailsAdapter() {
    // utility class
  }

  @NotNull
  private static Either<RuleNonContextualSectionDto, RuleContextualSectionWithDefaultContextKeyDto> buildNonContextualSectionDto(RuleDetails ruleDetails,
    RuleDetails.DescriptionSection matchingContext) {
    return Either.forLeft(new RuleNonContextualSectionDto(getTabContent(matchingContext, ruleDetails.getExtendedDescription(), ruleDetails.getCleanCodePrincipleKeys())));
  }

}
