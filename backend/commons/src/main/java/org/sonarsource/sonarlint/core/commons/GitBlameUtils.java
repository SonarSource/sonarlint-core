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

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Repository;
import org.sonar.scm.git.blame.RepositoryBlameCommand;

public class GitBlameUtils {

  private GitBlameUtils() {
    // Utility class
  }

  public static SonarLintBlameResult blameWithFilesGitCommand(Repository repo, Set<Path> gitRelativePath) {
    var pathStrings = gitRelativePath.stream().map(Path::toString).collect(Collectors.toSet());
    RepositoryBlameCommand blameCommand = new RepositoryBlameCommand(repo)
      .setTextComparator(RawTextComparator.WS_IGNORE_ALL)
      .setMultithreading(true)
      .setFilePaths(pathStrings);
    try {
      var blameResult = blameCommand.call();
      return new SonarLintBlameResult(blameResult);
    } catch (GitAPIException e) {
      throw new IllegalStateException("Failed to blame repository files", e);
    }
  }
}
