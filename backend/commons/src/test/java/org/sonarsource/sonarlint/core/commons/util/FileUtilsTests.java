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
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class FileUtilsTests {

  @ParameterizedTest
  @MethodSource("uris")
  void shouldDecodeUriToFilePath(String uri, String expectedPath) {
    var path = FileUtils.getFilePathFromUri(URI.create(uri));

    assertThat(path).isEqualTo(Path.of(expectedPath));
  }

  private static Stream<Arguments> uris() {
    return Stream.of(
      Arguments.of("file:///home/user/src/c%2B%2B/main.cpp", "/home/user/src/c++/main.cpp"),
      Arguments.of("file:///home/user/my%20project/main.cpp", "/home/user/my project/main.cpp"),
      // JDK-8162518: Path.of(URI) throws "Bad escape" for a hierarchical URI with an empty authority component
      // (e.g. "file:///...") containing raw, non-percent-encoded non-ASCII characters; falls back to URI.getPath()
      Arguments.of("file:///home/user/src/español/main.cpp", "/home/user/src/español/main.cpp"),
      // Same JDK-8162518 fallback, but the path also contains a percent-encoded segment: URI.getPath() must
      // decode "%2B" while leaving the raw "é" alone
      Arguments.of("file:///home/user/josé/c%2B%2B/main.cpp", "/home/user/josé/c++/main.cpp"));
  }
}
