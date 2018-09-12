/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
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
   * @param ruleKey See {@link Issue#getRuleKey()}
   * @return Rule details
   * @throws IllegalArgumentException if ruleKey is unknown
   * @since 1.2
   */
  RuleDetails getRuleDetails(String ruleKey);

  /**
   * Trigger an analysis
   */
  AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener, @Nullable LogOutput logOutput, @Nullable ProgressMonitor monitor);

  /**
   * Gets locally stored server issues for a given file. 
   * @param moduleKey to which the project is bound (must have been previously updated with {@link #updateModule(ServerConfiguration,String)})
   * @param filePath relative to the module to which the moduleKey refers.
   * @return All server issues in the local storage for the given file. If file has no issues, an empty list is returned.
   */
  List<ServerIssue> getServerIssues(String moduleKey, String filePath);

  /**
   * Get information about current global storage state
   * @return null if storage was never updated
   * @since 2.6
   */
  @CheckForNull
  GlobalStorageStatus getGlobalStorageStatus();

  /**
   * Get information about module storage state
   * @return null if module was never updated
   * @since 2.6
   */
  @CheckForNull
  ProjectStorageStatus getProjectStorageStatus(String moduleKey);

  /**
   * Return all projects by key
   * @since 2.0
   */
  Map<String, RemoteProject> allProjectsByKey();

  // REQUIRES SERVER TO BE REACHABLE

  /**
   * Attempts to download and store the list of projects and to return all projects by key
   * @since 2.5
   * @throws DownloadException if it fails to download
   */
  Map<String, RemoteProject> downloadAllProjects(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor);

  /**
   * Update current server.
   * @since 2.0
   * @throws UnsupportedServerException if server version is too low
   * @throws CanceledException if the update task was cancelled
   */
  UpdateResult update(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor);

  /**
   * Update given module.
   * @since 2.0
   */
  void updateProject(ServerConfiguration serverConfig, String moduleKey, Collection<String> localFilePaths, @Nullable ProgressMonitor monitor);

  /**
   * Check server to see if global storage need updates.
   * @since 2.6
   * @throws GlobalUpdateRequiredException if global storage is not initialized or stale (see {@link #getGlobalStorageStatus()})
   * @throws DownloadException if it fails to download
   */
  StorageUpdateCheckResult checkIfGlobalStorageNeedUpdate(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor);

  /**
   * Check server to see if module storage need updates.
   * @since 2.6
   * @throws StorageException if module storage is not initialized or stale (see {@link #getProjectStorageStatus(String)})
   * @throws DownloadException if it fails to download
   */
  StorageUpdateCheckResult checkIfProjectStorageNeedUpdate(ServerConfiguration serverConfig, String moduleKey, @Nullable ProgressMonitor monitor);

  /**
   * Downloads, stores and returns server issues for a given file. 
   * @param moduleKey to which the project is bound (must have been previously updated with {@link #updateModule(ServerConfiguration,String)})
   * @param filePath relative to the module to which the moduleKey refers.
   * @return All server issues in the local storage for the given file. If file has no issues, an empty list is returned.
   * @since 2.5
   * @throws DownloadException if it fails to download
   */
  List<ServerIssue> downloadServerIssues(ServerConfiguration serverConfig, String moduleKey, String filePath);

  /**
   * Downloads and stores server issues for a given module.
   * @param serverConfig form which to download issues
   * @param moduleKey to which the project is bound (must have been previously updated with {@link #updateModule(ServerConfiguration,String)})
   * @since 2.9
   */
  void downloadServerIssues(ServerConfiguration serverConfig, String moduleKey);

  /**
   * Get information about the analyzers that are currently loaded.
   * Should only be called when engine is started.
   */
  Collection<LoadedAnalyzer> getLoadedAnalyzers();

  Set<String> getExcludedFiles(String moduleKey, Collection<String> filePaths, Predicate<String> testFilePredicate);

}
