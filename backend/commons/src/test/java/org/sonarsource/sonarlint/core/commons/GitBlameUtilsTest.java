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
import java.util.Map;
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
import static org.awaitility.Awaitility.await;
import static org.eclipse.jgit.util.FileUtils.RECURSIVE;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.appendFile;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.commit;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createFile;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createRepository;

class GitBlameUtilsTest {

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
  void it_should_return_correct_latest_changed_date_for_file_line_ranges() throws IOException, GitAPIException {
    createFile(gitDirPath, "fileA", "line1", "line2", "line3");
    var c1 = commit(git, "fileA");

    // Wait for one second to achieve different commit time
    await().pollDelay(1, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(true));
    appendFile(gitDirPath.resolve("fileA"), "new line 4");
    var c2 = commit(git, "fileA");

    // Wait for one second to achieve different commit time
    await().pollDelay(1, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(true));
    createFile(gitDirPath, "fileB", "line1", "line2", "line3");
    var c3 = commit(git, "fileB");

    // Wait for one second to achieve different commit time
    await().pollDelay(1, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(true));
    createFile(gitDirPath, "fileC", "line1", "line2", "line3");
    commit(git, "fileC");

    var results = GitBlameUtils.getLatestChangeDateForFilesLines(git.getRepository(), Map.of(Path.of("fileA"), List.of(new LinesRange(2, 2),
      new LinesRange(2, 3), new LinesRange(0, 2)), Path.of("fileB"), List.of(new LinesRange(1, 2))));

    assertThat(results)
      .hasSize(2)
      .containsOnlyKeys(Path.of("fileA"), Path.of("fileB"));
    assertThat(results.get(Path.of("fileA")))
      .hasSize(3)
      .containsEntry(new LinesRange(0, 2), c1)
      .containsEntry(new LinesRange(2, 2), c1)
      .containsEntry(new LinesRange(2, 3), c2);
    assertThat(results.get(Path.of("fileB")))
      .hasSize(1)
      .containsEntry(new LinesRange(1, 2), c3);
  }

  @Test
  void it_should_handle_dodgy_range_input() throws IOException, GitAPIException {
    createFile(gitDirPath, "fileA", "line1", "line2", "line3");
    var c1 = commit(git, "fileA");

    var results = GitBlameUtils.getLatestChangeDateForFilesLines(git.getRepository(), Map.of(Path.of("fileA"), List.of(new LinesRange(0, Integer.MAX_VALUE))));
    assertThat(results)
      .hasSize(1)
      .containsOnlyKeys(Path.of("fileA"));
    assertThat(results.get(Path.of("fileA")))
      .hasSize(1)
      .containsEntry(new LinesRange(0, Integer.MAX_VALUE), c1);
  }

  @Test
  void it_should_blame_file() throws IOException, GitAPIException {
    createFile(gitDirPath, "fileA", "line1", "line2", "line3");
    var c1 = commit(git, "fileA");

    var result = GitBlameUtils.blameWithFilesGitCommand(git.getRepository(), Set.of(Path.of("fileA")));

    assertThat(result.getFileBlameByPath()).hasSize(1);
    assertThat(result.getFileBlameByPath().get("fileA").getCommitDates())
      .hasSize(3)
      .allMatch(date -> date.equals(c1));
  }
}
