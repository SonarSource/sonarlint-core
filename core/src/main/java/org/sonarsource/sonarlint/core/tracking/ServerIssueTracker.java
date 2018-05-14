/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.tracking;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;

public class ServerIssueTracker {

  private final Logger logger;
  private final CachingIssueTracker issueTracker;

  public ServerIssueTracker(Logger logger, CachingIssueTracker issueTracker) {
    this.logger = logger;
    this.issueTracker = issueTracker;
  }

  public void update(ServerConfiguration serverConfiguration, ConnectedSonarLintEngine engine, String moduleKey, Collection<String> fileKeys) {
    update(fileKeys, fileKey -> fetchServerIssues(serverConfiguration, engine, moduleKey, fileKey));
  }

  public void update(ConnectedSonarLintEngine engine, String moduleKey, Collection<String> fileKeys) {
    update(fileKeys, fileKey -> engine.getServerIssues(moduleKey, fileKey));
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
      logger.error(message, e);
    }
  }

  private List<ServerIssue> fetchServerIssues(ServerConfiguration serverConfiguration, ConnectedSonarLintEngine engine, String moduleKey, String fileKey) {
    try {
      logger.debug("fetchServerIssues moduleKey=" + moduleKey + ", fileKey=" + fileKey);
      return engine.downloadServerIssues(serverConfiguration, moduleKey, fileKey);
    } catch (DownloadException e) {
      logger.debug("failed to download server issues", e);
      return engine.getServerIssues(moduleKey, fileKey);
    }
  }
}
