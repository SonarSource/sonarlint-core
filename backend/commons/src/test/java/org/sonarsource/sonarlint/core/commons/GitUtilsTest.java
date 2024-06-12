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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.util.GitUtils;

import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jgit.lib.Constants.GITIGNORE_FILENAME;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.addFileToGitIgnoreAndCommit;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createFile;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createRepository;

class GitUtilsTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  private Path projectDirPath;
  private Git git;

  @BeforeEach
  public void prepare() throws Exception {
    git = createRepository(projectDirPath);
  }

  @AfterEach
  public void cleanup() {
    FileUtils.deleteQuietly(projectDirPath.toFile());
  }

  @Test
  void should_filter_ignored_files() throws IOException, GitAPIException {
    createFile(projectDirPath, "fileA", "line1", "line2", "line3");
    createFile(projectDirPath, "fileB", "line1", "line2", "line3");
    createFile(projectDirPath, "fileC", "line1", "line2", "line3");

    var fileAUri = projectDirPath.resolve("fileA").toUri();
    var fileBUri = projectDirPath.resolve("fileB").toUri();
    var fileCUri = projectDirPath.resolve("fileC").toUri();

    var sonarLintGitIgnore = GitUtils.createSonarLintGitIgnore(projectDirPath);
    assertThat(Stream.of(fileAUri, fileBUri, fileCUri).filter(not(sonarLintGitIgnore::isFileIgnored)).collect(Collectors.toList()))
      .hasSize(3)
      .containsExactly(fileAUri, fileBUri, fileCUri);

    addFileToGitIgnoreAndCommit(git, "fileB");

    sonarLintGitIgnore = GitUtils.createSonarLintGitIgnore(projectDirPath);
    assertThat(Stream.of(fileAUri, fileBUri, fileCUri).filter(not(sonarLintGitIgnore::isFileIgnored)).collect(Collectors.toList()))
      .hasSize(2)
      .containsExactly(fileAUri, fileCUri);
  }

  @Test
  void should_filter_ignored_directories() throws IOException, GitAPIException {
    var fileB = Path.of("myDir").resolve("fileB");
    var fileC = Path.of("myDir").resolve("fileC");

    createFile(projectDirPath, "fileA", "line1", "line2", "line3");
    createFile(projectDirPath, fileB.toString(), "line1", "line2", "line3");
    createFile(projectDirPath, fileC.toString(), "line1", "line2", "line3");

    var fileAUri = projectDirPath.resolve("fileA").toUri();
    var fileBUri = projectDirPath.resolve(fileB).toUri();
    var fileCUri = projectDirPath.resolve(fileC).toUri();

    var sonarLintGitIgnore = GitUtils.createSonarLintGitIgnore(projectDirPath);
    assertThat(Stream.of(fileAUri, fileBUri, fileCUri).filter(not(sonarLintGitIgnore::isFileIgnored)).collect(Collectors.toList()))
      .hasSize(3)
      .containsExactly(fileAUri, fileBUri, fileCUri);

    addFileToGitIgnoreAndCommit(git, "myDir/");

    sonarLintGitIgnore = GitUtils.createSonarLintGitIgnore(projectDirPath);
    assertThat(Stream.of(fileAUri, fileBUri, fileCUri).filter(not(sonarLintGitIgnore::isFileIgnored)).collect(Collectors.toList()))
      .hasSize(1)
      .containsExactly(fileAUri);
  }

  @Test
  void should_consider_all_files_not_ignored_on_exception() throws IOException {
    createFile(projectDirPath, "fileA", "line1", "line2", "line3");
    createFile(projectDirPath, "fileB", "line1", "line2", "line3");
    createFile(projectDirPath, "fileC", "line1", "line2", "line3");

    var fileAUri = projectDirPath.resolve("fileA").toUri();
    var fileBUri = projectDirPath.resolve("fileB").toUri();
    var fileCUri = projectDirPath.resolve("fileC").toUri();

    var gitIgnore = projectDirPath.resolve(GITIGNORE_FILENAME);
    FileUtils.deleteQuietly(gitIgnore.toFile());

    var sonarLintGitIgnore = GitUtils.createSonarLintGitIgnore(projectDirPath);

    assertThat(logTester.logs(LogOutput.Level.WARN))
      .anyMatch(s -> s.contains("Error occurred while reading .gitignore file"))
      .anyMatch(s -> s.contains("Building empty ignore node with no rules. Files checked against this node will be considered as not ignored"));

    assertThat(Stream.of(fileAUri, fileBUri, fileCUri).filter(not(sonarLintGitIgnore::isFileIgnored)).collect(Collectors.toList()))
      .hasSize(3)
      .containsExactly(fileAUri, fileBUri, fileCUri);
  }
}
