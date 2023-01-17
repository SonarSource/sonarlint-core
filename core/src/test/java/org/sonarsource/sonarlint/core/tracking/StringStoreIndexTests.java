/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.tracking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;

// note: most methods of the subject are already tested by higher level uses
class StringStoreIndexTests {

  @Test
  void should_throw_if_cannot_read_from_index_file(@TempDir Path storeBasePath) throws IOException {
    var indexFileName = "index.pb";
    var indexFilePath = storeBasePath.resolve(indexFileName);

    StoreIndex<String> index = new StringStoreIndex(storeBasePath, indexFileName);
    Files.write(indexFilePath, "garbage index data".getBytes());

    assertThrows(IllegalStateException.class, () -> index.keys());
  }
}
