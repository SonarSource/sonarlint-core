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
package org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonar.api.batch.fs.InputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InputFileCacheTests {
  private InputFileIndex cache;

  @BeforeEach
  void setUp() {
    cache = new InputFileIndex();
  }

  @Test
  void testFiles() {
    var file1 = mock(InputFile.class);
    when(file1.filename()).thenReturn("file1.java");
    when(file1.language()).thenReturn("lang1");
    var file2 = mock(InputFile.class);
    when(file2.filename()).thenReturn("file2");
    when(file2.language()).thenReturn("lang2");

    cache.doAdd(file1);
    cache.doAdd(file2);
    assertThat(cache.inputFiles()).containsOnly(file1, file2);

    assertThrows(UnsupportedOperationException.class, () -> cache.inputFile("file1.java"));

    assertThat(cache.getFilesByExtension("java")).containsOnly(file1);
    assertThat(cache.getFilesByExtension("")).containsOnly(file2);
    assertThat(cache.getFilesByName("file1.java")).containsOnly(file1);

    assertThat(cache.languages()).containsExactly("lang1", "lang2");

  }
}
