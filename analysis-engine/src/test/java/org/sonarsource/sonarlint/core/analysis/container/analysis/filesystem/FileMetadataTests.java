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

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileMetadataTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private final FileMetadata underTest = new FileMetadata();

  @Test
  void empty_file(@TempDir Path temp) throws Exception {
    var tempFile = temp.resolve("tmpFile");
    Files.createFile(tempFile);

    var metadata = underTest.readMetadata(tempFile.toFile(), StandardCharsets.UTF_8);
    assertThat(metadata.lines()).isEqualTo(1);
    assertThat(metadata.originalLineOffsets()).containsOnly(0);
    assertThat(metadata.lastValidOffset()).isZero();
  }

  @Test
  void windows_without_latest_eol(@TempDir Path temp) throws Exception {
    var tempFile = temp.resolve("tmpFile").toFile();
    FileUtils.write(tempFile, "foo\r\nbar\r\nbaz", StandardCharsets.UTF_8, true);

    var metadata = underTest.readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines()).isEqualTo(3);
    assertThat(metadata.originalLineOffsets()).containsOnly(0, 5, 10);
    assertThat(metadata.lastValidOffset()).isEqualTo(13);
  }

  @Test
  void read_with_wrong_encoding(@TempDir Path temp) throws Exception {
    var tempFile = temp.resolve("tmpFile").toFile();
    FileUtils.write(tempFile, "marker´s\n", Charset.forName("cp1252"));

    var metadata = underTest.readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines()).isEqualTo(2);
    assertThat(metadata.originalLineOffsets()).containsOnly(0, 9);
  }

  @Test
  void non_ascii_utf_8(@TempDir Path temp) throws Exception {
    var tempFile = temp.resolve("tmpFile").toFile();
    FileUtils.write(tempFile, "föo\r\nbàr\r\n\u1D11Ebaßz\r\n", StandardCharsets.UTF_8, true);

    var metadata = underTest.readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines()).isEqualTo(4);
    assertThat(metadata.originalLineOffsets()).containsOnly(0, 5, 10, 18);
  }

  @Test
  void non_ascii_utf_16(@TempDir Path temp) throws Exception {
    var tempFile = temp.resolve("tmpFile").toFile();
    FileUtils.write(tempFile, "föo\r\nbàr\r\n\u1D11Ebaßz\r\n", StandardCharsets.UTF_16, true);
    var metadata = underTest.readMetadata(tempFile, StandardCharsets.UTF_16);
    assertThat(metadata.lines()).isEqualTo(4);
    assertThat(metadata.originalLineOffsets()).containsOnly(0, 5, 10, 18);
  }

  @Test
  void unix_without_latest_eol(@TempDir Path temp) throws Exception {
    var tempFile = temp.resolve("tmpFile").toFile();
    FileUtils.write(tempFile, "foo\nbar\nbaz", StandardCharsets.UTF_8, true);

    var metadata = underTest.readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines()).isEqualTo(3);
    assertThat(metadata.originalLineOffsets()).containsOnly(0, 4, 8);
    assertThat(metadata.lastValidOffset()).isEqualTo(11);
  }

  @Test
  void unix_with_latest_eol(@TempDir Path temp) throws Exception {
    var tempFile = temp.resolve("tmpFile").toFile();
    FileUtils.write(tempFile, "foo\nbar\nbaz\n", StandardCharsets.UTF_8, true);

    var metadata = underTest.readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines()).isEqualTo(4);
    assertThat(metadata.originalLineOffsets()).containsOnly(0, 4, 8, 12);
    assertThat(metadata.lastValidOffset()).isEqualTo(12);
  }

  @Test
  void mac_without_latest_eol(@TempDir Path temp) throws Exception {
    var tempFile = temp.resolve("tmpFile").toFile();
    FileUtils.write(tempFile, "foo\rbar\rbaz", StandardCharsets.UTF_8, true);

    var metadata = underTest.readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines()).isEqualTo(3);
    assertThat(metadata.originalLineOffsets()).containsOnly(0, 4, 8);
    assertThat(metadata.lastValidOffset()).isEqualTo(11);
  }

  @Test
  void mac_with_latest_eol(@TempDir Path temp) throws Exception {
    var tempFile = temp.resolve("tmpFile").toFile();
    FileUtils.write(tempFile, "foo\rbar\rbaz\r", StandardCharsets.UTF_8, true);

    var metadata = underTest.readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines()).isEqualTo(4);
    assertThat(metadata.originalLineOffsets()).containsOnly(0, 4, 8, 12);
    assertThat(metadata.lastValidOffset()).isEqualTo(12);
  }

  @Test
  void mix_of_newlines_with_latest_eol(@TempDir Path temp) throws Exception {
    var tempFile = temp.resolve("tmpFile").toFile();
    FileUtils.write(tempFile, "foo\nbar\r\nbaz\n", StandardCharsets.UTF_8, true);

    var metadata = underTest.readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines()).isEqualTo(4);
    assertThat(metadata.originalLineOffsets()).containsOnly(0, 4, 9, 13);
  }

  @Test
  void several_new_lines(@TempDir Path temp) throws Exception {
    var tempFile = temp.resolve("tmpFile").toFile();
    FileUtils.write(tempFile, "foo\n\n\nbar", StandardCharsets.UTF_8, true);

    var metadata = underTest.readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines()).isEqualTo(4);
    assertThat(metadata.originalLineOffsets()).containsOnly(0, 4, 5, 6);
  }

  @Test
  void mix_of_newlines_without_latest_eol(@TempDir Path temp) throws Exception {
    var tempFile = temp.resolve("tmpFile").toFile();
    FileUtils.write(tempFile, "foo\nbar\r\nbaz", StandardCharsets.UTF_8, true);

    var metadata = underTest.readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines()).isEqualTo(3);
    assertThat(metadata.originalLineOffsets()).containsOnly(0, 4, 9);
  }

  @Test
  void start_with_newline(@TempDir Path temp) throws Exception {
    var tempFile = temp.resolve("tmpFile").toFile();
    FileUtils.write(tempFile, "\nfoo\nbar\r\nbaz", StandardCharsets.UTF_8, true);

    var metadata = underTest.readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines()).isEqualTo(4);
    assertThat(metadata.originalLineOffsets()).containsOnly(0, 1, 5, 10);
  }

  @Test
  void start_with_bom(@TempDir Path temp) throws Exception {
    var tempFile = temp.resolve("tmpFile").toFile();
    FileUtils.write(tempFile, "\uFEFFfoo\nbar\r\nbaz", StandardCharsets.UTF_8, true);

    var metadata = underTest.readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines()).isEqualTo(3);
    assertThat(metadata.originalLineOffsets()).containsOnly(0, 4, 9);
  }

  @Test
  void should_throw_if_file_does_not_exist(@TempDir Path temp) {
    var tempFolder = temp.toFile();
    var file = new File(tempFolder, "doesNotExist.txt");

    assertThrows(IllegalStateException.class, () -> underTest.readMetadata(file, StandardCharsets.UTF_8));
  }

  @Test
  void binary_file_with_unmappable_character() throws Exception {
    var woff = new File(this.getClass().getResource("/glyphicons-halflings-regular.woff").toURI());

    var metadata = underTest.readMetadata(woff, StandardCharsets.UTF_8);
    assertThat(metadata.lines()).isEqualTo(135);

    assertThat(logTester.logs(Level.WARN).get(0)).contains("Invalid character encountered in file");
    assertThat(logTester.logs(Level.WARN).get(0)).contains(
      "glyphicons-halflings-regular.woff' at line 1 for encoding UTF-8. Please fix file content or configure the encoding.");
  }

}
