/*
 * SonarLint Core - Commons
 * Copyright (C) SonarSource Sàrl
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class GitServiceWorkTreeRootTests {

  @TempDir
  Path dir;

  @Test
  void finds_the_root_of_the_work_tree_containing_a_nested_directory() throws IOException, GitAPIException {
    var repo = repository("repo");
    var nested = Files.createDirectories(repo.resolve("packages/frontend"));

    assertThat(GitService.findWorkTreeRoot(nested)).isEqualTo(repo.toRealPath());
    assertThat(GitService.findWorkTreeRoot(repo)).isEqualTo(repo.toRealPath());
  }

  @Test
  void finds_a_linked_worktree_as_a_work_tree_of_its_own() throws IOException, GitAPIException {
    var main = repository("main");
    var worktree = Files.createDirectories(dir.resolve("wt"));
    var worktreeGitDir = Files.createDirectories(main.resolve(".git/worktrees/wt"));
    Files.writeString(worktreeGitDir.resolve("HEAD"), "ref: refs/heads/feature\n");
    Files.writeString(worktreeGitDir.resolve("commondir"), "../..\n");
    Files.writeString(worktreeGitDir.resolve("gitdir"), worktree.resolve(".git") + "\n");
    Files.writeString(worktree.resolve(".git"), "gitdir: " + worktreeGitDir + "\n");

    assertThat(GitService.findWorkTreeRoot(Files.createDirectories(worktree.resolve("src")))).isEqualTo(worktree.toRealPath());
  }

  @Test
  void ignores_a_git_folder_that_is_not_a_repository() throws IOException {
    var notARepo = Files.createDirectories(dir.resolve("broken"));
    Files.createDirectories(notARepo.resolve(".git"));

    assertThat(GitService.findWorkTreeRoot(notARepo)).isNull();
  }

  @Test
  void is_null_outside_any_repository() {
    assertThat(GitService.findWorkTreeRoot(dir)).isNull();
  }

  private Path repository(String name) throws IOException, GitAPIException {
    var repo = Files.createDirectories(dir.resolve(name));
    Git.init().setDirectory(repo.toFile()).call().close();
    return repo;
  }
}
