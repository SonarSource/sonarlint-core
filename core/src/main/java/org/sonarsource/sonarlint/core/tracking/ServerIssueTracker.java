/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.tracking;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.issuetracking.CachingIssueTracker;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverconnection.DownloadException;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

public class ServerIssueTracker {

  private static final SonarLintLogger LOGGER = SonarLintLogger.get();

  private final CachingIssueTracker issueTracker;

  public ServerIssueTracker(CachingIssueTracker issueTracker) {
    this.issueTracker = issueTracker;
  }

  public void update(EndpointParams endpoint, HttpClient client, ConnectedSonarLintEngine engine, ProjectBinding projectBinding, Collection<String> fileKeys, String branchName) {
    update(fileKeys, fileKey -> fetchServerIssues(endpoint, client, engine, projectBinding, fileKey, branchName));
  }

  public void update(ConnectedSonarLintEngine engine, ProjectBinding projectBinding, String branchName, Collection<String> fileKeys) {
    update(fileKeys, fileKey -> engine.getServerIssues(projectBinding, branchName, fileKey));
  }

  private void update(Collection<String> fileKeys, Function<String, List<ServerIssue>> issueGetter) {
    try {
      for (String fileKey : fileKeys) {
        var serverIssues = issueGetter.apply(fileKey);
        Collection<Trackable> serverIssuesTrackable = serverIssues.stream().map(ServerIssueTrackable::new).collect(Collectors.toList());
        issueTracker.matchAndTrackAsBase(fileKey, serverIssuesTrackable);
      }
    } catch (Exception e) {
      LOGGER.error("error while fetching and matching server issues", e);
    }
  }

  private static List<ServerIssue> fetchServerIssues(EndpointParams endpoint, HttpClient client, ConnectedSonarLintEngine engine,
    ProjectBinding projectBinding, String ideFilePath, String branchName) {
    try {
      LOGGER.debug("fetchServerIssues projectKey=" + projectBinding.projectKey() + ", ideFilePath=" + ideFilePath + ", branchName=" + branchName);
      return engine.downloadServerIssues(endpoint, client, projectBinding, ideFilePath, branchName, null);
    } catch (DownloadException e) {
      LOGGER.debug("Failed to download server issues", e);
      return engine.getServerIssues(projectBinding, branchName, ideFilePath);
    }
  }
}
