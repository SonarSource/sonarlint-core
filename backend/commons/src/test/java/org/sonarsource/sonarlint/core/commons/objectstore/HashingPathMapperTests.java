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
package org.sonarsource.sonarlint.core.commons.objectstore;

import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HashingPathMapperTests {
  @Test
  void should_create_2_levels_of_nesting_for_level_2() {
    PathMapper<String> mapper = new HashingPathMapper(Paths.get("."), 2);
    // note: an easy way to verify the sha1 of something in Linux: printf something | sha1sum
    assertThat(mapper.apply("sample1")).isEqualTo(Paths.get("./c/3/c37bca4afb8ff7f52f450b04c1973f37dfde48db"));
    assertThat(mapper.apply("sample2")).isEqualTo(Paths.get("./1/f/1ff0b5b1c089d0f9e040a9080110e0be12d42867"));
  }

  @Test
  void should_create_5_levels_of_nesting_for_level_5() {
    PathMapper<String> mapper = new HashingPathMapper(Paths.get("."), 5);
    assertThat(mapper.apply("sample1")).isEqualTo(Paths.get("./c/3/7/b/c/c37bca4afb8ff7f52f450b04c1973f37dfde48db"));
    assertThat(mapper.apply("sample2")).isEqualTo(Paths.get("./1/f/f/0/b/1ff0b5b1c089d0f9e040a9080110e0be12d42867"));
  }

  @Test
  void should_throw_if_levels_below_1() {
    var base = Paths.get(".");
    assertThrows(IllegalArgumentException.class, () -> {
      new HashingPathMapper(base, 0);
    });
  }

  @Test
  void should_throw_if_levels_above_40() {
    var base = Paths.get(".");
    assertThrows(IllegalArgumentException.class, () -> {
      new HashingPathMapper(base, 41);
    });
  }
}
