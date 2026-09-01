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

public class FileUtils {

  /**
   * Converts a file URI (e.g. as sent by an IDE client) to a local {@link Path}, decoding any percent-encoded
   * characters in the process (so a directory literally named {@code c%2B%2B} always means {@code c++}, never a
   * raw {@code %2B%2B}, per the URI spec).
   *
   * @throws IllegalArgumentException if {@code uri} has no hierarchical path component at all (e.g. an opaque
   *                                   URI such as {@code mailto:x@y}). A URI with an authority component (UNC
   *                                   path), a query/fragment component, or raw non-percent-encoded non-ASCII
   *                                   characters does <em>not</em> throw: those are handled by falling back to
   *                                   {@link URI#getPath()}, which decodes percent-encoding but, unlike
   *                                   {@link Path#of(URI)}, silently drops the authority and any query/fragment.
   *                                   This fallback works around JDK-8162518, where {@code Path.of(URI)} throws
   *                                   {@code IllegalArgumentException: Bad escape} for raw non-ASCII characters
   *                                   in a {@code file:///...} URI.
   * @throws java.nio.file.FileSystemNotFoundException if {@code uri}'s scheme is not {@code file} and has no
   *                                                     installed {@link java.nio.file.spi.FileSystemProvider}
   *                                                     (e.g. the {@code temp} scheme used by IntelliJ or the
   *                                                     {@code rse} scheme used by Eclipse Remote System Explorer)
   */
  public static Path getFilePathFromUri(URI uri) {
    try {
      // Path.of(URI) correctly decodes percent-encoded sequences (e.g. "%2B" -> "+"), unlike URI.getPath() below
      return Path.of(uri);
    } catch (IllegalArgumentException e) {
      // Works around JDK-8162518: Path.of(URI) throws "Bad escape" for raw non-ASCII characters in the URI.
      // URI.getPath() decodes percent-encoded sequences while keeping raw non-ASCII characters as-is.
      var decodedPath = uri.getPath();
      if (decodedPath == null) {
        throw e;
      }
      return Path.of(decodedPath);
    }
  }

  private FileUtils() {
    // utility class
  }

}
