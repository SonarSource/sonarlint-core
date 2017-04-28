/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.analysis.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.fs.InputFile;

public class InputPathCacheTest {
  private InputPathCache cache;

  @Before
  public void setUp() {
    cache = new InputPathCache();
  }

  @Test
  public void testDirs() {
    InputDir dir1 = mock(InputDir.class);
    when(dir1.path()).thenReturn(Paths.get("dir1"));
    InputDir dir2 = mock(InputDir.class);
    when(dir2.path()).thenReturn(Paths.get("dir2"));

    cache.doAdd(dir1);
    cache.add(dir2);
    assertThat(cache.allDirs()).containsOnly(dir1, dir2);

    assertThat(cache.inputDir(Paths.get("dir1"))).isEqualTo(dir1);

    // always null
    assertThat(cache.inputDir("dir1")).isNull();
  }

  @Test
  public void testFiles() {
    InputFile file1 = mock(InputFile.class);
    when(file1.path()).thenReturn(Paths.get("file1.java"));
    when(file1.file()).thenReturn(new File("file1.java"));
    when(file1.language()).thenReturn("lang1");
    InputFile file2 = mock(InputFile.class);
    when(file2.path()).thenReturn(Paths.get("file2"));
    when(file2.file()).thenReturn(new File("file2"));
    when(file2.language()).thenReturn("lang2");

    cache.doAdd(file1);
    cache.doAdd(file2);
    assertThat(cache.inputFiles()).containsOnly(file1, file2);

    assertThat(cache.inputFile(Paths.get("file1.java"))).isEqualTo(file1);

    // always null
    assertThat(cache.inputFile("file1.java")).isNull();

    assertThat(cache.getFilesByExtension("java")).containsOnly(file1);
    assertThat(cache.getFilesByExtension("")).containsOnly(file2);
    assertThat(cache.getFilesByName("file1.java")).containsOnly(file1);

    assertThat(cache.languages()).containsExactly("lang1", "lang2");

  }
}
