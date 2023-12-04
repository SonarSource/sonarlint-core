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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

/**
 * Entry point for SonarLint.
 */
public interface ConnectedSonarLintEngine extends SonarLintEngine {

  void stop();

  /**
   * Trigger an analysis
   */
  AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener, @Nullable ClientLogOutput logOutput, @Nullable ClientProgressMonitor monitor);

  /**
   * Gets locally stored server issues for a given file.
   *
   * @param projectBinding information about the project (must have been previously updated with {@link #updateProject(EndpointParams, HttpClient, String, ClientProgressMonitor)})
   * @param filePath       relative to the project.
   * @return All server issues in the local storage for the given file. If file has no issues, an empty list is returned.
   * @deprecated tracking is managed by the new backend
   */
  @Deprecated(since = "9.3")
  List<ServerIssue> getServerIssues(ProjectBinding projectBinding, String branchName, String filePath);

  // REQUIRES SERVER TO BE REACHABLE

  /**
   * @deprecated synchronization is managed by the new backend
   */
  @Deprecated(since = "10.0")
  void sync(EndpointParams endpoint, HttpClient client, Set<String> projectKeys, @Nullable ClientProgressMonitor monitor);

  /**
   * Update given project.
   *
   * @since 2.0
   * @deprecated synchronization is managed by the new backend
   */
  @Deprecated(since = "10.0")
  void updateProject(EndpointParams endpoint, HttpClient client, String projectKey, @Nullable ClientProgressMonitor monitor);
}
