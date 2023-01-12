/*
 * SonarLint Core - Rule Extractor
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
package org.sonarsource.sonarlint.core.rule.extractor;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.rule.extractor.SecurityStandards.CWES_BY_SL_CATEGORY;
import static org.sonarsource.sonarlint.core.rule.extractor.SecurityStandards.SL_CATEGORY_KEYS_ORDERING;
import static org.sonarsource.sonarlint.core.rule.extractor.SecurityStandards.fromSecurityStandards;

class SecurityStandardsTest {
  @Test
  void fromSecurityStandards_from_empty_set_has_SLCategory_OTHERS() {
    SecurityStandards securityStandards = fromSecurityStandards(emptySet());

    assertThat(securityStandards.getStandards()).isEmpty();
    assertThat(securityStandards.getSlCategory()).isEqualTo(SecurityStandards.SLCategory.OTHERS);
    assertThat(securityStandards.getIgnoredSLCategories()).isEmpty();
  }

  @Test
  void fromSecurityStandards_from_empty_set_has_unknown_cwe_standard() {
    SecurityStandards securityStandards = fromSecurityStandards(emptySet());

    assertThat(securityStandards.getStandards()).isEmpty();
    assertThat(securityStandards.getCwe()).containsOnly("unknown");
  }

  @Test
  void fromSecurityStandards_finds_SLCategory_from_any_if_the_mapped_CWE_standard() {
    CWES_BY_SL_CATEGORY.forEach((slCategory, cwes) -> {
      cwes.forEach(cwe -> {
        SecurityStandards securityStandards = fromSecurityStandards(singleton("cwe:" + cwe));

        assertThat(securityStandards.getSlCategory()).isEqualTo(slCategory);
      });
    });
  }

  @Test
  void fromSecurityStandards_finds_SLCategory_from_multiple_of_the_mapped_CWE_standard() {
    CWES_BY_SL_CATEGORY.forEach((slCategory, cwes) -> {
      SecurityStandards securityStandards = fromSecurityStandards(cwes.stream().map(t -> "cwe:" + t).collect(toSet()));

      assertThat(securityStandards.getSlCategory()).isEqualTo(slCategory);
    });
  }

  @Test
  void fromSecurityStandards_finds_SLCategory_first_in_order_when_CWEs_map_to_multiple_SLCategories() {
    EnumSet<SecurityStandards.SLCategory> slCategories = EnumSet.allOf(SecurityStandards.SLCategory.class);
    slCategories.remove(SecurityStandards.SLCategory.OTHERS);

    while (!slCategories.isEmpty()) {
      // First category from SLCategories
      SecurityStandards.SLCategory expected = slCategories.stream().min(SL_CATEGORY_KEYS_ORDERING.onResultOf(SecurityStandards.SLCategory::getKey)).get();
      // Everything except for the expected category
      SecurityStandards.SLCategory[] expectedIgnored = slCategories.stream().filter(t -> t != expected).toArray(SecurityStandards.SLCategory[]::new);

      // All CWEs from all categories
      Set<String> cwes = slCategories.stream()
        .flatMap(t -> CWES_BY_SL_CATEGORY.get(t).stream().map(e -> "cwe:" + e))
        .collect(Collectors.toSet());
      SecurityStandards securityStandards = fromSecurityStandards(cwes);

      assertThat(securityStandards.getSlCategory()).isEqualTo(expected);
      assertThat(securityStandards.getIgnoredSLCategories()).containsOnly(expectedIgnored);

      slCategories.remove(expected);
    }
  }
}
