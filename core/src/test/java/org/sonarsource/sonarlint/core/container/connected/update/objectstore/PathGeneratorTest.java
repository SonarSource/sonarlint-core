/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected.update.objectstore;

import org.junit.Test;

import java.nio.file.Paths;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class PathGeneratorTest {
  @Test
  public void first_3_of_one_level_10_should_be_1_2_3() {
    PathGenerator gen = new PathGenerator(1, 10, Paths.get("."));
    assertThat(gen.next()).isEqualTo(Paths.get("./0"));
    assertThat(gen.next()).isEqualTo(Paths.get("./1"));
    assertThat(gen.next()).isEqualTo(Paths.get("./2"));
  }

  @Test
  public void first_3_of_two_level_10_should_be_0x1_0x2_0x3() {
    PathGenerator gen = new PathGenerator(2, 10, Paths.get("."));
    assertThat(gen.next()).isEqualTo(Paths.get("./0/0"));
    assertThat(gen.next()).isEqualTo(Paths.get("./0/1"));
    assertThat(gen.next()).isEqualTo(Paths.get("./0/2"));
  }

  @Test
  public void first_3_of_two_level_2_should_be_0x1_0x2_1x1() {
    PathGenerator gen = new PathGenerator(2, 2, Paths.get("."));
    assertThat(gen.next()).isEqualTo(Paths.get("./0/0"));
    assertThat(gen.next()).isEqualTo(Paths.get("./0/1"));
    assertThat(gen.next()).isEqualTo(Paths.get("./1/0"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void should_throw_if_levels_below_1() {
    new PathGenerator(0, 10, Paths.get("."));
  }

  @Test(expected = IllegalArgumentException.class)
  public void should_throw_if_filesPerLevel_below_1() {
    new PathGenerator(1, 0, Paths.get("."));
  }

  @Test
  public void more_than_9_should_exhaust_3_level_3() {
    int levels = 3;
    int filesPerLevel = 3;

    PathGenerator gen = new PathGenerator(levels, filesPerLevel, Paths.get("."));

    for (int i = 0; i < Math.pow(filesPerLevel, levels); i++) {
      assertThat(gen.hasNext()).isTrue();
      gen.next();
    }

    assertThat(gen.hasNext()).isFalse();
    assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(gen::next);
  }
}
