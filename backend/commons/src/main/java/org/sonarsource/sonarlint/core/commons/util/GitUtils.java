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
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.jgit.ignore.FastIgnoreRule;
import org.eclipse.jgit.ignore.IgnoreNode;
import org.eclipse.jgit.lib.Repository;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static org.eclipse.jgit.lib.Constants.GITIGNORE_FILENAME;
import static org.sonarsource.sonarlint.core.commons.util.gitblame.GitBlameUtils.buildGitRepository;

public class GitUtils {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private GitUtils() {
    // Utility class
  }

  /**
   * Assumes the supplied {@param baseDir} or some of its parents is a git repository.
   * The method checks {@param filePathsToAnalyze} against patterns defined in the .gitignore file and returns
   * those that are not ignored.
   * If error occurs during execution then all {@param filePathsToAnalyze} are considered as not ignored.
   */
  public static List<URI> filterOutIgnoredFiles(Path baseDir, List<URI> filePathsToAnalyze) {
    try {
      var gitRepo = buildGitRepository(baseDir);
      var gitRepoRelativeProjectBaseDir = gitRepo.getWorkTree().toPath().relativize(baseDir);
      var ignoreNode = buildIgnoreNode(gitRepo);
      return ignoreNode.getRules().isEmpty() ? filePathsToAnalyze : filePathsToAnalyze.stream().filter(uri -> {
        var fileRelativeToGitRepoPath = gitRepoRelativeProjectBaseDir.resolve(Path.of(uri)).toUri().toString();
        var checkIgnored = checkIgnored(ignoreNode.getRules(), fileRelativeToGitRepoPath);
        return !checkIgnored.orElse(false);
      }).collect(Collectors.toList());
    } catch (Exception e) {
      LOG.warn("Error occurred while determining files ignored by Git: ", e);
      LOG.warn("Considering all files as not ignored by Git");
      return filePathsToAnalyze;
    }
  }

  private static IgnoreNode buildIgnoreNode(Repository repository) throws IOException {
    var rootDir = repository.getDirectory().getParentFile();
    var gitIgnoreFile = new File(rootDir, GITIGNORE_FILENAME);
    var ignoreNode = new IgnoreNode();
    try (var inputStream = new FileInputStream(gitIgnoreFile)) {
      ignoreNode.parse(inputStream);
    }
    return ignoreNode;
  }

  private static Optional<Boolean> checkIgnored(List<FastIgnoreRule> rules, String entryPath) {
    // Parse rules in the reverse order that they were read because later rules have higher priority
    for (var i = rules.size() - 1; i > -1; i--) {
      var rule = rules.get(i);
      if (rule.isMatch(entryPath, false)) {
        return Optional.of(rule.getResult());
      }
    }
    return Optional.empty();
  }
}
