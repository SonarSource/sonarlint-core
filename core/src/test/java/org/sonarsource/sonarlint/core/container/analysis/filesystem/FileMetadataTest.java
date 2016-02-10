/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;

import static org.assertj.core.api.Assertions.assertThat;

public class FileMetadataTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public LogTester logTester = new LogTester();

  @Test
  public void empty_file() throws Exception {
    File tempFile = temp.newFile();
    FileUtils.touch(tempFile);

    FileMetadata.Metadata metadata = new FileMetadata().readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines).isEqualTo(1);
    assertThat(metadata.originalLineOffsets).containsOnly(0);
    assertThat(metadata.lastValidOffset).isEqualTo(0);
  }

  @Test
  public void windows_without_latest_eol() throws Exception {
    File tempFile = temp.newFile();
    FileUtils.write(tempFile, "foo\r\nbar\r\nbaz", StandardCharsets.UTF_8, true);

    FileMetadata.Metadata metadata = new FileMetadata().readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines).isEqualTo(3);
    assertThat(metadata.originalLineOffsets).containsOnly(0, 5, 10);
    assertThat(metadata.lastValidOffset).isEqualTo(13);
  }

  @Test
  public void read_with_wrong_encoding() throws Exception {
    File tempFile = temp.newFile();
    FileUtils.write(tempFile, "marker´s\n", Charset.forName("cp1252"));

    FileMetadata.Metadata metadata = new FileMetadata().readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines).isEqualTo(2);
    assertThat(metadata.originalLineOffsets).containsOnly(0, 9);
  }

  @Test
  public void non_ascii_utf_8() throws Exception {
    File tempFile = temp.newFile();
    FileUtils.write(tempFile, "föo\r\nbàr\r\n\u1D11Ebaßz\r\n", StandardCharsets.UTF_8, true);

    FileMetadata.Metadata metadata = new FileMetadata().readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines).isEqualTo(4);
    assertThat(metadata.originalLineOffsets).containsOnly(0, 5, 10, 18);
  }

  @Test
  public void non_ascii_utf_16() throws Exception {
    File tempFile = temp.newFile();
    FileUtils.write(tempFile, "föo\r\nbàr\r\n\u1D11Ebaßz\r\n", StandardCharsets.UTF_16, true);
    FileMetadata.Metadata metadata = new FileMetadata().readMetadata(tempFile, StandardCharsets.UTF_16);
    assertThat(metadata.lines).isEqualTo(4);
    assertThat(metadata.originalLineOffsets).containsOnly(0, 5, 10, 18);
  }

  @Test
  public void unix_without_latest_eol() throws Exception {
    File tempFile = temp.newFile();
    FileUtils.write(tempFile, "foo\nbar\nbaz", StandardCharsets.UTF_8, true);

    FileMetadata.Metadata metadata = new FileMetadata().readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines).isEqualTo(3);
    assertThat(metadata.originalLineOffsets).containsOnly(0, 4, 8);
    assertThat(metadata.lastValidOffset).isEqualTo(11);
  }

  @Test
  public void unix_with_latest_eol() throws Exception {
    File tempFile = temp.newFile();
    FileUtils.write(tempFile, "foo\nbar\nbaz\n", StandardCharsets.UTF_8, true);

    FileMetadata.Metadata metadata = new FileMetadata().readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines).isEqualTo(4);
    assertThat(metadata.originalLineOffsets).containsOnly(0, 4, 8, 12);
    assertThat(metadata.lastValidOffset).isEqualTo(12);
  }

  @Test
  public void mac_without_latest_eol() throws Exception {
    File tempFile = temp.newFile();
    FileUtils.write(tempFile, "foo\rbar\rbaz", StandardCharsets.UTF_8, true);

    FileMetadata.Metadata metadata = new FileMetadata().readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines).isEqualTo(3);
    assertThat(metadata.originalLineOffsets).containsOnly(0, 4, 8);
    assertThat(metadata.lastValidOffset).isEqualTo(11);
  }

  @Test
  public void mac_with_latest_eol() throws Exception {
    File tempFile = temp.newFile();
    FileUtils.write(tempFile, "foo\rbar\rbaz\r", StandardCharsets.UTF_8, true);

    FileMetadata.Metadata metadata = new FileMetadata().readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines).isEqualTo(4);
    assertThat(metadata.originalLineOffsets).containsOnly(0, 4, 8, 12);
    assertThat(metadata.lastValidOffset).isEqualTo(12);
  }

  @Test
  public void mix_of_newlines_with_latest_eol() throws Exception {
    File tempFile = temp.newFile();
    FileUtils.write(tempFile, "foo\nbar\r\nbaz\n", StandardCharsets.UTF_8, true);

    FileMetadata.Metadata metadata = new FileMetadata().readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines).isEqualTo(4);
    assertThat(metadata.originalLineOffsets).containsOnly(0, 4, 9, 13);
  }

  @Test
  public void several_new_lines() throws Exception {
    File tempFile = temp.newFile();
    FileUtils.write(tempFile, "foo\n\n\nbar", StandardCharsets.UTF_8, true);

    FileMetadata.Metadata metadata = new FileMetadata().readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines).isEqualTo(4);
    assertThat(metadata.originalLineOffsets).containsOnly(0, 4, 5, 6);
  }

  @Test
  public void mix_of_newlines_without_latest_eol() throws Exception {
    File tempFile = temp.newFile();
    FileUtils.write(tempFile, "foo\nbar\r\nbaz", StandardCharsets.UTF_8, true);

    FileMetadata.Metadata metadata = new FileMetadata().readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines).isEqualTo(3);
    assertThat(metadata.originalLineOffsets).containsOnly(0, 4, 9);
  }

  @Test
  public void start_with_newline() throws Exception {
    File tempFile = temp.newFile();
    FileUtils.write(tempFile, "\nfoo\nbar\r\nbaz", StandardCharsets.UTF_8, true);

    FileMetadata.Metadata metadata = new FileMetadata().readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines).isEqualTo(4);
    assertThat(metadata.originalLineOffsets).containsOnly(0, 1, 5, 10);
  }

  @Test
  public void start_with_bom() throws Exception {
    File tempFile = temp.newFile();
    FileUtils.write(tempFile, "\uFEFFfoo\nbar\r\nbaz", StandardCharsets.UTF_8, true);

    FileMetadata.Metadata metadata = new FileMetadata().readMetadata(tempFile, StandardCharsets.UTF_8);
    assertThat(metadata.lines).isEqualTo(3);
    assertThat(metadata.originalLineOffsets).containsOnly(0, 4, 9);
  }

  @Test
  public void should_throw_if_file_does_not_exist() throws Exception {
    File tempFolder = temp.newFolder();
    File file = new File(tempFolder, "doesNotExist.txt");

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Fail to read file '" + file.getAbsolutePath() + "' with encoding 'UTF-8'");

    new FileMetadata().readMetadata(file, StandardCharsets.UTF_8);
  }

  @Test
  public void binary_file_with_unmappable_character() throws Exception {
    File woff = new File(this.getClass().getResource("/glyphicons-halflings-regular.woff").toURI());

    FileMetadata.Metadata metadata = new FileMetadata().readMetadata(woff, StandardCharsets.UTF_8);
    assertThat(metadata.lines).isEqualTo(135);

    assertThat(logTester.logs(LoggerLevel.WARN).get(0)).contains("Invalid character encountered in file");
    assertThat(logTester.logs(LoggerLevel.WARN).get(0)).contains(
      "glyphicons-halflings-regular.woff at line 1 for encoding UTF-8. Please fix file content or configure the encoding to be used using property 'sonar.sourceEncoding'.");
  }

}
