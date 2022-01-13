/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static java.nio.file.Files.createDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileUtilsTests {

  @Test
  void moveDir_should_rename_a_dir(@TempDir Path temp) throws IOException {
    Path oldDir = createDirectory(temp.resolve("oldDir"));
    assertThat(oldDir.toFile().isDirectory()).isTrue();

    Path newDir = temp.resolve("new dir");
    assertThat(newDir.toFile().isDirectory()).isFalse();

    FileUtils.moveDir(oldDir, newDir);
    assertThat(oldDir).doesNotExist();
    assertThat(newDir).exists().isDirectory();
  }

  @Test
  void moveDir_non_atomic(@TempDir Path temp) throws IOException {
    Path oldDir = createDirectory(temp.resolve("oldDir"));
    Path foo = oldDir.resolve("foo");
    Files.createDirectories(foo);
    new File(foo.toFile(), "foo.txt").createNewFile();

    Path destPath = createDirectory(temp.resolve("newDir"));

    Path destMock = mock(Path.class, RETURNS_DEEP_STUBS);
    // Make the dest path look like it doesn't belong to the same FileSystem
    when(destMock.getFileSystem().provider()).thenReturn(null);
    when(destMock.resolve(any(Path.class))).thenAnswer(new Answer<Path>() {
      @Override
      public Path answer(InvocationOnMock invocation) throws Throwable {
        return destPath.resolve(invocation.<Path>getArgument(0));
      };
    });

    FileUtils.moveDir(oldDir, destMock);
    assertThat(oldDir).doesNotExist();
    assertThat(new File(destPath.toFile(), "foo/foo.txt")).exists();
  }

  @Test
  void moveDir_should_fail_to_replace_nonempty_destination(@TempDir Path temp) throws IOException {
    Path oldDir = createDirectory(temp.resolve("oldDir"));
    assertThat(oldDir.toFile()).isDirectory();

    Path newDir = createDirectory(temp.resolve("newDir"));
    assertThat(newDir.toFile()).isDirectory();

    File fileInDir = createNewFile(newDir, "dummy");
    assertThat(fileInDir).isFile();

    assertThrows(IllegalStateException.class, () -> {
      FileUtils.moveDir(oldDir, newDir);
    });
  }

  @Test
  void deleteRecursively(@TempDir Path dir) throws IOException {
    File fileInDir = createNewFile(dir, "dummy");
    assertThat(fileInDir).isFile();

    FileUtils.deleteRecursively(dir);
    assertThat(fileInDir).doesNotExist();
    assertThat(dir).doesNotExist();
  }

  @Test
  void deleteRecursively_should_ignore_nonexistent_dir(@TempDir Path temp) throws IOException {
    File dir = new File(temp.toFile(), "nonexistent");
    assertThat(dir).doesNotExist();

    FileUtils.deleteRecursively(dir.toPath());
  }

  @Test
  void deleteRecursively_should_delete_file(@TempDir Path temp) throws IOException {
    File file = createNewFile(temp, "foo.txt");
    assertThat(file.isFile()).isTrue();

    FileUtils.deleteRecursively(file.toPath());
    assertThat(file).doesNotExist();
  }

  @Test
  void deleteRecursively_should_delete_deeply_nested_dirs(@TempDir Path basedir) throws IOException {
    Path deeplyNestedDir = basedir.resolve("a").resolve("b").resolve("c");
    assertThat(deeplyNestedDir.toFile().isDirectory()).isFalse();
    FileUtils.mkdirs(deeplyNestedDir);

    FileUtils.deleteRecursively(basedir);
    assertThat(basedir.toFile().exists()).isFalse();
  }

  @Test
  void allRelativePathsForFilesInTree_should_find_all_files(@TempDir Path basedir) {
    Path deeplyNestedDir = basedir.resolve("a").resolve("b").resolve("c");
    assertThat(deeplyNestedDir.toFile().isDirectory()).isFalse();
    FileUtils.mkdirs(deeplyNestedDir);
    FileUtils.mkdirs(basedir.resolve(".git").resolve("refs"));
    FileUtils.mkdirs(basedir.resolve("a").resolve(".config"));

    createNewFile(basedir, ".gitignore");
    createNewFile(basedir.resolve(".git/refs"), "HEAD");
    createNewFile(basedir.resolve("a"), "a.txt");
    createNewFile(basedir.resolve("a/.config"), "test");
    createNewFile(basedir.resolve("a/b"), "b.txt");
    createNewFile(basedir.resolve("a/b/c"), "c.txt");

    Collection<String> relativePaths = FileUtils.allRelativePathsForFilesInTree(basedir);
    assertThat(relativePaths).containsExactlyInAnyOrder(
      "a/a.txt",
      "a/b/b.txt",
      "a/b/c/c.txt");
  }

  @Test
  void allRelativePathsForFilesInTree_should_handle_non_existing_dir(@TempDir Path basedir) {
    Path deeplyNestedDir = basedir.resolve("a").resolve("b").resolve("c");
    assertThat(deeplyNestedDir).doesNotExist();

    Collection<String> relativePaths = FileUtils.allRelativePathsForFilesInTree(deeplyNestedDir);
    assertThat(relativePaths).isEmpty();
  }

  @Test
  void mkdirs(@TempDir Path temp) {
    Path deeplyNestedDir = temp.resolve("a").resolve("b").resolve("c");
    assertThat(deeplyNestedDir).doesNotExist();
    if (deeplyNestedDir.toFile().mkdir()) {
      throw new IllegalStateException("creating nested dir should have failed");
    }

    FileUtils.mkdirs(deeplyNestedDir);
    assertThat(deeplyNestedDir).isDirectory();
  }

  @Test
  void mkdirs_should_fail_if_destination_is_a_file(@TempDir Path temp) throws IOException {
    Path file = createNewFile(temp, "foo").toPath();
    assertThrows(IllegalStateException.class, () -> {
      FileUtils.mkdirs(file);
    });
  }

  @Test
  void toSonarQubePath_should_return_slash_separated_path() {
    Path path = Paths.get("some").resolve("relative").resolve("path");
    assertThat(FileUtils.toSonarQubePath(path.toString())).isEqualTo("some/relative/path");
  }

  @Test
  void replaceDir_should_replace_content_of_dir(@TempDir Path temp) throws IOException {
    Path oldDir = createDirectory(temp.resolve("oldDir"));
    assertThat(oldDir).isDirectory();

    File oldFileInDir = createNewFile(oldDir, "dummy");
    assertThat(oldFileInDir).isFile();

    File newFileInDir = new File(oldDir.toFile(), "new file");
    assertThat(newFileInDir).doesNotExist();
    FileUtils.replaceDir(tmp -> createNewFile(tmp, newFileInDir.getName()), oldDir, Files.createTempDirectory(temp, "workDir"));

    assertThat(oldFileInDir).doesNotExist();
    assertThat(newFileInDir).isFile();
  }

  @Test
  void always_retry_at_least_once() throws IOException {
    FileUtils.IORunnable runnable = mock(FileUtils.IORunnable.class);
    FileUtils.retry(runnable, 0);
    verify(runnable, times(1)).run();
  }

  @Test
  void retry_on_failure() throws IOException {
    int[] count = {0};
    FileUtils.IORunnable throwOnce = () -> {
      count[0]++;
      if (count[0] == 1) {
        throw new AccessDeniedException("foo");
      }
    };
    FileUtils.retry(throwOnce, 10);
    assertThat(count[0]).isEqualTo(2);
  }

  private File createNewFile(Path basedir, String filename) {
    Path path = basedir.resolve(filename);
    try {
      return Files.createFile(path).toFile();
    } catch (IOException e) {
      fail("could not create file: " + path);
    }
    throw new IllegalStateException("should be unreachable");
  }
}
