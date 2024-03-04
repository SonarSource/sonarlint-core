/*
 * SonarLint Core - ITs - Tests
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
package its.utils;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class InputFileFinder {
  private final PathMatcher srcMatcher;

  private static PathMatcher acceptAll = new PathMatcher() {
    @Override
    public boolean matches(Path path) {
      return true;
    }
  };

  public InputFileFinder(@Nullable String srcGlobPattern) {
    var fs = FileSystems.getDefault();
    try {
      if (srcGlobPattern != null) {
        srcMatcher = fs.getPathMatcher("glob:" + srcGlobPattern);
      } else {
        srcMatcher = acceptAll;
      }
    } catch (Exception e) {
      throw e;
    }
  }

  public List<Path> collect(Path dir) throws IOException {
    final List<Path> files = new ArrayList<>();
    Files.walkFileTree(dir, new FileCollector(files));
    return files;
  }

  private class FileCollector extends SimpleFileVisitor<Path> {
    private List<Path> files;

    private FileCollector(List<Path> files) {
      this.files = files;
    }

    @Override
    public FileVisitResult visitFile(final Path file, BasicFileAttributes attrs) throws IOException {
      var isSrc = srcMatcher.matches(file);

      if (isSrc) {
        files.add(file);
      }

      return super.visitFile(file, attrs);
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
      if (Files.isHidden(dir)) {
        return FileVisitResult.SKIP_SUBTREE;
      }

      return super.preVisitDirectory(dir, attrs);
    }
  }
}
