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

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static org.sonarsource.sonarlint.core.commons.GitBlameUtils.buildRepository;

public class GitUtils {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private GitUtils() {
    // Utility class
  }

  /**
   *
   *
   */
  public static List<URI> filterOutIgnoredFiles(Path baseDir, List<URI> filePathsToAnalyze) {
    try {
      var repo = buildRepository(baseDir);
      var ignoredFiles = getIgnoredFiles(repo);
      return filePathsToAnalyze.stream().filter(uri -> !ignoredFiles.contains(uri.getPath())).collect(Collectors.toList());
    } catch (Exception e) {
      LOG.warn("Error occurred while determining files ignored by Git: ", e);
      LOG.warn("Considering all files as not ignored by Git");
      return filePathsToAnalyze;
    }
  }

  private static Set<String> getIgnoredFiles(Repository repository) throws GitAPIException {
    try (var git = new Git(repository)) {
      var status = git.status().call();
      return status.getIgnoredNotInIndex();
    }
  }
}
