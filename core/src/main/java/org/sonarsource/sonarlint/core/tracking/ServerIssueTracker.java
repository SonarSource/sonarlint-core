/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.issuetracking.CachingIssueTracker;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverconnection.DownloadException;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;

public class ServerIssueTracker {

  private static final SonarLintLogger LOGGER = SonarLintLogger.get();
  private final CachingIssueTracker hotspotTracker;

  public ServerIssueTracker(CachingIssueTracker hotspotTracker) {
    this.hotspotTracker = hotspotTracker;
  }

  public void update(EndpointParams endpoint, HttpClient client, ConnectedSonarLintEngine engine, ProjectBinding projectBinding, Collection<String> fileKeys, String branchName) {
    update(fileKeys, fileKey -> fetchServerHotspots(endpoint, client, engine, projectBinding, fileKey, branchName));
  }

  public void update(ConnectedSonarLintEngine engine, ProjectBinding projectBinding, String branchName, Collection<String> fileKeys) {
    update(fileKeys, fileKey -> engine.getServerHotspots(projectBinding, branchName, fileKey));
  }

  private void update(Collection<String> fileKeys, Function<String, Collection<ServerHotspot>> hotspotsGetter) {
    try {
      for (String fileKey : fileKeys) {
        var serverHotspots = hotspotsGetter.apply(fileKey);
        Collection<Trackable> serverHotspotsTrackable = serverHotspots.stream().map(ServerHotspotTrackable::new).collect(Collectors.toList());
        hotspotTracker.matchAndTrackAsBase(fileKey, serverHotspotsTrackable);
      }
    } catch (Exception e) {
      LOGGER.error("error while fetching and matching server issues", e);
    }
  }

  private static Collection<ServerHotspot> fetchServerHotspots(EndpointParams endpoint, HttpClient client, ConnectedSonarLintEngine engine,
    ProjectBinding projectBinding, String ideFilePath, String branchName) {
    try {
      LOGGER.debug("fetchServerHotspots projectKey=" + projectBinding.projectKey() + ", ideFilePath=" + ideFilePath + ", branchName=" + branchName);
      engine.downloadAllServerHotspotsForFile(endpoint, client, projectBinding, ideFilePath, branchName, null);
    } catch (DownloadException e) {
      LOGGER.debug("Failed to download server hotspots", e);
    }
    return engine.getServerHotspots(projectBinding, branchName, ideFilePath);
  }
}
