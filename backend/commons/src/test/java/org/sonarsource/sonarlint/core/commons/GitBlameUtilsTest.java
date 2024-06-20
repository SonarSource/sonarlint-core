/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.commons;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jgit.util.FileUtils.RECURSIVE;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.commit;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createFile;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createRepository;

class GitBlameUtilsTest {

  private Git git;
  @TempDir
  private Path projectDir;

  @BeforeEach
  public void prepare() throws IOException {
    git = createRepository(projectDir);
  }

  @AfterEach
  public void cleanup() throws IOException {
    FileUtils.delete(projectDir.toFile(), RECURSIVE);
  }

  @Test
  void it_should_blame_file() throws IOException, GitAPIException {
    createFile(projectDir, "fileA", "line1", "line2", "line3");
    var c1 = commit(git, "fileA");

    var sonarLintBlameResult = GitBlameUtils.blameWithFilesGitCommand(projectDir, Set.of(Path.of("fileA")));
    assertThat(IntStream.of(1, 2, 3)
      .mapToObj(lineNumber -> sonarLintBlameResult.getLatestChangeDateForLinesInFile(Path.of("fileA"), List.of(lineNumber))))
      .map(Optional::get)
      .allMatch(date -> date.equals(c1));
  }

  @Test
  void it_should_blame_with_given_contents_within_inner_dir() throws IOException, GitAPIException {
    var deepFilePath = Path.of("innerDir").resolve("fileA").toString();
    createFile(projectDir, deepFilePath, "SonarQube", "SonarCloud", "SonarLint");
    var c1 = commit(git, deepFilePath);
    var content = String.join(System.lineSeparator(), "SonarQube", "Cloud", "SonarLint", "SonarSolution") + System.lineSeparator();

    UnaryOperator<String> fileContentProvider = path -> deepFilePath.equals(path) ? content : null;
    var sonarLintBlameResult = GitBlameUtils.blameWithFilesGitCommand(projectDir, Set.of(Path.of(deepFilePath)), fileContentProvider);
    assertThat(IntStream.of(1, 2, 3, 4)
      .mapToObj(lineNumber -> sonarLintBlameResult.getLatestChangeDateForLinesInFile(Path.of(deepFilePath), List.of(lineNumber))))
      .map(dateOpt -> dateOpt.orElse(null))
      .containsExactly(c1, null, c1, null);
  }

  @Test
  void it_should_blame_file_within_inner_dir() throws IOException, GitAPIException {
    var deepFilePath = Path.of("innerDir").resolve("fileA").toString();

    createFile(projectDir, deepFilePath, "line1", "line2", "line3");
    var c1 = commit(git, deepFilePath);

    var sonarLintBlameResult = GitBlameUtils.blameWithFilesGitCommand(projectDir, Set.of(Path.of(deepFilePath)));
    var latestChangeDate = sonarLintBlameResult.getLatestChangeDateForLinesInFile(Path.of(deepFilePath), List.of(1, 2, 3));
    assertThat(latestChangeDate).isPresent().contains(c1);
  }

  @Test
  void it_should_blame_project_files_when_project_base_is_sub_folder_of_git_repo() throws IOException, GitAPIException {
    projectDir = projectDir.resolve("subFolder");

    createFile(projectDir, "fileA", "line1", "line2", "line3");
    var c1 = commit(git, git.getRepository().getWorkTree().toPath().relativize(projectDir).resolve("fileA").toString());

    var sonarLintBlameResult = GitBlameUtils.blameWithFilesGitCommand(projectDir, Set.of(Path.of("fileA")));
    assertThat(IntStream.of(1, 2, 3)
      .mapToObj(lineNumber -> sonarLintBlameResult.getLatestChangeDateForLinesInFile(Path.of("fileA"), List.of(lineNumber))))
      .map(Optional::get)
      .allMatch(date -> date.equals(c1));
  }
}
