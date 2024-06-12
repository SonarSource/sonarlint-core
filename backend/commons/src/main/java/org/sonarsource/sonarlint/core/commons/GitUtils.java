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
