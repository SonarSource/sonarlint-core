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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FileUtilsTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void moveDir_should_rename_a_dir() throws IOException {
    File oldDir = temporaryFolder.newFolder();
    assertThat(oldDir.isDirectory()).isTrue();

    File newDir = new File(temporaryFolder.getRoot(), "new dir");
    assertThat(newDir.isDirectory()).isFalse();

    FileUtils.moveDir(oldDir.toPath(), newDir.toPath());
    assertThat(oldDir).doesNotExist();
    assertThat(newDir).exists().isDirectory();
  }

  @Test
  public void moveDir_non_atomic() throws IOException {
    File oldDir = temporaryFolder.newFolder();
    File foo = new File(oldDir, "foo");
    foo.mkdirs();
    new File(foo, "foo.txt").createNewFile();

    File newDir = temporaryFolder.newFolder();

    Path destPath = newDir.toPath();

    Path destMock = mock(Path.class, RETURNS_DEEP_STUBS);
    // Make the dest path look like it doesn't belong to the same FileSystem
    when(destMock.getFileSystem().provider()).thenReturn(null);
    when(destMock.resolve(any(Path.class))).thenAnswer(new Answer<Path>() {
      @Override
      public Path answer(InvocationOnMock invocation) throws Throwable {
        return destPath.resolve(invocation.<Path>getArgument(0));
      };
    });

    FileUtils.moveDir(oldDir.toPath(), destMock);
    assertThat(oldDir).doesNotExist();
    assertThat(new File(newDir, "foo/foo.txt")).exists();
  }

  @Test(expected = IllegalStateException.class)
  public void moveDir_should_fail_to_replace_nonempty_destination() throws IOException {
    File oldDir = temporaryFolder.newFolder();
    assertThat(oldDir.isDirectory()).isTrue();

    File newDir = temporaryFolder.newFolder();
    assertThat(newDir.isDirectory()).isTrue();

    File fileInDir = createNewFile(newDir, "dummy");
    assertThat(fileInDir.isFile()).isTrue();

    FileUtils.moveDir(oldDir.toPath(), newDir.toPath());
  }

  @Test
  public void deleteRecursively() throws IOException {
    File dir = temporaryFolder.newFolder();
    assertThat(dir.isDirectory()).isTrue();

    File fileInDir = createNewFile(dir, "dummy");
    assertThat(fileInDir.isFile()).isTrue();

    FileUtils.deleteRecursively(dir.toPath());
    assertThat(dir.isDirectory()).isFalse();
    assertThat(fileInDir.isFile()).isFalse();
  }

  @Test
  public void deleteRecursively_should_ignore_nonexistent_dir() throws IOException {
    File dir = new File(temporaryFolder.newFolder(), "nonexistent");
    assertThat(dir.exists()).isFalse();

    FileUtils.deleteRecursively(dir.toPath());
  }

  @Test
  public void deleteRecursively_should_delete_file() throws IOException {
    File file = temporaryFolder.newFile();
    assertThat(file.isFile()).isTrue();

    FileUtils.deleteRecursively(file.toPath());
    assertThat(file.exists()).isFalse();
  }

  @Test
  public void deleteRecursively_should_delete_deeply_nested_dirs() throws IOException {
    Path basedir = temporaryFolder.getRoot().toPath();
    Path deeplyNestedDir = basedir.resolve("a").resolve("b").resolve("c");
    assertThat(deeplyNestedDir.toFile().isDirectory()).isFalse();
    FileUtils.mkdirs(deeplyNestedDir);

    FileUtils.deleteRecursively(basedir);
    assertThat(basedir.toFile().exists()).isFalse();
  }

  @Test
  public void mkdirs() {
    Path deeplyNestedDir = temporaryFolder.getRoot().toPath().resolve("a").resolve("b").resolve("c");
    assertThat(deeplyNestedDir.toFile().isDirectory()).isFalse();
    if (deeplyNestedDir.toFile().mkdir()) {
      throw new IllegalStateException("creating nested dir should have failed");
    }

    FileUtils.mkdirs(deeplyNestedDir);
    assertThat(deeplyNestedDir.toFile().isDirectory()).isTrue();
  }

  @Test(expected = IllegalStateException.class)
  public void mkdirs_should_fail_if_destination_is_a_file() throws IOException {
    File file = temporaryFolder.newFile();
    FileUtils.mkdirs(file.toPath());
  }

  @Test
  public void toSonarQubePath_should_return_slash_separated_path() {
    Path path = Paths.get("some").resolve("relative").resolve("path");
    assertThat(FileUtils.toSonarQubePath(path.toString())).isEqualTo("some/relative/path");
  }

  @Test
  public void replaceDir_should_replace_content_of_dir() throws IOException {
    File oldDir = temporaryFolder.newFolder();
    assertThat(oldDir.isDirectory()).isTrue();

    File oldFileInDir = createNewFile(oldDir, "dummy");
    assertThat(oldFileInDir.isFile()).isTrue();

    File newFileInDir = new File(oldDir, "new file");
    assertThat(newFileInDir.isFile()).isFalse();
    FileUtils.replaceDir(temp -> createNewFile(temp.toFile(), newFileInDir.getName()), oldDir.toPath(), temporaryFolder.newFolder().toPath());

    assertThat(oldFileInDir.isFile()).isFalse();
    assertThat(newFileInDir.isFile()).isTrue();
  }

  private File createNewFile(File basedir, String filename) {
    Path path = basedir.toPath().resolve(filename);
    try {
      return Files.createFile(path).toFile();
    } catch (IOException e) {
      fail("could not create file: " + path);
    }
    throw new IllegalStateException("should be unreachable");
  }
}
