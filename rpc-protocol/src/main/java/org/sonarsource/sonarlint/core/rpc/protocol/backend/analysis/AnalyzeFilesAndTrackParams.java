/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AnalyzeFilesAndTrackParams {
  private final String configurationScopeId;
  // this is a random id provided by the client that will be used to correlate the raw issues notified back to the client
  private final UUID analysisId;
  private final List<URI> filesToAnalyze;
  private final Map<String, String> extraProperties;
  private final boolean shouldFetchServerIssues;

  /**
   * @deprecated As analysis might not execute straight away, the startTime parameter is not relevant, it doesn't help measure the duration of the actual analysis.
   * Use the other constructor instead.
   */
  @Deprecated(since = "10.18")
  public AnalyzeFilesAndTrackParams(String configurationScopeId, UUID analysisId, List<URI> filesToAnalyze, Map<String, String> extraProperties, boolean shouldFetchServerIssues,
    long startTime) {
    this(configurationScopeId, analysisId, filesToAnalyze, extraProperties, shouldFetchServerIssues);
  }

  public AnalyzeFilesAndTrackParams(String configurationScopeId, UUID analysisId, List<URI> filesToAnalyze, Map<String, String> extraProperties,
    boolean shouldFetchServerIssues) {
    this.configurationScopeId = configurationScopeId;
    this.analysisId = analysisId;
    this.filesToAnalyze = filesToAnalyze;
    this.extraProperties = extraProperties;
    this.shouldFetchServerIssues = shouldFetchServerIssues;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public UUID getAnalysisId() {
    return analysisId;
  }

  public List<URI> getFilesToAnalyze() {
    return filesToAnalyze;
  }

  public Map<String, String> getExtraProperties() {
    return extraProperties;
  }

  public boolean isShouldFetchServerIssues() {
    return shouldFetchServerIssues;
  }
}
