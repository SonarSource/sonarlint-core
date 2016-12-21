package org.sonarsource.sonarlint.core.client.api.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

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
    assertThat(oldDir.isDirectory()).isFalse();
    assertThat(newDir.isDirectory()).isTrue();
  }

  @Test
  public void moveDir_should_replace_empty_destination() throws IOException {
    File oldDir = temporaryFolder.newFolder();
    assertThat(oldDir.isDirectory()).isTrue();

    File fileInDir = createNewFile(oldDir, "dummy");
    assertThat(fileInDir.isFile()).isTrue();

    File newDir = temporaryFolder.newFolder();
    assertThat(newDir.isDirectory()).isTrue();

    FileUtils.moveDir(oldDir.toPath(), newDir.toPath());
    assertThat(oldDir.isDirectory()).isFalse();
    assertThat(newDir.isDirectory()).isTrue();
    assertThat(new File(newDir, fileInDir.getName()).isFile()).isTrue();
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

  @Test(expected = IllegalStateException.class)
  public void moveDir_should_fail_to_move_if_destination_is_a_file() throws IOException {
    File oldDir = temporaryFolder.newFolder();
    assertThat(oldDir.isDirectory()).isTrue();

    File target = temporaryFolder.newFile();
    assertThat(target.isFile()).isTrue();

    FileUtils.moveDir(oldDir.toPath(), target.toPath());
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
