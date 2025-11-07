/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.fs;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientFileTests {

  @Test
  void dirty_file_larger_than_threshold_returns_true() throws Exception {
    var uri = URI.create("file:///dirty.js");
    var clientFile = new ClientFile(uri, "scope", Paths.get("dirty.js"), null, StandardCharsets.UTF_8, null, null, true);

    var content = "x".repeat(2048);
    clientFile.setDirty(content);

    assertThat(clientFile.isLargerThan(1024)).isTrue();
    assertThat(clientFile.isLargerThan(4096)).isFalse();
  }

  @Test
  void clean_local_file_uses_files_size_and_non_local_returns_false() throws Exception {
    var tempFile = Files.createTempFile("sl-clientfile-size", ".txt");
    Files.write(tempFile, new byte[4096]);

    var localUri = tempFile.toUri();
    var localClientFile = new ClientFile(localUri, "scope", Paths.get("local.txt"), null, StandardCharsets.UTF_8, tempFile, null, true);

    var missingPath = tempFile.getParent().resolve("missing.txt");
    var nonLocalUri = missingPath.toUri();
    var nonLocalClientFile = new ClientFile(nonLocalUri, "scope", Paths.get("missing.txt"), null, StandardCharsets.UTF_8, null, null, true);

    assertThat(localClientFile.isLargerThan(1024)).isTrue();
    assertThat(localClientFile.isLargerThan(8192)).isFalse();
    assertThat(nonLocalClientFile.isLargerThan(1)).isFalse();
  }

}


