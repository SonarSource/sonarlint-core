/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class FileUtils {

  private static final String PATH_SEPARATOR_PATTERN = Pattern.quote(File.separator);

  private FileUtils() {
    // utility class, forbidden constructor
  }

  public static void moveDir(Path src, Path dest) {
    try {
      moveDirPreferAtomic(src, dest);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to move " + src + " to " + dest, e);
    }
  }

  private static void moveDirPreferAtomic(Path src, Path dest) throws IOException {
    try {
      Files.move(src, dest, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      // Fallback to non atomic move
      Files.walkFileTree(src, new CopyRecursivelyVisitor(src, dest));
      deleteRecursively(src);
    }
  }

  private static class CopyRecursivelyVisitor extends SimpleFileVisitor<Path> {
    private final Path fromPath;
    private final Path toPath;
    private final CopyOption[] copyOptions;

    public CopyRecursivelyVisitor(Path fromPath, Path toPath, CopyOption... copyOptions) {
      this.fromPath = fromPath;
      this.toPath = toPath;
      this.copyOptions = copyOptions;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
      Path targetPath = toPath.resolve(fromPath.relativize(dir));
      if (!Files.exists(targetPath)) {
        Files.createDirectory(targetPath);
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      Files.copy(file, toPath.resolve(fromPath.relativize(file)), copyOptions);
      return FileVisitResult.CONTINUE;
    }
  }

  /**
   * Deletes recursively the specified file or directory tree.
   *
   * @param path
   */
  public static void deleteRecursively(Path path) {
    if (!path.toFile().exists()) {
      return;
    }
    try {
      Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new IllegalStateException("Unable to delete directory " + path, e);
    }
  }

  /**
   * Creates a directory by creating all nonexistent parent directories first.
   *
   * @param path the directory to create
   */
  public static void mkdirs(Path path) {
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create directory: " + path, e);
    }
  }

  /**
   * Converts path to format used by SonarQube
   *
   * @param path path string in the local OS
   * @return SonarQube path
   */
  public static String toSonarQubePath(String path) {
    if (File.separatorChar != '/') {
      return path.replaceAll(PATH_SEPARATOR_PATTERN, "/");
    }
    return path;
  }

  /**
   * Populates a new temporary directory and when done, replace the target directory with it.
   *
   * @param dirContentUpdater function that will be called to create new content
   * @param target target location to replace when content is ready
   * @param work directory to populate with new content (typically a new empty temporary directory)
   */
  public static void replaceDir(Consumer<Path> dirContentUpdater, Path target, Path work) {
    dirContentUpdater.accept(work);
    FileUtils.deleteRecursively(target);
    FileUtils.mkdirs(target.getParent());
    FileUtils.moveDir(work, target);
  }
}
