/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.util;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileUtilsTests {

  @Test
  void shouldDecodePercentEncodedPlusSignsInUri() {
    var uri = URI.create("file:///home/user/src/c%2B%2B/main.cpp");

    var path = FileUtils.getFilePathFromUri(uri);

    assertThat(path).isEqualTo(Path.of("/home/user/src/c++/main.cpp"));
  }

  @Test
  void shouldDecodePercentEncodedSpacesInUri() {
    var uri = URI.create("file:///home/user/my%20project/main.cpp");

    var path = FileUtils.getFilePathFromUri(uri);

    assertThat(path).isEqualTo(Path.of("/home/user/my project/main.cpp"));
  }

  @Test
  void shouldFallBackToUrlPathForUriWithRawNonAsciiCharacters() {
    // JDK-8162518: Path.of(URI) throws "Bad escape" for a hierarchical URI with an empty authority
    // component (e.g. "file:///...") containing raw, non-percent-encoded non-ASCII characters
    var uri = URI.create("file:///home/user/src/español/main.cpp");

    var path = FileUtils.getFilePathFromUri(uri);

    assertThat(path).isEqualTo(Path.of("/home/user/src/español/main.cpp"));
  }
}
