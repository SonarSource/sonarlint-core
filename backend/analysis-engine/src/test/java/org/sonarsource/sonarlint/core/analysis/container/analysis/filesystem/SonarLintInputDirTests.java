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

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonar.api.utils.PathUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SonarLintInputDirTests {
  private SonarLintInputDir inputDir;
  private Path path;

  @BeforeEach
  void setUp() {
    path = Paths.get("file1").toAbsolutePath();
    inputDir = new SonarLintInputDir(path);
  }

  @Test
  void testInputDir() {
    assertThat(inputDir.absolutePath()).isEqualTo(PathUtils.canonicalPath(path.toFile()));
    assertThat(inputDir.file()).isEqualTo(path.toFile());
    assertThat(inputDir.key()).isEqualTo(PathUtils.canonicalPath(path.toFile()));
    assertThat(inputDir.isFile()).isFalse();
    assertThat(inputDir.path()).isEqualTo(path);
    assertThat(inputDir.relativePath()).isEqualTo(inputDir.absolutePath());
    assertThat(inputDir)
      .hasToString("[path=" + path + "]")
      .isNotEqualTo(mock(SonarLintInputDir.class))
      .isEqualTo(inputDir);
  }

}
