/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths;

import static org.apache.commons.lang3.StringUtils.repeat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

class ProjectStoragePathsTests {

  @Test
  void encode_paths_for_fs() {
    assertThat(encodeForFs("my/string%to encode**")).isEqualTo("6d792f737472696e6725746f20656e636f64652a2a");
    assertThat(encodeForFs("AU-TpxcA-iU5OvuD2FLz").toLowerCase()).isNotEqualTo(encodeForFs("AU-TpxcA-iU5OvuD2FLZ"));
    assertThat(encodeForFs("too_long_for_most_fs" + repeat("a", 1000))).hasSize(255);
    assertThat(encodeForFs("too_long_for_most_fs" + repeat("a", 1000)))
      .isNotEqualTo(encodeForFs("too_long_for_most_fs" + repeat("a", 1000) + "2"));
  }

  @Test
  void encodeProjectKeyForFs(@TempDir Path connectionStorageRoot) {
    var manager = new ProjectStoragePaths(connectionStorageRoot);

    var moduleStorageRoot = manager.getProjectStorageRoot("module.:key/with_branch%");
    assertThat(moduleStorageRoot)
      .isEqualTo(connectionStorageRoot.resolve("6d6f64756c652e3a6b65792f776974685f6272616e636825"));
  }

  @Test
  void paths(@TempDir Path connectionStorageRoot) {
    var manager = new ProjectStoragePaths(connectionStorageRoot);

    assertThat(manager.getComponentListPath("project")).isEqualTo(connectionStorageRoot
      .resolve("70726f6a656374")
      .resolve("component_list.pb"));
  }

}
