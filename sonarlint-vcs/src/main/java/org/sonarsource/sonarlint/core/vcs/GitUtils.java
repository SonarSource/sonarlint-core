/*
 * SonarLint Version Control System
 * Copyright (C) 2016-2022 SonarSource SA
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;


public class GitUtils {

  private static final String BRANCH_REF_PREFIX = "refs/heads/";

  private GitUtils() {
    // util class
  }

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public static List<String> getCommitNamesForRef(String branchName, Git git) {
    var commitNames = new ArrayList<String>();
    try {
      var branchObjectId = git.getRepository().resolve(branchName);
      if (branchObjectId == null) {
        return commitNames;
      }
      var commits = git.log().add(branchObjectId).call();
      for (RevCommit commit : commits) {
        commitNames.add(commit.getName());
      }
    } catch (IncorrectObjectTypeException e) {
      // do nothing, jgit will throw it if branchName can not be resolved to any commit
    } catch (GitAPIException | IOException e) {
      LOG.error("Couldn't fetch commits for branch " + branchName, e);
    }
    return commitNames;
  }

  @CheckForNull
  public static Git getGitForDir(URI projectDirUri) {
    try {
      var builder = new FileRepositoryBuilder();
      var gitUri = new URI(projectDirUri + "/.git");
      var repository = builder.setGitDir(new File(gitUri)).setMustExist(true).build();
      return new Git(repository);
    } catch (IOException | URISyntaxException e) {
      LOG.error("Couldn't access repository for path " + projectDirUri.getPath());
    }
    return null;
  }


  public static Optional<String> electSQBranchForLocalBranch(String branchName, Git git, Set<ServerBranch> serverCandidates) {
    Set<String> serverCandidateNames = serverCandidates.stream().map(ServerBranch::getName).collect(Collectors.toSet());
    Map<String, List<String>> commitsCache = buildCommitsCache(git);
    if (commitsCache.isEmpty()) {
      return Optional.empty();
    }
    List<String> commitNamesForBranch = getCommitNamesForRef(branchName, git);
    Optional<String> mainBranchName = serverCandidates.stream().filter(ServerBranch::isMain).map(ServerBranch::getName).findFirst();
    if (mainBranchName.isEmpty()) {
      return Optional.empty();
    }
    List<String> listOfLocalCandidates;
    for (String commitName : commitNamesForBranch) {
      if (commitsCache.containsKey(commitName)) {
        listOfLocalCandidates = commitsCache.get(commitName);
        if (listOfLocalCandidates.contains(mainBranchName.get())) {
          return mainBranchName;
        }
        for (String localCandidateName : listOfLocalCandidates) {
          if (serverCandidateNames.contains(localCandidateName)) {
            return Optional.of(localCandidateName);
          }
        }
      }
    }
    return mainBranchName;
  }

  public static Map<String, List<String>> buildCommitsCache(Git git) {
    List<Ref> refs;
    try {
      refs = git.getRepository().getRefDatabase().getRefs();
    } catch (IOException e) {
      LOG.error("Unable to build commits " + e);
      return Collections.emptyMap();
    }
    Map<String, List<String>> commitToBranches = new HashMap<>();
    for (Ref ref : refs) {
      List<String> commitNamesForBranch = GitUtils.getCommitNamesForRef(ref.getName(), git);
      for (String commitName : commitNamesForBranch) {
        commitToBranches.putIfAbsent(commitName, new ArrayList<>());
        if (ref.getName().startsWith(BRANCH_REF_PREFIX)) {
          commitToBranches.get(commitName).add(ref.getName().substring(BRANCH_REF_PREFIX.length()));
        }
      }
    }
    return commitToBranches;
  }

}
