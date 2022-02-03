/*
 * SonarLint Version Control System
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.vcs;


import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.lang.String.format;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitUtilsTest {

  @Test
  void noGitRepoShouldBeNull(@TempDir File projectDir) throws IOException {
    javaUnzip("no-git-repo.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "no-git-repo");
    Git git = GitUtils.getGitForDir(path);

    assertThat(git).isNull();
  }

  @Test
  void gitRepoShouldBeNotNull(@TempDir File projectDir) throws IOException {
    javaUnzip("dummy-git.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "dummy-git");
    Git git = GitUtils.getGitForDir(path);

    assertThat(git).isNotNull();

    Map<String, List<String>> commitsCache = GitUtils.buildCommitsCache(git);
    Optional<String> branch = GitUtils.electSQBranchForLocalBranch("foo", git,
      Set.of("foo", "bar", "master"),"master");

    assertThat(commitsCache).hasSize(5);
    assertThat(branch).contains("master");
  }

  @Test
  void shouldElectAnalyzedBranch(@TempDir File projectDir) throws IOException {
    javaUnzip("analyzed-branch.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "analyzed-branch");
    Git git = GitUtils.getGitForDir(path);

    assertThat(git).isNotNull();

    Map<String, List<String>> commitsCache = GitUtils.buildCommitsCache(git);
    Optional<String> branch = GitUtils.electSQBranchForLocalBranch("closest_branch", git,
      Set.of("foo", "closest_branch", "master"), "master");

    assertThat(commitsCache).hasSize(3);
    assertThat(branch).contains("closest_branch");
  }

  @Test
  void shouldElectClosestBranch(@TempDir File projectDir) throws IOException {
    javaUnzip("closest-branch.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "closest-branch");
    Git git = GitUtils.getGitForDir(path);

    assertThat(git).isNotNull();

    Map<String, List<String>> commitsCache = GitUtils.buildCommitsCache(git);
    Optional<String> branch = GitUtils.electSQBranchForLocalBranch("current_branch", git,
      Set.of("foo", "closest_branch", "master"), "master");

    assertThat(commitsCache).hasSize(3);
    assertThat(branch).contains("closest_branch");
  }

  @Test
  void shouldElectMasterForNonAnalyzedChildBranch(@TempDir File projectDir) throws IOException {
    javaUnzip("child-from-non-analyzed.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "child-from-non-analyzed");
    Git git = GitUtils.getGitForDir(path);

    assertThat(git).isNotNull();

    Map<String, List<String>> commitsCache = GitUtils.buildCommitsCache(git);
    Optional<String> branch = GitUtils.electSQBranchForLocalBranch("not_analyzed_branch", git,
      Set.of("foo", "branch_to_analyze", "master"), "master");

    assertThat(commitsCache).hasSize(5);
    assertThat(branch).contains("master");
  }

  @Test
  void shouldReturnEmptyOptionalOnException() throws IOException {
    Git git = mock(Git.class);
    Repository repo = mock(Repository.class);
    RefDatabase database = mock(RefDatabase.class);
    when(git.getRepository()).thenReturn(repo);
    when(repo.getRefDatabase()).thenReturn(database);
    when(database.getRefs()).thenThrow(new IOException());

    Optional<String> branch = GitUtils.electSQBranchForLocalBranch("foo", git,
      Set.of("foo", "bar", "master"),"master");

    assertThat(branch).isEmpty();
  }

  @Test
  void shouldReturnEmptyCacheOnException() throws IOException {
    Git git = mock(Git.class);
    Repository repo = mock(Repository.class);
    RefDatabase database = mock(RefDatabase.class);
    when(git.getRepository()).thenReturn(repo);
    when(repo.getRefDatabase()).thenReturn(database);
    when(database.getRefs()).thenThrow(new IOException());

    Map<String, List<String>> cache = GitUtils.buildCommitsCache(git);

    assertThat(cache).isEmpty();
  }

  public void javaUnzip(String zipFileName, File toDir) throws IOException {
    try {
      File testRepos = new File(this.getClass().getResource("/test-repos").toURI());
      File zipFile = new File(testRepos, zipFileName);
      javaUnzip(zipFile, toDir);
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  private static void javaUnzip(File zip, File toDir) {
    try {
      try (ZipFile zipFile = new ZipFile(zip)) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          File to = new File(toDir, entry.getName());
          if (entry.isDirectory()) {
            forceMkdir(to);
          } else {
            File parent = to.getParentFile();
            forceMkdir(parent);

            Files.copy(zipFile.getInputStream(entry), to.toPath());
          }
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException(format("Fail to unzip %s to %s", zip, toDir), e);
    }
  }

  private static void forceMkdir(final File directory) throws IOException {
    if ((directory != null) && (!directory.mkdirs() && !directory.isDirectory())) {
      throw new IOException("Cannot create directory '" + directory + "'.");
    }
  }
}
