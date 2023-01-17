/*
 * SonarLint Core - Version Control System
 * Copyright (C) 2016-2023 SonarSource SA
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.lang.String.format;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitUtilsTest {

  @Test
  void noGitRepoShouldBeNull(@TempDir File projectDir) throws IOException {
    javaUnzip("no-git-repo.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "no-git-repo");
    Repository repo = GitUtils.getRepositoryForDir(path);
    assertThat(repo).isNull();
  }

  @Test
  void gitRepoShouldBeNotNull(@TempDir File projectDir) throws IOException {
    javaUnzip("dummy-git.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "dummy-git");
    try (Repository repo = GitUtils.getRepositoryForDir(path)) {
      Set<String> serverCandidateNames = Set.of("foo", "bar", "master");

      String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverCandidateNames, "master");
      assertThat(branch).isEqualTo("master");
    }
  }

  @Test
  void shouldElectAnalyzedBranch(@TempDir File projectDir) throws IOException {
    javaUnzip("analyzed-branch.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "analyzed-branch");
    try (Repository repo = GitUtils.getRepositoryForDir(path)) {
      Set<String> serverCandidateNames = Set.of("foo", "closest_branch", "master");

      String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverCandidateNames, "master");
      assertThat(branch).isEqualTo("closest_branch");
    }
  }

  @Test
  void shouldReturnNullIfNonePresentInLocalGit(@TempDir File projectDir) throws IOException {
    javaUnzip("analyzed-branch.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "analyzed-branch");
    try (Repository repo = GitUtils.getRepositoryForDir(path)) {
      Set<String> serverCandidateNames = Set.of("unknown1", "unknown2", "unknown3");

      String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverCandidateNames, "master");
      assertThat(branch).isNull();
    }
  }

  @Test
  void shouldElectClosestBranch(@TempDir File projectDir) throws IOException {
    javaUnzip("closest-branch.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "closest-branch");

    try (Repository repo = GitUtils.getRepositoryForDir(path)) {

      Set<String> serverCandidateNames = Set.of("foo", "closest_branch", "master");

      String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverCandidateNames, "master");
      assertThat(branch).isEqualTo("closest_branch");
    }
  }

  @Test
  void shouldElectClosestBranch_even_if_no_main_branch(@TempDir File projectDir) throws IOException {
    javaUnzip("closest-branch.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "closest-branch");

    try (Repository repo = GitUtils.getRepositoryForDir(path)) {

      Set<String> serverCandidateNames = Set.of("foo", "closest_branch", "master");

      String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverCandidateNames, null);
      assertThat(branch).isEqualTo("closest_branch");
    }
  }

  @Test
  void shouldElectMainBranchForNonAnalyzedChildBranch(@TempDir File projectDir) throws IOException {
    javaUnzip("child-from-non-analyzed.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "child-from-non-analyzed");
    try (Repository repo = GitUtils.getRepositoryForDir(path)) {

      Set<String> serverCandidateNames = Set.of("foo", "branch_to_analyze", "master");

      String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverCandidateNames, "master");
      assertThat(branch).isEqualTo("master");
    }
  }

  @Test
  void shouldReturnNullOnException() throws IOException {
    Repository repo = mock(Repository.class);
    RefDatabase db = mock(RefDatabase.class);
    when(repo.getRefDatabase()).thenReturn(db);
    when(db.exactRef(anyString())).thenThrow(new IOException());

    String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, Set.of("foo", "bar", "master"), "master");

    assertThat(branch).isNull();
  }

  @Test
  void shouldFavorCurrentBranchIfMultipleCandidates(@TempDir File projectDir) throws IOException {
    // Both main and same-as-master branches are pointing to HEAD, but same-as-master is the currently checked out branch
    javaUnzip("two-branches-for-head.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "two-branches-for-head");
    try (Repository repo = GitUtils.getRepositoryForDir(path)) {

      Set<String> serverCandidateNames = Set.of("main", "same-as-master", "another");

      String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverCandidateNames, "main");
      assertThat(branch).isEqualTo("same-as-master");
    }
  }

  public void javaUnzip(String zipFileName, File toDir) throws IOException {
    File testRepos = new File("src/test/test-repos");
    File zipFile = new File(testRepos, zipFileName);
    javaUnzip(zipFile, toDir);
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
