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
  void should_consider_all_files_not_ignored_on_exception() {
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
