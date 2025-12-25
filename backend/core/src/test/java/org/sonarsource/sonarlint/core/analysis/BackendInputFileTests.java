/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.analysis;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.fs.ReadThroughFileCache;
import org.sonarsource.sonarlint.core.fs.ClientFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BackendInputFileTests {

  private final ReadThroughFileCache cache = mock(ReadThroughFileCache.class);

  @Test
  void ascii_path_should_be_the_same() {
    var path = Path.of("/test/file.php");
    var pathAsString = path.toString().replace(File.separatorChar, '/');
    var clientFile = new ClientFile(URI.create("file://" + pathAsString), "configScopeId", path, false, null, null, null, true, cache);

    var inputFile = new BackendInputFile(clientFile);

    assertThat(inputFile.getPath().replace(File.separatorChar, '/')).endsWith(pathAsString);
  }

  @Test
  void non_ascii_path_should_be_the_same() {
    var path = Path.of("/中文字符/file.php");
    var pathAsString = path.toString().replace(File.separatorChar, '/');
    var clientFile = new ClientFile(URI.create("file://" + pathAsString), "configScopeId", path, false, null, null, null, true, cache);

    var inputFile = new BackendInputFile(clientFile);

    assertThat(inputFile.getPath().replace(File.separatorChar, '/')).endsWith(pathAsString);
  }

}
