/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.tracking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class PathStoreKeyValidatorTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void should_return_true_if_relative_path_exists() throws IOException {
    Path projectBaseDir = temporaryFolder.newFolder().toPath();
    String filename = "dummy";
    Files.createFile(projectBaseDir.resolve(filename));
    assertThat(new PathStoreKeyValidator(projectBaseDir).apply(filename)).isTrue();
  }

  @Test
  public void should_return_false_if_relative_path_does_not_exist() throws IOException {
    assertThat(new PathStoreKeyValidator(temporaryFolder.newFolder().toPath()).apply("nonexistent")).isFalse();
  }
}
