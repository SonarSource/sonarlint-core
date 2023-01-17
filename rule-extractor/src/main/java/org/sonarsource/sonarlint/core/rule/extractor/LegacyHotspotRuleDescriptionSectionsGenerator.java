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

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDescriptionSection.Context;

import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.sonar.api.server.rule.RuleDescriptionSection.RuleDescriptionSectionKeys.ASSESS_THE_PROBLEM_SECTION_KEY;
import static org.sonar.api.server.rule.RuleDescriptionSection.RuleDescriptionSectionKeys.HOW_TO_FIX_SECTION_KEY;
import static org.sonar.api.server.rule.RuleDescriptionSection.RuleDescriptionSectionKeys.ROOT_CAUSE_SECTION_KEY;

/**
 * @see <a href="https://github.com/SonarSource/sonar-enterprise/blob/36eae8ba853a6a411d1932d6faa2265510843580/server/sonar-webserver-core/src/main/java/org/sonar/server/rule/LegacyHotspotRuleDescriptionSectionsGenerator.java">Original on SonarQube</a>
 */
public class LegacyHotspotRuleDescriptionSectionsGenerator {

  private LegacyHotspotRuleDescriptionSectionsGenerator() {
    // Static stuff only
  }

  static List<SonarLintRuleDescriptionSection> extractDescriptionSectionsFromHtml(@Nullable String descriptionInHtml) {
    if (descriptionInHtml == null || descriptionInHtml.isEmpty()) {
      return List.of();
    }
    String[] split = extractSection("", descriptionInHtml);
    String remainingText = split[0];
    String ruleDescriptionSection = split[1];

    split = extractSection("<h2>Exceptions</h2>", remainingText);
    remainingText = split[0];
    String exceptions = split[1];

    split = extractSection("<h2>Ask Yourself Whether</h2>", remainingText);
    remainingText = split[0];
    String askSection = split[1];

    split = extractSection("<h2>Sensitive Code Example</h2>", remainingText);
    remainingText = split[0];
    String sensitiveSection = split[1];

    split = extractSection("<h2>Noncompliant Code Example</h2>", remainingText);
    remainingText = split[0];
    String noncompliantSection = split[1];

    split = extractSection("<h2>Recommended Secure Coding Practices</h2>", remainingText);
    remainingText = split[0];
    String recommendedSection = split[1];

    split = extractSection("<h2>Compliant Solution</h2>", remainingText);
    remainingText = split[0];
    String compliantSection = split[1];

    split = extractSection("<h2>See</h2>", remainingText);
    remainingText = split[0];
    String seeSection = split[1];

    Optional<SonarLintRuleDescriptionSection> rootSection = createSection(ROOT_CAUSE_SECTION_KEY, ruleDescriptionSection, exceptions, remainingText);
    Optional<SonarLintRuleDescriptionSection> assessSection = createSection(ASSESS_THE_PROBLEM_SECTION_KEY, askSection, sensitiveSection, noncompliantSection);
    Optional<SonarLintRuleDescriptionSection> fixSection = createSection(HOW_TO_FIX_SECTION_KEY, recommendedSection, compliantSection, seeSection);

    return Stream.of(rootSection, assessSection, fixSection)
      .filter(Predicate.not(Optional::isEmpty))
      .flatMap(Optional::stream)
      .collect(Collectors.toList());
  }

  private static String[] extractSection(String beginning, String description) {
    var endSection = "<h2>";
    var beginningIndex = description.indexOf(beginning);
    if (beginningIndex != -1) {
      var endIndex = description.indexOf(endSection, beginningIndex + beginning.length());
      if (endIndex == -1) {
        endIndex = description.length();
      }
      return new String[] {
        description.substring(0, beginningIndex) + description.substring(endIndex),
        description.substring(beginningIndex, endIndex)
      };
    } else {
      return new String[] {description, ""};
    }
  }

  private static Optional<SonarLintRuleDescriptionSection> createSection(String sectionKey, String... contentPieces) {
    var content = trimToNull(String.join("", contentPieces));
    if (content == null) {
      return Optional.empty();
    }
    return Optional.of(new SonarLintRuleDescriptionSection(sectionKey, content, emptyContextForConvertedHotspotSection()));
  }

  private static Optional<Context> emptyContextForConvertedHotspotSection() {
    return Optional.empty();
  }
}
