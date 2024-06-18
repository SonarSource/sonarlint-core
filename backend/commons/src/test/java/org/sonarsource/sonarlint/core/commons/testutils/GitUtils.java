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
package org.sonarsource.sonarlint.core.commons.testutils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Date;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.SystemReader;

public class GitUtils {

  private GitUtils() {
    // Utils class
  }

  public static Git createRepository(Path worktree) throws IOException {
    var repo = FileRepositoryBuilder.create(worktree.resolve(".git").toFile());
    repo.create();
    return new Git(repo);
  }

  public static Date commit(Git git, String... paths) throws GitAPIException {
    if (paths.length > 0) {
      var add = git.add();
      for (String p : paths) {
        add.addFilepattern(FilenameUtils.separatorsToUnix(p));
      }
      add.call();
    }
    var commit = git.commit().setCommitter("joe", "email@email.com").setMessage("msg").call();
    return commit.getCommitterIdent().getWhen();
  }

  public static Date commitAtDate(Git git, Instant commitDate, String... paths) throws GitAPIException {
    if (paths.length > 0) {
      var add = git.add();
      for (String p : paths) {
        add.addFilepattern(FilenameUtils.separatorsToUnix(p));
      }
      add.call();
    }
    var commitTimestamp = commitDate.toEpochMilli();
    var commit = git.commit().setCommitter(new PersonIdent("joe", "email@email.com", commitTimestamp, SystemReader.getInstance()
      .getTimezone(commitTimestamp))).setMessage("msg").call();
    return commit.getCommitterIdent().getWhen();
  }

  public static void createFile(Path worktree, String relativePath, String... lines) throws IOException {
    var newFile = worktree.resolve(relativePath);
    Files.createDirectories(newFile.getParent());
    var content = String.join(System.lineSeparator(), lines) + System.lineSeparator();
    Files.write(newFile, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  public static void appendFile(Path file, String... lines) throws IOException {
    var content = String.join(System.lineSeparator(), lines) + System.lineSeparator();
    Files.write(file, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
  }

  public static void modifyFile(Path file, String... lines) throws IOException {
    var content = String.join(System.lineSeparator(), lines) + System.lineSeparator();
    Files.write(file, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
  }

}
