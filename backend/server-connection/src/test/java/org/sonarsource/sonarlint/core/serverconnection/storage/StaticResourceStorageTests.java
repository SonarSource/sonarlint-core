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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.serverapi.plugins.StaticResource;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.assertj.core.api.Assertions.assertThat;

class StaticResourceStorageTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path storageRoot;

  private StaticResourceStorage underTest;

  @BeforeEach
  void setUp() {
    underTest = new StaticResourceStorage(storageRoot);
  }

  @Test
  void deletes_hash_directories_missing_from_the_inventory() throws IOException {
    var kept = write("aa", "native-tool", "keep");
    var removed = write("bb", "native-tool", "remove");
    var shared = write("cc", "native-tool", "shared");
    var stray = storageRoot.resolve("resources").resolve("notes.txt");
    Files.writeString(stray, "stray");

    underTest.purgeExcept(List.of(
      plugin("javascript", "AA"),
      plugin("python", "cc"),
      plugin("kotlin", "cc")));

    assertThat(kept).hasContent("keep");
    assertThat(shared).hasContent("shared");
    assertThat(removed).doesNotExist();
    assertThat(removed.getParent()).doesNotExist();
    assertThat(stray).hasContent("stray");
  }

  @Test
  void does_nothing_when_there_is_no_resource_cache() {
    underTest.purgeExcept(List.of());

    assertThat(storageRoot.resolve("resources")).doesNotExist();
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void leaves_a_directory_in_place_when_a_file_cannot_be_deleted() throws IOException {
    var locked = write("bb", "native-tool", "locked");
    var directory = locked.getParent();
    Files.setPosixFilePermissions(directory, Set.of(OWNER_READ, OWNER_EXECUTE));

    try {
      underTest.purgeExcept(List.of());

      assertThat(locked).hasContent("locked");
      assertThat(directory).exists();
    } finally {
      Files.setPosixFilePermissions(directory, Set.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE));
    }
  }

  private Path write(String hash, String filename, String content) throws IOException {
    var file = storageRoot.resolve("resources").resolve(hash).resolve(filename);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
    return file;
  }

  private static StoredPlugin plugin(String key, String hash) {
    return new StoredPlugin(key, "jar", Path.of(key + ".jar"), "folder", List.of(new StaticResource("linux-x64/native-tool", hash, 1)));
  }

}
