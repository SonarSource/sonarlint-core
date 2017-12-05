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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.TestClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.client.api.util.FileUtils.toSonarQubePath;

public class InputFileBuilderTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  @Rule
  public ExpectedException exception = ExpectedException.none();

  private LanguageDetection langDetection = mock(LanguageDetection.class);
  private FileMetadata metadata = new FileMetadata();

  @Test
  public void testCreate() throws IOException {
    when(langDetection.language(any(InputFile.class))).thenReturn("java");

    Path path = temp.getRoot().toPath().resolve("file");
    Files.write(path, "test".getBytes(StandardCharsets.ISO_8859_1));
    ClientInputFile file = new TestClientInputFile(path, "file", true, StandardCharsets.ISO_8859_1);

    InputFileBuilder builder = new InputFileBuilder(langDetection, metadata);
    SonarLintInputFile inputFile = builder.create(file);

    assertThat(inputFile.type()).isEqualTo(InputFile.Type.TEST);
    assertThat(inputFile.file()).isEqualTo(path.toFile());
    assertThat(inputFile.absolutePath()).isEqualTo(toSonarQubePath(path.toString()));
    assertThat(inputFile.language()).isEqualTo("java");
    assertThat(inputFile.key()).isEqualTo(toSonarQubePath(path.toAbsolutePath().toString()));
    assertThat(inputFile.lines()).isEqualTo(1);

    assertThat(builder.langDetection()).isEqualTo(langDetection);
  }

  @Test
  public void testCreateWithLanguageSet() throws IOException {
    Path path = temp.getRoot().toPath().resolve("file");
    Files.write(path, "test".getBytes(StandardCharsets.ISO_8859_1));
    ClientInputFile file = new TestClientInputFile(path, "file", true, StandardCharsets.ISO_8859_1, "cpp");

    InputFileBuilder builder = new InputFileBuilder(langDetection, metadata);
    SonarLintInputFile inputFile = builder.create(file);

    assertThat(inputFile.language()).isEqualTo("cpp");
    verifyZeroInteractions(langDetection);
  }

  @Test
  public void testCreateError() throws IOException {
    when(langDetection.language(any(InputFile.class))).thenReturn("java");
    ClientInputFile file = new TestClientInputFile(Paths.get("INVALID"), "INVALID", true, StandardCharsets.ISO_8859_1);

    InputFileBuilder builder = new InputFileBuilder(langDetection, metadata);

    exception.expect(IllegalStateException.class);
    exception.expectMessage("Failed to open a stream on file");
    builder.create(file);
  }
}
