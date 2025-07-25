/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2025 SonarSource SA
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.ignore.IgnoreNode;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.sonar.scm.git.blame.RepositoryBlameCommand;
import org.sonarsource.sonarlint.core.commons.SonarLintBlameResult;
import org.sonarsource.sonarlint.core.commons.SonarLintGitIgnore;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.util.git.exceptions.GitException;
import org.sonarsource.sonarlint.core.commons.util.git.exceptions.GitRepoNotFoundException;

import static java.util.Optional.ofNullable;
import static org.eclipse.jgit.lib.Constants.GITIGNORE_FILENAME;

public class GitService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final NativeGitWrapper nativeGit;

  GitService(NativeGitWrapper nativeGit) {
    this.nativeGit = nativeGit;
  }

  public static GitService create() {
    return new GitService(new NativeGitWrapper());
  }

  public static SonarLintBlameResult blameWithFilesGitCommand(Path baseDir, Set<Path> gitRelativePath) {
    return blameWithFilesGitCommand(baseDir, gitRelativePath, null);
  }

  // Could be optimized to only fetch VCS changed files matching the base dir
  // Currently, it finds all the files of the git repo, even when called against a subfolder
  public static Set<URI> getVCSChangedFiles(@Nullable Path baseDir) {
    if (baseDir == null) {
      return Set.of();
    }
    try {
      var repo = buildGitRepository(baseDir);
      var workTreePath = repo.getWorkTree().toPath();
      var git = new Git(repo);
      var status = git.status().call();
      var uncommitted = status.getUncommittedChanges().stream();
      var untracked = status.getUntracked().stream().filter(f -> !f.equals(GITIGNORE_FILENAME));
      return Stream.concat(uncommitted, untracked)
        .map(workTreePath::resolve)
        .filter(path -> path.normalize().startsWith(baseDir.normalize()))
        .map(Path::toUri)
        .collect(Collectors.toSet());
    } catch (GitAPIException | GitException e) {
      LOG.debug("Git repository access error: ", e);
      return Set.of();
    }
  }

  /**
   * Retrieves the Git remote URL for the origin remote from the repository.
   *
   * @param baseDir the base directory of the project
   * @return Optional containing the remote URL if found, empty otherwise
   */
  @CheckForNull
  public static String getRemoteUrl(@Nullable Path baseDir) {
    if (baseDir == null) {
      return null;
    }

    try {
      var gitRepo = buildGitRepository(baseDir);
      var config = gitRepo.getConfig();

      return config.getString("remote", "origin", "url");
    } catch (GitRepoNotFoundException e) {
      LOG.debug("Git repository not found for {}", baseDir);
      return null;
    } catch (Exception e) {
      LOG.debug("Error retrieving remote URL for {}: {}", baseDir, e.getMessage());
      return null;
    }
  }

  public static SonarLintBlameResult blameWithFilesGitCommand(Path projectBaseDir, Set<Path> projectBaseRelativeFilePaths, @Nullable UnaryOperator<String> fileContentProvider) {
    var gitRepo = buildGitRepository(projectBaseDir);

    var gitRepoRelativeProjectBaseDir = getRelativePath(gitRepo, projectBaseDir);

    var gitRepoRelativeFilePaths = projectBaseRelativeFilePaths.stream()
      .map(gitRepoRelativeProjectBaseDir::resolve)
      .map(Path::toString)
      .map(FilenameUtils::separatorsToUnix)
      .collect(Collectors.toSet());

    var blameCommand = new RepositoryBlameCommand(gitRepo)
      .setTextComparator(RawTextComparator.WS_IGNORE_ALL)
      .setMultithreading(true)
      .setFilePaths(gitRepoRelativeFilePaths);
    ofNullable(fileContentProvider)
      .ifPresent(provider -> blameCommand.setFileContentProvider(adaptToPlatformBasedPath(provider)));

    try {
      var blameResult = blameCommand.call();
      return new SonarLintBlameResult(blameResult, gitRepoRelativeProjectBaseDir);
    } catch (NoHeadException e) {
      // it means that the repository has no commits, so we can't get any blame information
      return SonarLintBlameResult.withEmptyBlameResult(gitRepoRelativeProjectBaseDir);
    } catch (GitAPIException e) {
      throw new IllegalStateException("Failed to blame repository files", e);
    }
  }

  private static Path getRelativePath(Repository gitRepo, Path projectBaseDir) {
    var repoDir = gitRepo.isBare() ? gitRepo.getDirectory() : gitRepo.getWorkTree();
    return repoDir.toPath().relativize(projectBaseDir);
  }

  public static Repository buildGitRepository(Path basedir) {
    try {
      var repositoryBuilder = new RepositoryBuilder()
        .findGitDir(basedir.toFile());
      if (ofNullable(repositoryBuilder.getGitDir()).isEmpty()) {
        throw new GitRepoNotFoundException(basedir.toString());
      }

      var repository = repositoryBuilder.build();
      try (var objReader = repository.getObjectDatabase().newReader()) {
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
  public static SonarLintGitIgnore createSonarLintGitIgnore(@Nullable Path baseDir) {
    if (baseDir == null) {
      return new SonarLintGitIgnore(new IgnoreNode());
    }
    try {
      var gitRepo = buildGitRepository(baseDir);
      var ignoreNode = buildIgnoreNode(gitRepo);
      return new SonarLintGitIgnore(ignoreNode);
    } catch (GitRepoNotFoundException e) {
      LOG.info("Git Repository not found for {}. The path {} is not in a Git repository", baseDir, e.getPath());
    } catch (FileNotFoundException e) {
      LOG.info(".gitignore file was not found for {}", baseDir);
    } catch (Exception e) {
      LOG.warn("Error occurred while reading .gitignore file: ", e);
      LOG.warn("Building empty ignore node with no rules. Files checked against this node will be considered as not ignored.");
    }
    return new SonarLintGitIgnore(new IgnoreNode());
  }

  private static IgnoreNode buildIgnoreNode(Repository repository) throws IOException {
    var ignoreNode = new IgnoreNode();
    if (repository.isBare()) {
      readGitIgnoreFileFromBareRepo(repository, ignoreNode);
    } else {
      readIgnoreFileFromNonBareRepo(repository, ignoreNode);
    }
    return ignoreNode;
  }

  private static void readGitIgnoreFileFromBareRepo(Repository repository, IgnoreNode ignoreNode) throws IOException {
    var loader = readFileContentFromGitRepo(repository, GITIGNORE_FILENAME);
    if (loader.isPresent()) {
      try (InputStream inputStream = loader.get().openStream()) {
        ignoreNode.parse(inputStream);
      }
    }
  }

  private static void readIgnoreFileFromNonBareRepo(Repository repository, IgnoreNode ignoreNode) throws IOException {
    var rootDir = repository.getWorkTree();
    var gitIgnoreFile = new File(rootDir, GITIGNORE_FILENAME);
    try (var inputStream = new FileInputStream(gitIgnoreFile)) {
      ignoreNode.parse(inputStream);
    }
  }

  private static UnaryOperator<String> adaptToPlatformBasedPath(UnaryOperator<String> provider) {
    return unixPath -> {
      var platformBasedPath = Path.of(unixPath).toString();
      return provider.apply(platformBasedPath);
    };
  }

  private static Optional<ObjectLoader> readFileContentFromGitRepo(Repository repository, String fileName) throws IOException {
    var headId = repository.resolve(Constants.HEAD);
    if (headId == null) {
      // No commits in the repository
      return Optional.empty();
    }

    try (var revWalk = new RevWalk(repository)) {
      var commit = revWalk.parseCommit(headId);

      try (var treeWalk = new TreeWalk(repository)) {
        treeWalk.addTree(commit.getTree());
        treeWalk.setRecursive(true);
        treeWalk.setFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(fileName));

        if (!treeWalk.next()) {
          return Optional.empty();
        }

        return Optional.of(repository.open(treeWalk.getObjectId(0)));
      }
    }
  }

  public SonarLintBlameResult getBlameResult(Path projectBaseDir, Set<Path> projectBaseRelativeFilePaths, Set<URI> fileUris,
    @Nullable UnaryOperator<String> fileContentProvider, Instant thresholdDate) {
    return getBlameResult(projectBaseDir, projectBaseRelativeFilePaths, fileUris, fileContentProvider, nativeGit::checkIfNativeGitEnabled, thresholdDate);
  }

  SonarLintBlameResult getBlameResult(Path projectBaseDir, Set<Path> projectBaseRelativeFilePaths, Set<URI> fileUris, @Nullable UnaryOperator<String> fileContentProvider,
    Predicate<Path> isNativeBlameSupported, Instant thresholdDate) {
    if (isNativeBlameSupported.test(projectBaseDir)) {
      LOG.debug("Using native git blame");
      return nativeGit.blameFromNativeCommand(projectBaseDir, fileUris, thresholdDate);
    } else {
      LOG.debug("Falling back to JGit");
      return blameWithFilesGitCommand(projectBaseDir, projectBaseRelativeFilePaths, fileContentProvider);
    }
  }

}
