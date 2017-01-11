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

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.utils.PathUtils;

public class SonarLintInputDirTest {
  private SonarLintInputDir inputDir;
  private Path path;

  @Before
  public void setUp() {
    path = Paths.get("file1").toAbsolutePath();
    inputDir = new SonarLintInputDir(path);
  }

  @Test
  public void testInputDir() {
    assertThat(inputDir.absolutePath()).isEqualTo(PathUtils.canonicalPath(path.toFile()));
    assertThat(inputDir.file()).isEqualTo(path.toFile());
    assertThat(inputDir.key()).isEqualTo(PathUtils.canonicalPath(path.toFile()));
    assertThat(inputDir.isFile()).isFalse();
    assertThat(inputDir.moduleKey()).isNull();
    assertThat(inputDir.path()).isEqualTo(path);
    assertThat(inputDir.relativePath()).isEqualTo(inputDir.absolutePath());
    assertThat(inputDir.toString()).isEqualTo("[path=" + path + "]");
    assertThat(inputDir.equals(mock(SonarLintInputDir.class))).isFalse();
    assertThat(inputDir.equals(inputDir)).isTrue();
  }

}
