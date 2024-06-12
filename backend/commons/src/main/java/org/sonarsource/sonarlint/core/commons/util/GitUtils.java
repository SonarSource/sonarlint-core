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
package org.sonarsource.sonarlint.core.commons.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.jgit.ignore.IgnoreNode;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.sonarsource.sonarlint.core.commons.SonarLintGitIgnore;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.util.gitblame.GitRepoNotFoundException;

import static java.util.Optional.ofNullable;
import static org.eclipse.jgit.lib.Constants.GITIGNORE_FILENAME;

public class GitUtils {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private GitUtils() {
    // Utility class
  }

  public static Repository buildGitRepository(Path basedir) {
    try {
      var repositoryBuilder = new RepositoryBuilder()
        .findGitDir(basedir.toFile());
      if (ofNullable(repositoryBuilder.getGitDir()).isEmpty()) {
        throw new GitRepoNotFoundException(basedir.toString());
      }

      var repository = repositoryBuilder.build();
      try (ObjectReader objReader = repository.getObjectDatabase().newReader()) {
        // SONARSCGIT-2 Force initialization of shallow commits to avoid later concurrent modification issue
        objReader.getShallowCommits();
        return repository;
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to open Git repository", e);
    }
  }

  /**
   * Assumes the supplied {@param baseDir} or some of its parents is a git repository.
   * If error occurs during parsing .gitignore file then an ignore node with no rules is created -> Files checked against this node will be considered as not ignored.
   */
  public static SonarLintGitIgnore createSonarLintGitIgnore(Path baseDir) {
    try {
      var gitRepo = buildGitRepository(baseDir);
      var gitRepoRelativeProjectBaseDir = gitRepo.getWorkTree().toPath().relativize(baseDir);
      var ignoreNode = buildIgnoreNode(gitRepo);
      return new SonarLintGitIgnore(ignoreNode, gitRepoRelativeProjectBaseDir);
    } catch (GitRepoNotFoundException e) {
      LOG.info("Git Repository not found for {}. The path {} is not in a Git repository", baseDir, e.getPath());
    }
    return new SonarLintGitIgnore(new IgnoreNode(), baseDir);
  }

  private static IgnoreNode buildIgnoreNode(Repository repository) {
    var rootDir = repository.getWorkTree();
    var gitIgnoreFile = new File(rootDir, GITIGNORE_FILENAME);
    var ignoreNode = new IgnoreNode();
    try (var inputStream = new FileInputStream(gitIgnoreFile)) {
      ignoreNode.parse(inputStream);
    } catch (Exception e) {
      LOG.warn("Error occurred while reading .gitignore file: ", e);
      LOG.warn("Building empty ignore node with no rules. Files checked against this node will be considered as not ignored.");
    }
    return ignoreNode;
  }
}
