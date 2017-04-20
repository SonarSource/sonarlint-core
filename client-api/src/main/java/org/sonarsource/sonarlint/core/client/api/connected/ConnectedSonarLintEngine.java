/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2017 SonarSource SA
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
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.exceptions.CanceledException;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalUpdateRequiredException;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;

/**
 * Entry point for SonarLint.
 */
public interface ConnectedSonarLintEngine {

  enum State {
    UNKNOW,
    UPDATING,
    NEVER_UPDATED,
    NEED_UPDATE,
    UPDATED
  }

  State getState();

  void stop(boolean deleteStorage);

  void addStateListener(StateListener listener);

  void removeStateListener(StateListener listener);

  /**
   * Return rule details.
   * @param organizationKey Optional organization key
   * @param ruleKey See {@link Issue#getRuleKey()}
   * @return Rule details
   * @throws IllegalArgumentException if ruleKey is unknown
   */
  RuleDetails getRuleDetails(@Nullable String organizationKey, String ruleKey);

  /**
   * Trigger an analysis
   */
  AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener);

  AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener, @Nullable LogOutput logOutput);

  /**
   * Gets locally stored server issues for a given file. 
   * @param projectId to which the project is bound (must have been previously updated with {@link #updateModule(ServerConfiguration,String)})
   * @param filePath relative to the module to which the moduleKey refers.
   * @return All server issues in the local storage for the given file. If file has no issues, an empty list is returned.
   */
  List<ServerIssue> getServerIssues(ProjectId projectId, String filePath);

  /**
   * Get information about current global storage state
   * @return null if storage was never updated
   */
  @CheckForNull
  GlobalStorageStatus getGlobalStorageStatus();

  /**
   * Get information about project storage state
   * @return null if project was never updated
   */
  @CheckForNull
  ProjectStorageStatus getProjectStorageStatus(ProjectId project);

  /**
   * Return all modules by key
   */
  Map<String, RemoteModule> allModulesByKey();

  // REQUIRES SERVER TO BE REACHABLE

  /**
   * Attempts to download and store the list of modules and to return all modules by key
   * @throws DownloadException if it fails to download
   */
  Map<String, RemoteModule> downloadAllModules(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor);

  /**
   * Update global storage for the given server.
   * @throws UnsupportedServerException if server version is too low
   * @throws CanceledException if the update task was cancelled
   */
  GlobalStorageStatus updateGlobalStorage(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor);

  /**
   * Update given project storage.
   * @param forceUpdate if <code>true</code> then global and organization storage will be also updated
   */
  void updateProjectStorage(ServerConfiguration serverConfig, ProjectId projectId, boolean forceUpdate, @Nullable ProgressMonitor monitor);

  /**
   * Check server to see if global storage need updates.
   * @throws GlobalUpdateRequiredException if global storage is not initialized or stale (see {@link #getGlobalStorageStatus()})
   * @throws DownloadException if it fails to download
   */
  StorageUpdateCheckResult checkIfGlobalStorageNeedUpdate(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor);

  /**
   * Check server to see if project storage need updates.
   * @throws StorageException if project storage is not initialized or stale (see {@link #getProjectStorageStatus(String)})
   * @throws DownloadException if it fails to download
   */
  StorageUpdateCheckResult checkIfProjectStorageNeedUpdate(ServerConfiguration serverConfig, ProjectId projectId, @Nullable ProgressMonitor monitor);

  /**
   * Downloads, stores and returns server issues for a given file. 
   * @param projectId to which the project is bound (must have been previously updated with {@link #updateModule(ServerConfiguration,String)})
   * @param filePath relative to the module to which the moduleKey refers.
   * @return All server issues in the local storage for the given file. If file has no issues, an empty list is returned.
   * @throws DownloadException if it fails to download
   */
  List<ServerIssue> downloadServerIssues(ServerConfiguration serverConfig, ProjectId projectId, String filePath, @Nullable ProgressMonitor monitor);

  /**
   * Downloads and stores server issues for a given project.
   * @param serverConfig from which to download issues
   * @param projectId to which the project is bound (must have been previously updated with {@link #updateModule(ServerConfiguration,String)})
   */
  void downloadServerIssues(ServerConfiguration serverConfig, ProjectId project, @Nullable ProgressMonitor monitor);

}
