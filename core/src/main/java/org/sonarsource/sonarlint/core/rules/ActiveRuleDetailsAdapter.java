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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleContextualSectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleDescriptionTabDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleMonolithicDescriptionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleNonContextualSectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleParamDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleSplitDescriptionDto;

class ActiveRuleDetailsAdapter {
  public static final String INTRODUCTION_SECTION_KEY = "introduction";
  public static final String RESOURCES_SECTION_KEY = "resources";
  private static final Map<String, String> SECTION_KEYS_TO_TAB_TITLE_ORDERED = new LinkedHashMap<>();

  static {
    SECTION_KEYS_TO_TAB_TITLE_ORDERED.put("root_cause", "Why is this an issue?");
    SECTION_KEYS_TO_TAB_TITLE_ORDERED.put("assess_the_problem", "Assess the risk");
    SECTION_KEYS_TO_TAB_TITLE_ORDERED.put("how_to_fix", "How can I fix it?");
    SECTION_KEYS_TO_TAB_TITLE_ORDERED.put(RESOURCES_SECTION_KEY, "More Info");
  }

  public static ActiveRuleDetailsDto transform(ActiveRuleDetails ruleDetails) {
    return new ActiveRuleDetailsDto(
      ruleDetails.getKey(),
      ruleDetails.getName(),
      ruleDetails.getDefaultSeverity(),
      ruleDetails.getType(),
      transformDescriptions(ruleDetails),
      transform(ruleDetails.getParams()),
      ruleDetails.getLanguage());
  }

  private static Either<ActiveRuleMonolithicDescriptionDto, ActiveRuleSplitDescriptionDto> transformDescriptions(ActiveRuleDetails ruleDetails) {
    if (ruleDetails.hasMonolithicDescription()) {
      return Either.forLeft(transformMonolithicDescription(ruleDetails));
    }
    return Either.forRight(transformSplitDescription(ruleDetails));
  }

  private static ActiveRuleMonolithicDescriptionDto transformMonolithicDescription(ActiveRuleDetails ruleDetails) {
    var htmlSnippets = new ArrayList<String>();
    htmlSnippets.add(ruleDetails.getHtmlDescription());
    htmlSnippets.add(ruleDetails.getExtendedDescription());
    htmlSnippets.add(getCleanCodePrinciplesContent(ruleDetails.getCleanCodePrincipleKeys()));
    return new ActiveRuleMonolithicDescriptionDto(concat(htmlSnippets));
  }

  private static String getCleanCodePrinciplesContent(Set<String> cleanCodePrincipleKeys) {
    var principles = cleanCodePrincipleKeys.stream().sorted(Comparator.naturalOrder()).map(CleanCodePrinciples::getContent).collect(Collectors.toList());
    return (principles.stream().anyMatch(StringUtils::isNotBlank) ? "<h3>Clean Code Principles</h3>\n" : "") + concat(principles);
  }

  private static ActiveRuleSplitDescriptionDto transformSplitDescription(ActiveRuleDetails ruleDetails) {
    var sectionsByKey = ruleDetails.getDescriptionSectionsByKey();

    var tabbedSections = new ArrayList<>(transformSectionsButIntroductionToTabs(ruleDetails));
    addMoreInfoTabIfNeeded(ruleDetails, tabbedSections);
    return new ActiveRuleSplitDescriptionDto(extractIntroductionFromSections(sectionsByKey), tabbedSections);
  }

  @org.jetbrains.annotations.Nullable
  private static String extractIntroductionFromSections(Map<String, List<ActiveRuleDetails.DescriptionSection>> sectionsByKey) {
    var introductionSections = sectionsByKey.get(INTRODUCTION_SECTION_KEY);
    String introductionHtmlContent = null;
    if (introductionSections != null && !introductionSections.isEmpty()) {
      // assume there is only one introduction section
      introductionHtmlContent = introductionSections.get(0).getHtmlContent();
    }
    return introductionHtmlContent;
  }

  private static void addMoreInfoTabIfNeeded(ActiveRuleDetails ruleDetails, ArrayList<ActiveRuleDescriptionTabDto> tabbedSections) {
    if (!ruleDetails.getDescriptionSectionsByKey().containsKey(RESOURCES_SECTION_KEY)) {
      var htmlSnippets = new ArrayList<String>();
      htmlSnippets.add(ruleDetails.getExtendedDescription());
      htmlSnippets.add(getCleanCodePrinciplesContent(ruleDetails.getCleanCodePrincipleKeys()));
      var content = concat(htmlSnippets);
      if (StringUtils.isNotBlank(content)) {
        tabbedSections
          .add(new ActiveRuleDescriptionTabDto(SECTION_KEYS_TO_TAB_TITLE_ORDERED.get(RESOURCES_SECTION_KEY), Either.forLeft(new ActiveRuleNonContextualSectionDto(content))));
      }
    }
  }

  private static Collection<ActiveRuleDescriptionTabDto> transformSectionsButIntroductionToTabs(ActiveRuleDetails ruleDetails) {
    var tabbedSections = new ArrayList<ActiveRuleDescriptionTabDto>();
    var sectionsByKey = ruleDetails.getDescriptionSectionsByKey();
    SECTION_KEYS_TO_TAB_TITLE_ORDERED.keySet().forEach(sectionKey -> {
      if (sectionsByKey.containsKey(sectionKey)) {
        var sections = sectionsByKey.get(sectionKey);
        var title = SECTION_KEYS_TO_TAB_TITLE_ORDERED.get(sectionKey);
        Either<ActiveRuleNonContextualSectionDto, Collection<ActiveRuleContextualSectionDto>> content;
        if (sections.size() == 1 && sections.get(0).getContext().isEmpty()) {
          content = Either
            .forLeft(new ActiveRuleNonContextualSectionDto(getTabContent(sections.get(0), ruleDetails.getExtendedDescription(), ruleDetails.getCleanCodePrincipleKeys())));
        } else {
          // if there is more than one section, they should all have a context (verified in sonar-plugin-api)
          content = Either.forRight(sections.stream().map(s -> {
            var context = s.getContext().get();
            return new ActiveRuleContextualSectionDto(getTabContent(s, ruleDetails.getExtendedDescription(), ruleDetails.getCleanCodePrincipleKeys()), context.getKey(),
              context.getDisplayName());
          }).collect(Collectors.toList()));
        }
        tabbedSections.add(new ActiveRuleDescriptionTabDto(title, content));
      }
    });
    return tabbedSections;
  }

  private static String concat(Collection<String> htmlSnippets) {
    return htmlSnippets.stream()
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.joining("<br/><br/>"));
  }

  private static String getTabContent(ActiveRuleDetails.DescriptionSection section, @Nullable String extendedDescription, Set<String> educationPrincipleKeys) {
    var htmlSnippets = new ArrayList<String>();
    htmlSnippets.add(section.getHtmlContent());
    if (RESOURCES_SECTION_KEY.equals(section.getKey())) {
      htmlSnippets.add(extendedDescription);
      htmlSnippets.add(getCleanCodePrinciplesContent(educationPrincipleKeys));
    }
    return concat(htmlSnippets);
  }

  private static Collection<ActiveRuleParamDto> transform(Collection<ActiveRuleDetails.ActiveRuleParam> params) {
    var builder = new ArrayList<ActiveRuleParamDto>();
    for (var param : params) {
      builder.add(new ActiveRuleParamDto(
        param.getName(),
        param.getDescription(),
        param.getDefaultValue()));
    }
    return builder;
  }

  private ActiveRuleDetailsAdapter() {
    // utility class
  }
}
