/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextRangeWithHashTests {

  @Test
  void test_getters() {
    var textRange = new TextRangeWithHash(1, 2, 3, 4, "md5");
    assertThat(textRange.getHash()).isEqualTo("md5");
  }

  @Test
  void test_equals_hashcode() {
    var textRange = new TextRangeWithHash(1, 2, 3, 4, "md5");
    assertThat(textRange).hasSameHashCodeAs(new TextRangeWithHash(1, 2, 3, 4, "md5"))
      .isEqualTo(textRange)
      .isEqualTo(new TextRangeWithHash(1, 2, 3, 4, "md5"))
      .isNotEqualTo(new TextRange(1, 2, 3, 4))
      .isNotEqualTo(new TextRangeWithHash(11, 2, 3, 4, "md5"))
      .isNotEqualTo(new TextRangeWithHash(1, 22, 3, 4, "md5"))
      .isNotEqualTo(new TextRangeWithHash(1, 2, 33, 4, "md5"))
      .isNotEqualTo(new TextRangeWithHash(1, 2, 3, 44, "md5"))
      .isNotEqualTo(new TextRangeWithHash(1, 2, 3, 4, "md55"))
      .isNotEqualTo("foo");
  }

}
