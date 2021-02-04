/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;

public class ServerIssueTracker {

  private static final Logger LOGGER = Loggers.get(ServerIssueTracker.class);

  private final CachingIssueTracker issueTracker;

  public ServerIssueTracker(CachingIssueTracker issueTracker) {
    this.issueTracker = issueTracker;
  }

  public void update(EndpointParams endpoint, HttpClient client, ConnectedSonarLintEngine engine, ProjectBinding projectBinding,
    Collection<String> fileKeys) {
    update(fileKeys, fileKey -> fetchServerIssues(endpoint, client, engine, projectBinding, fileKey));
  }

  public void update(ConnectedSonarLintEngine engine, ProjectBinding projectBinding, Collection<String> fileKeys) {
    update(fileKeys, fileKey -> engine.getServerIssues(projectBinding, fileKey));
  }

  private void update(Collection<String> fileKeys, Function<String, List<ServerIssue>> issueGetter) {
    try {
      for (String fileKey : fileKeys) {
        List<ServerIssue> serverIssues = issueGetter.apply(fileKey);
        Collection<Trackable> serverIssuesTrackable = serverIssues.stream().map(ServerIssueTrackable::new).collect(Collectors.toList());
        issueTracker.matchAndTrackAsBase(fileKey, serverIssuesTrackable);
      }
    } catch (Exception e) {
      String message = "error while fetching and matching server issues";
      LOGGER.error(message, e);
    }
  }

  private static List<ServerIssue> fetchServerIssues(EndpointParams endpoint, HttpClient client, ConnectedSonarLintEngine engine,
    ProjectBinding projectBinding, String ideFilePath) {
    try {
      LOGGER.debug("fetchServerIssues projectKey=" + projectBinding.projectKey() + ", ideFilePath=" + ideFilePath);
      return engine.downloadServerIssues(endpoint, client, projectBinding, ideFilePath, null);
    } catch (DownloadException e) {
      LOGGER.debug("Failed to download server issues", e);
      return engine.getServerIssues(projectBinding, ideFilePath);
    }
  }
}
