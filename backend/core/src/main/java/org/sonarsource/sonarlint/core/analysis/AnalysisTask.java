/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.analysis;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;

public class AnalysisTask {
  private final UUID analysisId;
  private final TriggerType triggerType;
  private final String configScopeId;
  private final List<URI> filePathsToAnalyze;
  private final Map<String, String> extraProperties;
  private final boolean hotspotsOnly;
  private final CompletableFuture<AnalysisResult> result = new CompletableFuture<>();
  private final ProgressMonitor progressMonitor;
  private final Consumer<RawIssue> issueStreamingListener;
  private final long startTime;
  private final boolean shouldFetchServerIssues;

  public AnalysisTask(UUID analysisId, TriggerType triggerType, String configScopeId, List<URI> filePathsToAnalyze, Map<String, String> extraProperties, boolean hotspotsOnly,
    ProgressMonitor progressMonitor, Consumer<RawIssue> issueStreamingListener, long startTime, boolean shouldFetchServerIssues) {
    this.analysisId = analysisId;
    this.triggerType = triggerType;
    this.configScopeId = configScopeId;
    this.filePathsToAnalyze = filePathsToAnalyze;
    this.extraProperties = extraProperties;
    this.hotspotsOnly = hotspotsOnly;
    this.progressMonitor = progressMonitor;
    this.issueStreamingListener = issueStreamingListener;
    this.startTime = startTime;
    this.shouldFetchServerIssues = shouldFetchServerIssues;
  }

  public TriggerType getTriggerType() {
    return triggerType;
  }

  public String getConfigScopeId() {
    return configScopeId;
  }

  public List<URI> getFilesToAnalyze() {
    return filePathsToAnalyze;
  }

  public Map<String, String> getExtraProperties() {
    return extraProperties;
  }

  public boolean isHotspotsOnly() {
    return hotspotsOnly;
  }

  public CompletableFuture<AnalysisResult> getResult() {
    return result;
  }

  public ProgressMonitor getProgressMonitor() {
    return progressMonitor;
  }

  public UUID getAnalysisId() {
    return analysisId;
  }

  public long getStartTime() {
    return startTime;
  }

  public boolean isShouldFetchServerIssues() {
    return shouldFetchServerIssues;
  }

  public Consumer<RawIssue> getIssueStreamingListener() {
    return issueStreamingListener;
  }
}
