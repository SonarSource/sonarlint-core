/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.file;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class FilePathTranslationTests {

  @Test
  void serverToPathTranslation() {
    var underTest = new FilePathTranslation(Path.of("/foo"), Path.of("/bar"));

    assertThat(underTest.serverToIdePath(Path.of("/baz"))).isEqualTo(Path.of("/baz"));
    assertThat(underTest.serverToIdePath(Path.of("/bar/baz"))).isEqualTo(Path.of("/foo/baz"));
  }

  @Test
  void serverToPathTranslationWhenPrefixIsEmpty() {
    var underTest = new FilePathTranslation(Path.of("ide"), Path.of(""));

    assertThat(underTest.serverToIdePath(Path.of("baz"))).isEqualTo(Path.of("ide/baz"));
    assertThat(underTest.serverToIdePath(Path.of("bar/baz"))).isEqualTo(Path.of("ide/bar/baz"));
  }

  @Test
  void ideToServerPathTranslation() {
    var underTest = new FilePathTranslation(Path.of("/foo"), Path.of("/bar"));

    assertThat(underTest.ideToServerPath(Path.of("/baz"))).isEqualTo(Path.of("/baz"));
    assertThat(underTest.ideToServerPath(Path.of("/foo/baz"))).isEqualTo(Path.of("/bar/baz"));
  }

  @Test
  void ideToServerPathTranslationWhenPrefixIsEmpty() {
    var underTest = new FilePathTranslation(Path.of(""), Path.of("server"));

    assertThat(underTest.ideToServerPath(Path.of("baz"))).isEqualTo(Path.of("server/baz"));
    assertThat(underTest.ideToServerPath(Path.of("foo/baz"))).isEqualTo(Path.of("server/foo/baz"));
  }

}
