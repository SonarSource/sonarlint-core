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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jgit.util.FileUtils.RECURSIVE;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.appendFile;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.commit;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createFile;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createRepository;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.modifyFile;

class SonarLintBlameResultTest {

  @TempDir
  private static Path baseDir;
  private Git git;
  private Path gitDirPath;

  @BeforeEach
  public void prepare() throws IOException {
    gitDirPath = baseDir.resolve("gitDir");
    git = createRepository(gitDirPath);
  }

  @AfterEach
  public void cleanup() throws IOException {
    FileUtils.delete(gitDirPath.toFile(), RECURSIVE);
  }

  @Test
  void it_should_return_correct_latest_changed_date_for_file_line_ranges() throws IOException, GitAPIException, InterruptedException {
    createFile(gitDirPath, "fileA", "line1", "line2", "line3");
    var c1 = commit(git, "fileA");

    // Wait for one second to achieve different commit time
    TimeUnit.SECONDS.sleep(1);
    appendFile(gitDirPath.resolve("fileA"), "new line 4");
    var c2 = commit(git, "fileA");

    // Wait for one second to achieve different commit time
    TimeUnit.SECONDS.sleep(1);
    createFile(gitDirPath, "fileB", "line1", "line2", "line3");
    var c3 = commit(git, "fileB");

    createFile(gitDirPath, "fileC", "line1", "line2", "line3");
    commit(git, "fileC");

    var results = GitBlameUtils.blameWithFilesGitCommand(git.getRepository(), Set.of(Path.of("fileA"), Path.of("fileB")));

    assertThat(results.getLatestChangeDateForLinesRangeInFile(Path.of("fileA"), new LinesRange(0, 2))).isPresent().contains(c1);
    assertThat(results.getLatestChangeDateForLinesRangeInFile(Path.of("fileA"), new LinesRange(2, 2))).isPresent().contains(c1);
    assertThat(results.getLatestChangeDateForLinesRangeInFile(Path.of("fileA"), new LinesRange(2, 3))).isPresent().contains(c2);
    assertThat(results.getLatestChangeDateForLinesRangeInFile(Path.of("fileB"), new LinesRange(1, 2))).isPresent().contains(c3);
    assertThat(results.getLatestChangeDateForLinesRangeInFile(Path.of("fileC"), new LinesRange(1, 2))).isEmpty();
  }

  @Test
  void it_should_handle_all_line_range_modified() throws IOException, GitAPIException {
    createFile(gitDirPath, "fileA", "line1", "line2", "line3");
    var c1 = commit(git, "fileA");

    var results = GitBlameUtils.blameWithFilesGitCommand(git.getRepository(), Set.of(Path.of("fileA")));
    assertThat(results.getLatestChangeDateForLinesRangeInFile(Path.of("fileA"), new LinesRange(0, 2))).isPresent().contains(c1);

    modifyFile(gitDirPath.resolve("fileA"), "new line1", "new line2", "new line3");

    results = GitBlameUtils.blameWithFilesGitCommand(git.getRepository(), Set.of(Path.of("fileA")));
    assertThat(results.getLatestChangeDateForLinesRangeInFile(Path.of("fileA"), new LinesRange(0, 2))).isEmpty();
  }

  @Test
  void it_should_handle_end_of_line_range_modified() throws IOException, GitAPIException {
    createFile(gitDirPath, "fileA", "line1", "line2");
    var c1 = commit(git, "fileA");

    var results = GitBlameUtils.blameWithFilesGitCommand(git.getRepository(), Set.of(Path.of("fileA")));
    assertThat(results.getLatestChangeDateForLinesRangeInFile(Path.of("fileA"), new LinesRange(0, 2))).isPresent().contains(c1);

    appendFile(gitDirPath.resolve("fileA"), "new line3", "new line4");

    results = GitBlameUtils.blameWithFilesGitCommand(git.getRepository(), Set.of(Path.of("fileA")));
    assertThat(results.getLatestChangeDateForLinesRangeInFile(Path.of("fileA"), new LinesRange(0, 2))).isEmpty();
  }

  @Test
  void it_should_handle_dodgy_range_input() throws IOException, GitAPIException {
    createFile(gitDirPath, "fileA", "line1", "line2", "line3");
    var c1 = commit(git, "fileA");

    var results = GitBlameUtils.blameWithFilesGitCommand(git.getRepository(), Set.of(Path.of("fileA"), Path.of("fileB")));

    assertThat(results.getLatestChangeDateForLinesRangeInFile(Path.of("fileA"), new LinesRange(0, Integer.MAX_VALUE))).isPresent().contains(c1);
    assertThat(results.getLatestChangeDateForLinesRangeInFile(Path.of("fileA"), new LinesRange(Integer.MAX_VALUE, Integer.MAX_VALUE))).isEmpty();
  }

}
