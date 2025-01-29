/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.commons.util.StringUtils.pluralize;
import static org.sonarsource.sonarlint.core.commons.util.StringUtils.sanitizeAgainstRTLO;

class StringUtilsTests {

  @Test
  void should_pluralize_words() {
    assertThat(pluralize(0, "word")).isEqualTo("0 words");
    assertThat(pluralize(1, "word")).isEqualTo("1 word");
    assertThat(pluralize(2, "word")).isEqualTo("2 words");
  }

  @Test
  void should_sanitize_against_rtlo() {
    assertThat(sanitizeAgainstRTLO("This is a \u202eegassem")).isEqualTo("This is a egassem");
  }

  @Test
  void should_sanitize_with_null() {
    assertThat(sanitizeAgainstRTLO(null)).isNull();
  }

}
