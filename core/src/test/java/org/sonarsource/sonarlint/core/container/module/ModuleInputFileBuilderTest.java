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
package org.sonarsource.sonarlint.core.container.module;

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
import org.sonarsource.sonarlint.core.OnDiskTestClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.FileMetadata;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.LanguageDetection;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.client.api.util.FileUtils.toSonarQubePath;

public class ModuleInputFileBuilderTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  @Rule
  public ExpectedException exception = ExpectedException.none();

  private final LanguageDetection langDetection = mock(LanguageDetection.class);
  private final FileMetadata metadata = new FileMetadata();

  @Test
  public void testCreate() throws IOException {
    when(langDetection.language(any(InputFile.class))).thenReturn(Language.JAVA);

    Path path = temp.getRoot().toPath().resolve("file");
    Files.write(path, "test".getBytes(StandardCharsets.ISO_8859_1));
    ClientInputFile file = new OnDiskTestClientInputFile(path, "file", true, StandardCharsets.ISO_8859_1);

    ModuleInputFileBuilder builder = new ModuleInputFileBuilder(langDetection, metadata);
    SonarLintInputFile inputFile = builder.create(file);

    assertThat(inputFile.type()).isEqualTo(InputFile.Type.TEST);
    assertThat(inputFile.file()).isEqualTo(path.toFile());
    assertThat(inputFile.absolutePath()).isEqualTo(toSonarQubePath(path.toString()));
    assertThat(inputFile.language()).isEqualTo("java");
    assertThat(inputFile.key()).isEqualTo(path.toUri().toString());
    assertThat(inputFile.lines()).isEqualTo(1);
  }

  @Test
  public void testCreateWithLanguageSet() throws IOException {
    Path path = temp.getRoot().toPath().resolve("file");
    Files.write(path, "test".getBytes(StandardCharsets.ISO_8859_1));
    ClientInputFile file = new OnDiskTestClientInputFile(path, "file", true, StandardCharsets.ISO_8859_1, Language.CPP);

    ModuleInputFileBuilder builder = new ModuleInputFileBuilder(langDetection, metadata);
    SonarLintInputFile inputFile = builder.create(file);

    assertThat(inputFile.language()).isEqualTo("cpp");
    verifyZeroInteractions(langDetection);
  }

  @Test
  public void testCreate_lazy_error() throws IOException {
    when(langDetection.language(any(InputFile.class))).thenReturn(Language.JAVA);
    ClientInputFile file = new OnDiskTestClientInputFile(Paths.get("INVALID"), "INVALID", true, StandardCharsets.ISO_8859_1);

    ModuleInputFileBuilder builder = new ModuleInputFileBuilder(langDetection, metadata);
    SonarLintInputFile slFile = builder.create(file);

    exception.expect(IllegalStateException.class);
    exception.expectMessage("Failed to open a stream on file");

    // Call any method that will trigger metadata initialization
    slFile.selectLine(1);

  }
}
