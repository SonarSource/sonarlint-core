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

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FileUtilsTests {

  @Test
  void deleteRecursively(@TempDir Path dir) throws IOException {
    var fileInDir = createNewFile(dir, "dummy");
    assertThat(fileInDir).isFile();

    FileUtils.deleteRecursively(dir);
    assertThat(fileInDir).doesNotExist();
    assertThat(dir).doesNotExist();
  }

  @Test
  void deleteRecursively_should_ignore_nonexistent_dir(@TempDir Path temp) throws IOException {
    var dir = new File(temp.toFile(), "nonexistent");
    assertThat(dir).doesNotExist();

    FileUtils.deleteRecursively(dir.toPath());
  }

  @Test
  void deleteRecursively_should_delete_file(@TempDir Path temp) throws IOException {
    var file = createNewFile(temp, "foo.txt");
    assertThat(file).isFile();

    FileUtils.deleteRecursively(file.toPath());
    assertThat(file).doesNotExist();
  }

  @Test
  void deleteRecursively_should_delete_deeply_nested_dirs(@TempDir Path basedir) throws IOException {
    var deeplyNestedDir = basedir.resolve("a").resolve("b").resolve("c");
    assertThat(deeplyNestedDir.toFile().isDirectory()).isFalse();
    FileUtils.mkdirs(deeplyNestedDir);

    FileUtils.deleteRecursively(basedir);
    assertThat(basedir.toFile()).doesNotExist();
  }

  @Test
  void mkdirs(@TempDir Path temp) {
    var deeplyNestedDir = temp.resolve("a").resolve("b").resolve("c");
    assertThat(deeplyNestedDir).doesNotExist();
    if (deeplyNestedDir.toFile().mkdir()) {
      throw new IllegalStateException("creating nested dir should have failed");
    }

    FileUtils.mkdirs(deeplyNestedDir);
    assertThat(deeplyNestedDir).isDirectory();
  }

  @Test
  void mkdirs_should_fail_if_destination_is_a_file(@TempDir Path temp) throws IOException {
    var file = createNewFile(temp, "foo").toPath();
    assertThrows(IllegalStateException.class, () -> {
      FileUtils.mkdirs(file);
    });
  }

  @Test
  void always_retry_at_least_once() throws IOException {
    var runnable = mock(FileUtils.IORunnable.class);
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
    var path = basedir.resolve(filename);
    try {
      return Files.createFile(path).toFile();
    } catch (IOException e) {
      fail("could not create file: " + path);
    }
    throw new IllegalStateException("should be unreachable");
  }
}
