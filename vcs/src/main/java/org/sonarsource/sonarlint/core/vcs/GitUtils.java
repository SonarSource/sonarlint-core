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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import javax.annotation.CheckForNull;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkUtils;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class GitUtils {

  private GitUtils() {
    // util class
  }

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  @CheckForNull
  public static Repository getRepositoryForDir(Path projectDir) {
    try {
      var builder = new RepositoryBuilder()
        .findGitDir(projectDir.toFile())
        .setMustExist(true);
      if (builder.getGitDir() == null) {
        LOG.error("Not inside a Git work tree: " + projectDir);
        return null;
      }
      return builder.build();
    } catch (IOException e) {
      LOG.error("Couldn't access repository for path " + projectDir, e);
    }
    return null;
  }

  public static String electBestMatchingServerBranchForCurrentHead(Repository repo, Set<String> serverCandidateNames, String serverMainBranch) {
    try {
      Ref head = repo.exactRef(Constants.HEAD);
      if (head == null) {
        return serverMainBranch;
      }

      String bestBranch = serverMainBranch;
      int bestDistance = Integer.MAX_VALUE;
      for (String serverBranchName : serverCandidateNames) {
        String shortBranchName = Repository.shortenRefName(serverBranchName);
        String localFullBranchName = Constants.R_HEADS + shortBranchName;

        Ref branchRef = repo.exactRef(localFullBranchName);
        if (branchRef == null) {
          continue;
        }

        int distance = distance(repo, head, branchRef);
        if (distance < bestDistance) {
          bestBranch = serverBranchName;
          bestDistance = distance;
        }

      }

      return bestBranch;
    } catch (IOException e) {
      LOG.error("Couldn't find best matching branch", e);
      return serverMainBranch;
    }
  }

  private static int distance(Repository repository, Ref from, Ref to) throws IOException {

    try (RevWalk walk = new RevWalk(repository)) {

      RevCommit fromCommit = walk.parseCommit(from.getObjectId());
      RevCommit toCommit = walk.parseCommit(to.getObjectId());

      walk.setRevFilter(RevFilter.MERGE_BASE);
      walk.markStart(fromCommit);
      walk.markStart(toCommit);
      RevCommit mergeBase = walk.next();

      walk.reset();
      walk.setRevFilter(RevFilter.ALL);
      int aheadCount = RevWalkUtils.count(walk, fromCommit, mergeBase);
      int behindCount = RevWalkUtils.count(walk, toCommit,
        mergeBase);

      return aheadCount + behindCount;
    }
  }

}
