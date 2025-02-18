/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.commons.util.git;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.LogTestStartAndEnd;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jgit.lib.Constants.GITIGNORE_FILENAME;
import static org.eclipse.jgit.util.FileUtils.RECURSIVE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.addFileToGitIgnoreAndCommit;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.commit;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createFile;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createRepository;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.modifyFile;
import static org.sonarsource.sonarlint.core.commons.util.git.GitUtils.blameWithFilesGitCommand;
import static org.sonarsource.sonarlint.core.commons.util.git.GitUtils.getBlameResult;
import static org.sonarsource.sonarlint.core.commons.util.git.GitUtils.getVSCChangedFiles;

@ExtendWith(LogTestStartAndEnd.class)
class GitUtilsTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  private Path projectDirPath;
  private Git git;
  private static Path bareRepoPath;
  private static Path workingRepoPath;

  @BeforeAll
  public static void beforeAll() throws GitAPIException, IOException {
    setUpBareRepo(Map.of(
      ".gitignore", "*.log\n*.tmp\n",
      "fileA", "lineA1\nlineA2\n",
      "fileB", "lineB1\nlineB2\n"
    ));
  }

  @AfterAll
  public static void afterAll() {
    try {
      FileUtils.forceDelete(bareRepoPath.toFile());
      FileUtils.forceDelete(workingRepoPath.toFile());
    } catch (Exception ignored) {
      //It throws an exception in windows
    }
  }

  @BeforeEach
  public void prepare() throws Exception {
    git = createRepository(projectDirPath);
  }

  @AfterEach
  public void cleanup() throws IOException {
    org.eclipse.jgit.util.FileUtils.delete(projectDirPath.toFile(), RECURSIVE);
  }

  @Test
  void it_should_blame_file() throws IOException, GitAPIException {
    createFile(projectDirPath, "fileA", "line1", "line2", "line3");
    var c1 = commit(git, "fileA");

    var sonarLintBlameResult = blameWithFilesGitCommand(projectDirPath, Set.of(Path.of("fileA")));
    assertThat(IntStream.of(1, 2, 3)
      .mapToObj(lineNumber -> sonarLintBlameResult.getLatestChangeDateForLinesInFile(Path.of("fileA"), List.of(lineNumber))))
      .map(Optional::get)
      .allMatch(date -> date.equals(c1));
  }

  @Test
  void it_should_fallback_to_jgit_blame() throws IOException, GitAPIException {
    createFile(projectDirPath, "fileA", "line1", "line2", "line3");
    var c1 = commit(git, "fileA");

    var sonarLintBlameResult = getBlameResult(projectDirPath, Set.of(Path.of("fileA")), null, path -> false);
    assertThat(IntStream.of(1, 2, 3)
      .mapToObj(lineNumber -> sonarLintBlameResult.getLatestChangeDateForLinesInFile(Path.of("fileA"), List.of(lineNumber))))
      .map(Optional::get)
      .allMatch(date -> date.equals(c1));
  }

  @Test
  void it_should_throw_if_no_files() {
    Set<Path> files = Set.of();

    assertThrows(IllegalStateException.class, () -> getBlameResult(projectDirPath, files, null, path -> true));
  }

  @Test
  void it_should_blame_with_given_contents_within_inner_dir() throws IOException, GitAPIException {
    var deepFilePath = Path.of("innerDir").resolve("fileA").toString();
    createFile(projectDirPath, deepFilePath, "SonarQube", "SonarCloud", "SonarLint");
    var c1 = commit(git, deepFilePath);
    var content = String.join(System.lineSeparator(), "SonarQube", "Cloud", "SonarLint", "SonarSolution") + System.lineSeparator();

    UnaryOperator<String> fileContentProvider = path -> deepFilePath.equals(path) ? content : null;
    var sonarLintBlameResult = blameWithFilesGitCommand(projectDirPath, Set.of(Path.of(deepFilePath)), fileContentProvider);
    assertThat(IntStream.of(1, 2, 3, 4)
      .mapToObj(lineNumber -> sonarLintBlameResult.getLatestChangeDateForLinesInFile(Path.of(deepFilePath), List.of(lineNumber))))
      .map(dateOpt -> dateOpt.orElse(null))
      .containsExactly(c1, null, c1, null);
  }

  @Test
  void it_should_blame_file_within_inner_dir() throws IOException, GitAPIException {
    var deepFilePath = Path.of("innerDir").resolve("fileA").toString();

    createFile(projectDirPath, deepFilePath, "line1", "line2", "line3");
    var c1 = commit(git, deepFilePath);

    var sonarLintBlameResult = blameWithFilesGitCommand(projectDirPath, Set.of(Path.of(deepFilePath)));
    var latestChangeDate = sonarLintBlameResult.getLatestChangeDateForLinesInFile(Path.of(deepFilePath), List.of(1, 2, 3));
    assertThat(latestChangeDate).isPresent().contains(c1);
  }

  @Test
  void it_should_blame_project_files_when_project_base_is_sub_folder_of_git_repo() throws IOException, GitAPIException {
    projectDirPath = projectDirPath.resolve("subFolder");

    createFile(projectDirPath, "fileA", "line1", "line2", "line3");
    var c1 = commit(git, git.getRepository().getWorkTree().toPath().relativize(projectDirPath).resolve("fileA").toString());

    var sonarLintBlameResult = blameWithFilesGitCommand(projectDirPath, Set.of(Path.of("fileA")));
    assertThat(IntStream.of(1, 2, 3)
      .mapToObj(lineNumber -> sonarLintBlameResult.getLatestChangeDateForLinesInFile(Path.of("fileA"), List.of(lineNumber))))
      .map(Optional::get)
      .allMatch(date -> date.equals(c1));
  }

  @Test
  void it_should_get_uncommitted_files_including_untracked_ones() throws GitAPIException, IOException {
    var committedFile = "committedFile";
    var committedAndModifiedFile = "committedAndModifiedFile";
    var uncommittedTrackedFile = "uncommittedTrackedFile";
    var uncommittedUntrackedFile = "uncommittedUntrackedFile";
    var committedFileUri = projectDirPath.resolve(committedFile).toUri();
    var committedAndModifiedFileUri = projectDirPath.resolve(committedAndModifiedFile).toUri();
    var uncommittedTrackedFileUri = projectDirPath.resolve(uncommittedTrackedFile).toUri();
    var uncommittedUntrackedFileUri = projectDirPath.resolve(uncommittedUntrackedFile).toUri();

    var folderFile = Path.of("folder").resolve("folderFile");
    var string = FilenameUtils.separatorsToUnix(folderFile.toString());
    createFile(projectDirPath, string, "line1", "line2", "line3");
    git.add().setUpdate(true).addFilepattern(string).call();

    createFile(projectDirPath, committedFile, "line1", "line2", "line3");
    commit(git, committedFile);

    createFile(projectDirPath, committedAndModifiedFile, "line1", "line2", "line3");
    commit(git, committedAndModifiedFile);
    modifyFile(projectDirPath.resolve(committedAndModifiedFile), "line1", "line2", "line3", "line4");

    createFile(projectDirPath, uncommittedTrackedFile, "line1", "line2", "line3");
    git.add().addFilepattern(uncommittedTrackedFile).call();

    createFile(projectDirPath, uncommittedUntrackedFile, "line1", "line2", "line3");

    var changedFiles = getVSCChangedFiles(projectDirPath);

    assertThat(changedFiles).hasSize(4)
      .doesNotContain(committedFileUri)
      .contains(committedAndModifiedFileUri)
      .contains(uncommittedTrackedFileUri)
      .contains(uncommittedUntrackedFileUri)
      .contains(projectDirPath.resolve(folderFile).toUri());
  }

  @Test
  void it_should_return_empty_list_if_base_dir_not_resolved() {
    assertThat(getVSCChangedFiles(null)).isEmpty();
  }

  @Test
  void it_should_return_empty_list_on_git_exception(@TempDir Path nonGitDir) {
    assertThat(getVSCChangedFiles(nonGitDir)).isEmpty();
  }

  @Test
  void should_filter_ignored_files() throws IOException, GitAPIException {
    createFile(projectDirPath, "fileA", "line1", "line2", "line3");
    createFile(projectDirPath, "fileB", "line1", "line2", "line3");
    createFile(projectDirPath, "fileC", "line1", "line2", "line3");

    var fileAPath = Path.of("fileA");
    var fileBPath = Path.of("fileB");
    var fileCPath = Path.of("fileC");

    var sonarLintGitIgnore = GitUtils.createSonarLintGitIgnore(projectDirPath);
    assertThat(Stream.of(fileAPath, fileBPath, fileCPath).filter(not(sonarLintGitIgnore::isFileIgnored)).toList())
      .hasSize(3)
      .containsExactly(fileAPath, fileBPath, fileCPath);

    addFileToGitIgnoreAndCommit(git, "fileB");

    sonarLintGitIgnore = GitUtils.createSonarLintGitIgnore(projectDirPath);
    assertThat(Stream.of(fileAPath, fileBPath, fileCPath).filter(not(sonarLintGitIgnore::isFileIgnored)).toList())
      .hasSize(2)
      .containsExactly(fileAPath, fileCPath);
  }

  @Test
  void should_filter_ignored_directories() throws IOException, GitAPIException {
    var fileA = Path.of("fileA");
    var fileB = Path.of("myDir").resolve("fileB");
    var fileC = Path.of("myDir").resolve("fileC");

    createFile(projectDirPath, "fileA", "line1", "line2", "line3");
    createFile(projectDirPath, fileB.toString(), "line1", "line2", "line3");
    createFile(projectDirPath, fileC.toString(), "line1", "line2", "line3");

    var sonarLintGitIgnore = GitUtils.createSonarLintGitIgnore(projectDirPath);
    assertThat(Stream.of(fileA, fileB, fileC).filter(not(sonarLintGitIgnore::isFileIgnored)).toList())
      .hasSize(3)
      .containsExactly(fileA, fileB, fileC);

    addFileToGitIgnoreAndCommit(git, "myDir/");

    sonarLintGitIgnore = GitUtils.createSonarLintGitIgnore(projectDirPath);
    assertThat(Stream.of(fileA, fileB, fileC).filter(not(sonarLintGitIgnore::isFileIgnored)).toList())
      .hasSize(1)
      .containsExactly(fileA);
  }

  @Test
  void should_consider_all_files_not_ignored_on_gitignore() throws IOException {
    createFile(projectDirPath, "fileA", "line1", "line2", "line3");
    createFile(projectDirPath, "fileB", "line1", "line2", "line3");
    createFile(projectDirPath, "fileC", "line1", "line2", "line3");

    var fileAPath = projectDirPath.resolve("fileA");
    var fileBPath = projectDirPath.resolve("fileB");
    var fileCPath = projectDirPath.resolve("fileC");

    var gitIgnore = projectDirPath.resolve(GITIGNORE_FILENAME);
    FileUtils.deleteQuietly(gitIgnore.toFile());

    var sonarLintGitIgnore = GitUtils.createSonarLintGitIgnore(projectDirPath);

    assertThat(logTester.logs(LogOutput.Level.INFO))
      .anyMatch(s -> s.contains(".gitignore file was not found for "));

    assertThat(Stream.of(fileAPath, fileBPath, fileCPath).filter(not(sonarLintGitIgnore::isFileIgnored)).toList())
      .hasSize(3)
      .containsExactly(fileAPath, fileBPath, fileCPath);
  }

  @Test
  void should_continue_normally_with_null_basedir() {
    var sonarLintGitIgnore = GitUtils.createSonarLintGitIgnore(null);

    assertThat(sonarLintGitIgnore.isIgnored(Path.of("file/path"))).isFalse();
  }

  @Test
  void should_consider_files_ignored_when_git_root_above_project_root() throws IOException, GitAPIException {
    var gitRoot = Files.createTempDirectory("test");
    var projectRoot = Files.createDirectory(gitRoot.resolve("toto"));
    try (var ignored = Git.init().setDirectory(gitRoot.toFile()).call()) {
      var gitignoreFile = new File(gitRoot.toFile(), ".gitignore");
      Files.writeString(gitignoreFile.toPath(), "*.js");
    }

    var sonarLintGitIgnore = GitUtils.createSonarLintGitIgnore(projectRoot);

    assertThat(sonarLintGitIgnore.isIgnored(Path.of("frontend/app/should_not_be_ignored.js"))).isTrue();
  }

  @Test
  void should_respect_gitignore_rules() throws IOException {
    Files.write(projectDirPath.resolve(GITIGNORE_FILENAME), List.of("app/", "!frontend/app/"), java.nio.file.StandardOpenOption.CREATE);
    var sonarLintGitIgnore = GitUtils.createSonarLintGitIgnore(projectDirPath);

    assertThat(sonarLintGitIgnore.isIgnored(Path.of("frontend/app/should_not_be_ignored.js"))).isFalse();
    assertThat(sonarLintGitIgnore.isIgnored(Path.of("should_be_ignored.js"))).isFalse();
    assertThat(sonarLintGitIgnore.isIgnored(Path.of("app/should_be_ignored.js"))).isTrue();
  }

  @Test
  void createSonarLintGitIgnore_works_for_bare_repos_too() {
    var sonarLintGitIgnore = GitUtils.createSonarLintGitIgnore(bareRepoPath);

    assertThat(sonarLintGitIgnore.isFileIgnored(Path.of("file.txt"))).isFalse();
    assertThat(sonarLintGitIgnore.isFileIgnored(Path.of("file.tmp"))).isTrue();
    assertThat(sonarLintGitIgnore.isFileIgnored(Path.of("file.log"))).isTrue();
  }

  @Test
  void nonAsciiCharacterFileName() {
    var sonarLintGitIgnore = GitUtils.createSonarLintGitIgnore(bareRepoPath);

    assertThat(sonarLintGitIgnore.isIgnored(Path.of("Sönar.txt"))).isFalse();
    assertThat(sonarLintGitIgnore.isIgnored(Path.of("Sönar.log"))).isTrue();
  }

  @Test
  void git_blame_works_for_bare_repos_too() {
    var sonarLintBlameResult = blameWithFilesGitCommand(bareRepoPath, Stream.of("fileA", "fileB").map(Path::of).collect(Collectors.toSet()));

    assertThat(sonarLintBlameResult.getLatestChangeDateForLinesInFile(Path.of("fileA"), List.of(1, 2))).isPresent();
    assertThat(sonarLintBlameResult.getLatestChangeDateForLinesInFile(Path.of("fileA"), List.of(3))).isEmpty();
    assertThat(sonarLintBlameResult.getLatestChangeDateForLinesInFile(Path.of("fileB"), List.of(1, 2))).isPresent();
    assertThat(sonarLintBlameResult.getLatestChangeDateForLinesInFile(Path.of("fileB"), List.of(3))).isEmpty();
  }

  private static void setUpBareRepo(Map<String, String> filePathContentMap) throws IOException, GitAPIException {
    bareRepoPath = Files.createTempDirectory("bare-repo");
    workingRepoPath = Files.createTempDirectory("working-repo");
    // Initialize a bare repository
    try (var ignored = Git.init().setBare(true).setDirectory(bareRepoPath.toFile()).call()) {
      // Initialize a working directory repository
      try (var workingGit = Git.init().setDirectory(workingRepoPath.toFile()).call()) {
        // Create a .gitignore file in the working directory
        for (var filePath : filePathContentMap.keySet()) {
          var gitignoreFile = new File(workingRepoPath.toFile(), filePath);
          Files.writeString(gitignoreFile.toPath(), filePathContentMap.get(filePath));

          // Stage and commit the .gitignore file
          workingGit.add().addFilepattern(filePath).call();
          workingGit.commit().setMessage("Add " + filePath).call();
        }

        // Add the bare repository as a remote and push the commit
        workingGit.remoteAdd()
          .setName("origin")
          .setUri(new URIish(bareRepoPath.toUri().toString()))
          .call();
        workingGit.push().setRemote("origin").call();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
