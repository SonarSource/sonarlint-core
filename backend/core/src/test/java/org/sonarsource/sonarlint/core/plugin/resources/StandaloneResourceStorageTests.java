/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.plugin.resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandaloneResourceStorageTests {

  @TempDir
  Path versionDirectory;

  @Test
  void publishes_through_a_temporary_file_and_leaves_only_the_final_path() throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var hash = DigestUtils.sha256Hex(bytes);
    var storage = new StandaloneResourceStorage(versionDirectory);

    var published = storage.store(hash, "native-tool", bytes);

    assertThat(published).isEqualTo(versionDirectory.resolve("resources").resolve(hash).resolve("native-tool"));
    assertThat(Files.readAllBytes(published)).isEqualTo(bytes);
    try (var children = Files.list(published.getParent())) {
      assertThat(children).containsExactly(published);
    }
  }

  @Test
  void does_not_publish_when_the_temporary_file_does_not_match_the_hash() throws IOException {
    var storage = new StandaloneResourceStorage(versionDirectory);

    assertThatThrownBy(() -> storage.store("abc", "native-tool", "nope".getBytes(StandardCharsets.UTF_8)))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("does not match abc");

    assertThat(storage.file("abc", "native-tool")).doesNotExist();
    try (var children = Files.list(versionDirectory.resolve("resources").resolve("abc"))) {
      assertThat(children).isEmpty();
    }
  }

  @Test
  void rejects_a_hash_that_is_not_hexadecimal() {
    var storage = new StandaloneResourceStorage(versionDirectory);

    assertThatThrownBy(() -> storage.file("not-hex", "native-tool"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid static resource hash: not-hex");
  }

  @Test
  void does_not_match_when_the_cached_path_is_a_directory() throws IOException {
    var storage = new StandaloneResourceStorage(versionDirectory);
    var directory = storage.file("abc", "native-tool");
    Files.createDirectories(directory);

    assertThat(storage.matches(directory, "abc")).isFalse();
  }

}
