/*
 * SonarLint Core - Server Connection
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipUtilsTests {

  @TempDir
  Path directory;

  @Test
  void extracts_a_directory_entry(@TempDir Path destination) throws IOException {
    var zip = zip(new ZipEntry("nested/"));

    ZipUtils.extractZip(zip, destination);

    assertThat(destination.resolve("nested")).isDirectory();
  }

  @Test
  void rejects_an_entry_that_escapes_the_destination(@TempDir Path destination) throws IOException {
    var zip = zip(new ZipEntry("../outside.txt"));

    assertThatThrownBy(() -> ZipUtils.extractZip(zip, destination))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("outside of the target dir");
  }

  private Path zip(ZipEntry entry) throws IOException {
    var file = directory.resolve("archive.zip");
    try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
      zip.putNextEntry(entry);
      if (!entry.isDirectory()) {
        zip.write("nope".getBytes(StandardCharsets.UTF_8));
      }
      zip.closeEntry();
    }
    return file;
  }

}
