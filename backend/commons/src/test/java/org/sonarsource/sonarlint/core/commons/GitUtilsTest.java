package org.sonarsource.sonarlint.core.commons;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.addFileToGitIgnore;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createFile;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createRepository;

class GitUtilsTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  private static Path baseDir;

  private Git git;
  private Path gitDirPath;

  @BeforeEach
  public void prepare() throws Exception {
    gitDirPath = baseDir.resolve("gitDir");
    git = createRepository(gitDirPath);
  }

  @AfterEach
  public void cleanup() {
    FileUtils.deleteQuietly(gitDirPath.toFile());
  }

  @Test
  void should_filter_ignored_files() throws IOException {
    createFile(gitDirPath, "fileA", "line1", "line2", "line3");
    createFile(gitDirPath, "fileB", "line1", "line2", "line3");
    createFile(gitDirPath, "fileC", "line1", "line2", "line3");

    var filteredOutIgnoredFiles = GitUtils.filterOutIgnoredFiles(gitDirPath, List.of(URI.create("fileA"), URI.create("fileB"),
      URI.create("fileC")));
    assertThat(filteredOutIgnoredFiles)
      .hasSize(3)
      .containsExactly(URI.create("fileA"), URI.create("fileB"), URI.create("fileC"));

    addFileToGitIgnore(git, "fileB");

    filteredOutIgnoredFiles = GitUtils.filterOutIgnoredFiles(gitDirPath, List.of(URI.create("fileA"), URI.create("fileB"), URI.create(
      "fileC")));
    assertThat(filteredOutIgnoredFiles)
      .hasSize(2)
      .containsExactly(URI.create("fileA"), URI.create("fileC"));
  }

  @Test
  void should_log_exception_filter_ignored_files() {
    FileUtils.deleteQuietly(gitDirPath.toFile());

    var filteredOutIgnoredFiles = GitUtils.filterOutIgnoredFiles(gitDirPath, List.of(URI.create("fileA"), URI.create("fileB"),
      URI.create("fileC")));

    assertThat(logTester.logs(LogOutput.Level.WARN))
      .anyMatch(s -> s.contains("Error occurred while determining files ignored by Git"))
      .anyMatch(s -> s.contains("Considering all files as not ignored by Git"));

    assertThat(filteredOutIgnoredFiles)
      .hasSize(3)
      .containsExactly(URI.create("fileA"), URI.create("fileB"), URI.create("fileC"));
  }
}
