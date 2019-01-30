/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile.Status;
import org.sonarsource.sonarlint.core.TestClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.sonarsource.sonarlint.core.client.api.util.FileUtils.toSonarQubePath;

public class SonarLintInputFileTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private SonarLintInputFile file;
  private ClientInputFile inputFile;
  private Path path;

  @Before
  public void setUp() throws IOException {
    path = temp.newFile().toPath();
    Files.write(path, "test string".getBytes(StandardCharsets.UTF_8));
    inputFile = new TestClientInputFile(path, "file", false, StandardCharsets.UTF_8);
    file = new SonarLintInputFile(inputFile, f -> new FileMetadata().readMetadata(path.toFile(), StandardCharsets.UTF_8));
  }

  @Test
  public void testGetters() throws IOException {
    assertThat(file.contents()).isEqualTo("test string");
    assertThat(file.charset()).isEqualByComparingTo(StandardCharsets.UTF_8);
    assertThat(file.absolutePath()).isEqualTo(toSonarQubePath(inputFile.getPath()));
    assertThat(file.file()).isEqualTo(path.toFile());
    assertThat(file.path()).isEqualTo(path);
    assertThat(file.getClientInputFile()).isEqualTo(inputFile);
    assertThat(file.status()).isEqualTo(Status.ADDED);
    assertThat(file.equals(file)).isTrue();
    assertThat(file.equals(mock(SonarLintInputFile.class))).isFalse();

    InputStream stream = file.inputStream();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
      assertThat(reader.lines().collect(Collectors.joining())).isEqualTo("test string");
    }

  }
}
