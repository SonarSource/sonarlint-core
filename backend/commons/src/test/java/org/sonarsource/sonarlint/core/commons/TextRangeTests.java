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
import org.sonarsource.sonarlint.core.commons.api.TextRange;

import static org.assertj.core.api.Assertions.assertThat;

class TextRangeTests {

  @Test
  void test_getters() {
    var textRange = new TextRange(1, 2, 3, 4);
    assertThat(textRange.getStartLine()).isEqualTo(1);
    assertThat(textRange.getStartLineOffset()).isEqualTo(2);
    assertThat(textRange.getEndLine()).isEqualTo(3);
    assertThat(textRange.getEndLineOffset()).isEqualTo(4);
  }

  @Test
  void test_equals_hashcode() {
    var textRange = new TextRange(1, 2, 3, 4);
    assertThat(textRange).hasSameHashCodeAs(new TextRange(1, 2, 3, 4))
      .isEqualTo(textRange)
      .isEqualTo(new TextRange(1, 2, 3, 4))
      .isNotEqualTo(new TextRange(11, 2, 3, 4))
      .isNotEqualTo(new TextRange(1, 22, 3, 4))
      .isNotEqualTo(new TextRange(1, 2, 33, 4))
      .isNotEqualTo(new TextRange(1, 2, 3, 44))
      .isNotEqualTo("foo");
  }

}
