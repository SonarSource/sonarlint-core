/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;

/**
 * @deprecated Use {@link AnalysisRpcService#analyzeFilesAndTrack(AnalyzeFilesAndTrackParams)} instead.
 */
@Deprecated(since = "10.2")
public class TrackWithServerIssuesParams {
  private final String configurationScopeId;
  private final Map<Path, List<ClientTrackedFindingDto>> clientTrackedIssuesByIdeRelativePath;
  private final boolean shouldFetchIssuesFromServer;

  public TrackWithServerIssuesParams(String configurationScopeId, Map<Path, List<ClientTrackedFindingDto>> clientTrackedIssuesByIdeRelativePath,
    boolean shouldFetchIssuesFromServer) {
    this.configurationScopeId = configurationScopeId;
    this.clientTrackedIssuesByIdeRelativePath = clientTrackedIssuesByIdeRelativePath;
    this.shouldFetchIssuesFromServer = shouldFetchIssuesFromServer;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public Map<Path, List<ClientTrackedFindingDto>> getClientTrackedIssuesByIdeRelativePath() {
    return clientTrackedIssuesByIdeRelativePath;
  }

  public boolean shouldFetchIssuesFromServer() {
    return shouldFetchIssuesFromServer;
  }
}
