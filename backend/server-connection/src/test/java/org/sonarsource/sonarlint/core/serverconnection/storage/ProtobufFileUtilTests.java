/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2024 SonarSource SA
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

import com.google.protobuf.Parser;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtobufFileUtilTests {

  private static final Sonarlint.PluginReferences SOME_MESSAGE = Sonarlint.PluginReferences.newBuilder().build();
  private static final Parser<Sonarlint.PluginReferences> SOME_PARSER = Sonarlint.PluginReferences.parser();

  @Test
  void test_readFile_error() {
    var p = Paths.get("invalid_non_existing_file");
    var thrown = assertThrows(StorageException.class, () -> ProtobufFileUtil.readFile(p, SOME_PARSER));
    assertThat(thrown).hasMessageStartingWith("Failed to read file");
  }

  @Test
  void test_writeFile_error() {
    var p = Paths.get("invalid", "non_existing", "file");
    var thrown = assertThrows(StorageException.class, () -> ProtobufFileUtil.writeToFile(SOME_MESSAGE, p));
    assertThat(thrown).hasMessageStartingWith("Unable to write protocol buffer data to file");
  }
}
