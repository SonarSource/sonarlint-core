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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.fs.InputFile.Status;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import testutils.FileUtils;
import testutils.InMemoryTestClientInputFile;
import testutils.OnDiskTestClientInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SonarLintInputFileTests {

  @Test
  void testGetters(@TempDir Path path) throws IOException {
    var filePath = path.resolve("foo.php");
    Files.write(filePath, "test string".getBytes(StandardCharsets.UTF_8));
    ClientInputFile inputFile = new OnDiskTestClientInputFile(filePath, "file", false, StandardCharsets.UTF_8);
    var file = new SonarLintInputFile(inputFile, f -> new FileMetadata().readMetadata(filePath.toFile(), StandardCharsets.UTF_8));

    assertThat(file.contents()).isEqualTo("test string");
    assertThat(file.charset()).isEqualByComparingTo(StandardCharsets.UTF_8);
    assertThat(file.absolutePath()).isEqualTo(FileUtils.toSonarQubePath(inputFile.getPath()));
    assertThat(file.file()).isEqualTo(filePath.toFile());
    assertThat(file.path()).isEqualTo(filePath);
    assertThat(file.getClientInputFile()).isEqualTo(inputFile);
    assertThat(file.status()).isEqualTo(Status.ADDED);
    assertThat(file)
      .isEqualTo(file)
      .isNotEqualTo(mock(SonarLintInputFile.class));

    var stream = file.inputStream();
    try (var reader = new BufferedReader(new InputStreamReader(stream))) {
      assertThat(reader.lines().collect(Collectors.joining())).isEqualTo("test string");
    }

  }

  @Test
  void checkValidPointer() {
    ClientInputFile inputFile = new InMemoryTestClientInputFile("foo", "src/Foo.php", null, false, null);
    var metadata = new FileMetadata.Metadata(2, new int[] {0, 10}, 16);
    var file = new SonarLintInputFile(inputFile, f -> metadata);
    assertThat(file.newPointer(1, 0).line()).isEqualTo(1);
    assertThat(file.newPointer(1, 0).lineOffset()).isZero();
    // Don't fail
    file.newPointer(1, 9);
    file.newPointer(2, 0);
    file.newPointer(2, 5);
  }

  @Test
  void selectLine() {
    ClientInputFile inputFile = new InMemoryTestClientInputFile("foo", "src/Foo.php", null, false, null);
    var metadata = new FileMetadata().readMetadata(new ByteArrayInputStream("bla bla a\nabcde\n\nabc".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8,
      URI.create("file://foo.php"), null);
    var file = new SonarLintInputFile(inputFile, f -> metadata);

    assertThat(file.selectLine(1).start().line()).isEqualTo(1);
    assertThat(file.selectLine(1).start().lineOffset()).isZero();
    assertThat(file.selectLine(1).end().line()).isEqualTo(1);
    assertThat(file.selectLine(1).end().lineOffset()).isEqualTo(9);

    // Don't fail when selecting empty line
    assertThat(file.selectLine(3).start().line()).isEqualTo(3);
    assertThat(file.selectLine(3).start().lineOffset()).isZero();
    assertThat(file.selectLine(3).end().line()).isEqualTo(3);
    assertThat(file.selectLine(3).end().lineOffset()).isZero();
  }

  @Test
  void testRangeOverlap() {
    ClientInputFile inputFile = new InMemoryTestClientInputFile("foo", "src/Foo.php", null, false, null);
    var metadata = new FileMetadata.Metadata(2, new int[] {0, 10}, 16);
    var file = new SonarLintInputFile(inputFile, f -> metadata);

    // Don't fail
    assertThat(file.newRange(file.newPointer(1, 0), file.newPointer(1, 1)).overlap(file.newRange(file.newPointer(1, 0), file.newPointer(1, 1)))).isTrue();
    assertThat(file.newRange(file.newPointer(1, 0), file.newPointer(1, 1)).overlap(file.newRange(file.newPointer(1, 0), file.newPointer(1, 2)))).isTrue();
    assertThat(file.newRange(file.newPointer(1, 0), file.newPointer(1, 1)).overlap(file.newRange(file.newPointer(1, 1), file.newPointer(1, 2)))).isFalse();
    assertThat(file.newRange(file.newPointer(1, 2), file.newPointer(1, 3)).overlap(file.newRange(file.newPointer(1, 0), file.newPointer(1, 2)))).isFalse();
  }
}
