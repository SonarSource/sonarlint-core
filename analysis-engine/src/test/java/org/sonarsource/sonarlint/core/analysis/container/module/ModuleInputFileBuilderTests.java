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
package org.sonarsource.sonarlint.core.analysis.container.module;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.FileMetadata;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.LanguageDetection;
import org.sonarsource.sonarlint.core.commons.Language;
import testutils.FileUtils;
import testutils.OnDiskTestClientInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModuleInputFileBuilderTests {

  private final LanguageDetection langDetection = mock(LanguageDetection.class);
  private final FileMetadata metadata = new FileMetadata();

  @TempDir
  private Path tempDir;

  @Test
  void testCreate() throws IOException {
    when(langDetection.language(any(InputFile.class))).thenReturn(Language.JAVA);

    var path = tempDir.resolve("file");
    Files.write(path, "test".getBytes(StandardCharsets.ISO_8859_1));
    ClientInputFile file = new OnDiskTestClientInputFile(path, "file", true, StandardCharsets.ISO_8859_1);

    var builder = new ModuleInputFileBuilder(langDetection, metadata);
    var inputFile = builder.create(file);

    assertThat(inputFile.type()).isEqualTo(InputFile.Type.TEST);
    assertThat(inputFile.file()).isEqualTo(path.toFile());
    assertThat(inputFile.absolutePath()).isEqualTo(FileUtils.toSonarQubePath(path.toString()));
    assertThat(inputFile.language()).isEqualTo("java");
    assertThat(inputFile.key()).isEqualTo(path.toUri().toString());
    assertThat(inputFile.lines()).isEqualTo(1);
  }

  @Test
  void testCreateWithLanguageSet() throws IOException {
    var path = tempDir.resolve("file");
    Files.write(path, "test".getBytes(StandardCharsets.ISO_8859_1));
    ClientInputFile file = new OnDiskTestClientInputFile(path, "file", true, StandardCharsets.ISO_8859_1, Language.CPP);

    var builder = new ModuleInputFileBuilder(langDetection, metadata);
    var inputFile = builder.create(file);

    assertThat(inputFile.language()).isEqualTo("cpp");
    verifyNoInteractions(langDetection);
  }

  @Test
  void testCreate_lazy_error() throws IOException {
    when(langDetection.language(any(InputFile.class))).thenReturn(Language.JAVA);
    ClientInputFile file = new OnDiskTestClientInputFile(Paths.get("INVALID"), "INVALID", true, StandardCharsets.ISO_8859_1);

    var builder = new ModuleInputFileBuilder(langDetection, metadata);
    var slFile = builder.create(file);

    // Call any method that will trigger metadata initialization
    var thrown = assertThrows(IllegalStateException.class, () -> slFile.selectLine(1));
    assertThat(thrown).hasMessageStartingWith("Failed to open a stream on file");

  }
}
