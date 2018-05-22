/*
 * SonarLint Language Server
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarlint.languageserver;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.tracking.CachingIssueTracker;
import org.sonarsource.sonarlint.core.tracking.CachingIssueTrackerImpl;
import org.sonarsource.sonarlint.core.tracking.InMemoryIssueTrackerCache;
import org.sonarsource.sonarlint.core.tracking.IssueTrackable;
import org.sonarsource.sonarlint.core.tracking.IssueTrackerCache;
import org.sonarsource.sonarlint.core.tracking.Logger;
import org.sonarsource.sonarlint.core.tracking.Trackable;

import static org.sonarsource.sonarlint.core.client.api.util.FileUtils.toSonarQubePath;

class ServerIssueTracker {

  private final ConnectedSonarLintEngine engine;
  private final ServerConfiguration serverConfiguration;
  private final String moduleKey;
  private final Path baseDir;
  private final Logger logger;

  ServerIssueTracker(ConnectedSonarLintEngine engine, ServerConfiguration serverConfiguration, String moduleKey, Path baseDir, Logger logger) {
    this.engine = engine;
    this.serverConfiguration = serverConfiguration;
    this.moduleKey = moduleKey;
    this.baseDir = baseDir;
    this.logger = logger;
  }

  Collection<Issue> matchAndTrack(String filePath, Collection<Issue> issues) {
    engine.downloadServerIssues(serverConfiguration, moduleKey, toSonarQubePath(filePath));

    Collection<Issue> issuesWithFile = issues.stream().filter(issue -> issue.getInputFile() != null).collect(Collectors.toList());
    Collection<String> relativePaths = getRelativePaths(baseDir, issuesWithFile);
    Map<String, List<Trackable>> trackablesPerFile = getTrackablesPerFile(baseDir, issuesWithFile);
    IssueTrackerCache cache = createCurrentIssueTrackerCache(relativePaths, trackablesPerFile);

    return getCurrentIssues(relativePaths, cache);
  }

  private IssueTrackerCache createCurrentIssueTrackerCache(Collection<String> relativePaths, Map<String, List<Trackable>> trackablesPerFile) {
    IssueTrackerCache cache = new InMemoryIssueTrackerCache();
    CachingIssueTracker issueTracker = new CachingIssueTrackerImpl(cache);
    trackablesPerFile.forEach(issueTracker::matchAndTrackAsNew);
    org.sonarsource.sonarlint.core.tracking.ServerIssueTracker serverIssueTracker = new org.sonarsource.sonarlint.core.tracking.ServerIssueTracker(logger, issueTracker);
    serverIssueTracker.update(engine, moduleKey, relativePaths);
    return cache;
  }

  private static Collection<Issue> getCurrentIssues(Collection<String> relativePaths, IssueTrackerCache cache) {
    return relativePaths.stream().flatMap(f -> cache.getCurrentTrackables(f).stream())
      .filter(trackable -> !trackable.isResolved())
      .map(trackable -> new DelegatingIssue(trackable.getIssue()) {
        @Override
        public String getSeverity() {
          return trackable.getSeverity();
        }

        @CheckForNull
        @Override
        public String getType() {
          return trackable.getType();
        }
      }).collect(Collectors.toList());
  }

  private static Map<String, List<Trackable>> getTrackablesPerFile(Path baseDirPath, Collection<Issue> issues) {
    return issues.stream()
      .collect(Collectors.groupingBy(issue -> getRelativePath(baseDirPath, issue), Collectors.toList()))
      .entrySet().stream()
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        entry -> entry.getValue().stream()
          .map(IssueTrackable::new)
          .collect(Collectors.toCollection(ArrayList::new))));
  }

  private static Collection<String> getRelativePaths(Path baseDirPath, Collection<Issue> issues) {
    return issues.stream()
      .map(issue -> getRelativePath(baseDirPath, issue))
      .collect(Collectors.toSet());
  }

  // note: engine.downloadServerIssues correctly figures out correct moduleKey and fileKey
  @CheckForNull
  private static String getRelativePath(Path baseDirPath, Issue issue) {
    ClientInputFile inputFile = issue.getInputFile();
    if (inputFile == null) {
      return null;
    }

    return toSonarQubePath(baseDirPath.relativize(Paths.get(inputFile.getPath())).toString());
  }
}
