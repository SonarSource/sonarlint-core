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
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
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
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static java.util.Optional.ofNullable;
import static org.eclipse.jgit.lib.Constants.GITIGNORE_FILENAME;
import static org.sonarsource.sonarlint.core.commons.util.git.BlameParser.parseBlameOutput;
import static org.sonarsource.sonarlint.core.commons.util.git.WinGitUtils.locateGitOnWindows;

public class GitUtils {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String MINIMUM_REQUIRED_GIT_VERSION = "2.24.0";
  private static final Pattern whitespaceRegex = Pattern.compile("\\s+");
  private static final Pattern semanticVersionDelimiter = Pattern.compile("\\.");
  private static final String BLAME_HISTORY_WINDOW = "--since='6 months ago'";

  // So we only have to make the expensive call once (or at most twice) to get the native Git executable!
  private static boolean checkedForNativeGitExecutable = false;
  private static String nativeGitExecutable = null;

  private GitUtils() {
    // Utility class
  }

  public static SonarLintBlameResult blameWithFilesGitCommand(Path baseDir, Set<Path> gitRelativePath) {
    return blameWithFilesGitCommand(baseDir, gitRelativePath, null);
  }

  public static List<URI> getVSCChangedFiles(@Nullable Path baseDir) {
    if (baseDir == null) {
      return List.of();
    }
    try {
      var repo = buildGitRepository(baseDir);
      var git = new Git(repo);
      var status = git.status().call();
      var uncommitted = status.getUncommittedChanges().stream();
      var untracked = status.getUntracked().stream().filter(f -> !f.equals(GITIGNORE_FILENAME));
      return Stream.concat(uncommitted, untracked)
        .map(file -> baseDir.resolve(file).toUri())
        .toList();
    } catch (GitAPIException | IllegalStateException e) {
      LOG.debug("Git repository access error: ", e);
      return List.of();
    }
  }

  public static SonarLintBlameResult getBlameResult(Path projectBaseDir, Set<Path> projectBaseRelativeFilePaths,
    @Nullable UnaryOperator<String> fileContentProvider, long thresholdDate) {
    return getBlameResult(projectBaseDir, projectBaseRelativeFilePaths, fileContentProvider, GitUtils::checkIfEnabled, thresholdDate);
  }

  static SonarLintBlameResult getBlameResult(Path projectBaseDir, Set<Path> projectBaseRelativeFilePaths, @Nullable UnaryOperator<String> fileContentProvider,
    Predicate<Path> isEnabled, long thresholdDate) {
    if (isEnabled.test(projectBaseDir)) {
      LOG.debug("Using native git blame");
      return blameFromNativeCommand(projectBaseDir, projectBaseRelativeFilePaths, thresholdDate);
    } else {
      LOG.debug("Falling back to JGit git blame");
      return blameWithFilesGitCommand(projectBaseDir, projectBaseRelativeFilePaths, fileContentProvider);
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
    } catch (GitAPIException e) {
      throw new IllegalStateException("Failed to blame repository files", e);
    }
  }

  /**
   * Get the native Git executable by checking for the version of both `git` and `git.exe`. We cache this information
   * to run these expensive processes more than once (or twice in case of Windows).
   */
  private static String getNativeGitExecutable() {
    if (!checkedForNativeGitExecutable) {
      try {
        var executable = getGitExecutable();
        var process = new ProcessBuilder(executable, "--version").start();
        var exitCode = process.waitFor();
        if (exitCode == 0) {
          nativeGitExecutable = executable;
        }
      } catch (IOException e) {
        LOG.debug("Checking for native Git executable failed", e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      checkedForNativeGitExecutable = true;
    }
    return nativeGitExecutable;
  }

  private static String getGitExecutable() throws IOException {
    return SystemUtils.IS_OS_WINDOWS ? locateGitOnWindows() : "git";
  }

  private static String executeGitCommand(Path workingDir, String... command) throws IOException {
    var commandResult = new LinkedList<String>();
    new ProcessWrapperFactory()
      .create(workingDir, commandResult::add, command)
      .execute();
    return String.join(System.lineSeparator(), commandResult);
  }

  public static SonarLintBlameResult blameFromNativeCommand(Path projectBaseDir, Set<Path> projectBaseRelativeFilePaths, long thresholdDate) {
    var nativeExecutable = getNativeGitExecutable();
    if (nativeExecutable != null) {
      for (var relativeFilePath : projectBaseRelativeFilePaths) {
        try {
          var blameHistoryWindow = getBlameHistoryWindow(thresholdDate);
          return parseBlameOutput(executeGitCommand(projectBaseDir,
              nativeExecutable, "blame", blameHistoryWindow, projectBaseDir.resolve(relativeFilePath).toString(), "--line-porcelain", "--encoding=UTF-8"),
            projectBaseDir.resolve(relativeFilePath).toString().replace("\\", "/"), projectBaseDir);
        } catch (IOException e) {
          throw new IllegalStateException("Failed to blame repository files", e);
        }
      }
    }
    throw new IllegalStateException("There is no native Git available");
  }

  private static String getBlameHistoryWindow(long thresholdDate) {
    var blameLimit = Instant.ofEpochMilli(thresholdDate);
    var blameLimitString = "--since='" + blameLimit + "'";
    return thresholdDate > 0 ? blameLimitString : BLAME_HISTORY_WINDOW;
  }

  public static boolean checkIfEnabled(Path projectBaseDir) {
    var nativeExecutable = getNativeGitExecutable();
    if (nativeExecutable == null) {
      return false;
    }
    try {
      var output = executeGitCommand(projectBaseDir, nativeExecutable, "--version");
      return output.contains("git version") && isCompatibleGitVersion(output);
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean isCompatibleGitVersion(String gitVersionCommandOutput) {
    // Due to the danger of argument injection on git blame the use of `--end-of-options` flag is necessary
    // The flag is available only on git versions >= 2.24.0
    var gitVersion = whitespaceRegex
      .splitAsStream(gitVersionCommandOutput)
      .skip(2)
      .findFirst()
      .orElse("");

    var formattedGitVersion = formatGitSemanticVersion(gitVersion);
    return Version.create(formattedGitVersion).compareToIgnoreQualifier(Version.create(MINIMUM_REQUIRED_GIT_VERSION)) >= 0;
  }

  private static String formatGitSemanticVersion(String version) {
    return semanticVersionDelimiter
      .splitAsStream(version)
      .takeWhile(NumberUtils::isCreatable)
      .collect(Collectors.joining("."));
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
}
