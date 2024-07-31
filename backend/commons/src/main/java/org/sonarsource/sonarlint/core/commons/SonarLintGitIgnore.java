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
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import org.eclipse.jgit.ignore.IgnoreNode;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class SonarLintGitIgnore {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final IgnoreNode ignoreNode;
  private final Path gitRepoRelativeProjectBaseDir;

  public SonarLintGitIgnore(IgnoreNode ignoreNode, Path gitRepoRelativeProjectBaseDir) {
    this.ignoreNode = ignoreNode;
    this.gitRepoRelativeProjectBaseDir = gitRepoRelativeProjectBaseDir;
  }

  public boolean isIgnored(URI fileUri, boolean isDirectory) {
    try {
      var fileRelativeToGitRepoPath = gitRepoRelativeProjectBaseDir.resolve(Path.of(fileUri)).toUri().toString();
      var rules = ignoreNode.getRules();
      // Parse rules in the reverse order that they were read because later rules have higher priority
      for (var i = rules.size() - 1; i > -1; i--) {
        var rule = rules.get(i);
        if (rule.isMatch(fileRelativeToGitRepoPath, isDirectory)) {
          return rule.getResult();
        }
      }
      return false;
    } catch (FileSystemNotFoundException e) {
      LOG.debug("Could not resolve file URI to a path. Considering file {} not ignored.", fileUri);
      return false;
    }
  }

  public boolean isFileIgnored(URI fileUri) {
    return isIgnored(fileUri, false);
  }
}
