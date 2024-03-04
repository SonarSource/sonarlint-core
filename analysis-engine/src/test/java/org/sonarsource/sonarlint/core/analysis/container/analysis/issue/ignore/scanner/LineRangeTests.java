/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.scanner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LineRangeTests {

  @Test
  void lineRangeShouldBeOrdered() {
    assertThrows(IllegalArgumentException.class, () -> new LineRange(25, 12));
  }

  @Test
  void shouldConvertLineRangeToLines() {
    var range = new LineRange(12, 15);

    assertThat(range.toLines()).containsOnly(12, 13, 14, 15);
  }

  @Test
  void shouldTestInclusionInRangeOfLines() {
    var range = new LineRange(12, 15);

    assertThat(range.in(3)).isFalse();
    assertThat(range.in(12)).isTrue();
    assertThat(range.in(13)).isTrue();
    assertThat(range.in(14)).isTrue();
    assertThat(range.in(15)).isTrue();
    assertThat(range.in(16)).isFalse();
  }

  @Test
  void testToString() {
    assertThat(new LineRange(12, 15)).hasToString("[12-15]");
  }

  @Test
  void testEquals() {
    var range = new LineRange(12, 15);
    assertThat(range).isEqualTo(range)
      .isEqualTo(new LineRange(12, 15))
      .isNotEqualTo(new LineRange(12, 2000))
      .isNotEqualTo(new LineRange(1000, 2000))
      .isNotEqualTo(null)
      .isNotEqualTo(new StringBuffer());
  }

  @Test
  void testHashCode() {
    assertThat(new LineRange(12, 15)).hasSameHashCodeAs(new LineRange(12, 15).hashCode());
  }
}
