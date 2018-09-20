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
import java.util.function.Function;
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
   *
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
   *
   * @param projectBinding information about the project (must have been previously updated with {@link #updateProject(ServerConfiguration, String, ProgressMonitor)})
   * @param filePath       relative to the project.
   * @return All server issues in the local storage for the given file. If file has no issues, an empty list is returned.
   */
  List<ServerIssue> getServerIssues(ProjectBinding projectBinding, String filePath);

  /**
   * Get information about current global storage state
   *
   * @return null if storage was never updated
   * @since 2.6
   */
  @CheckForNull
  GlobalStorageStatus getGlobalStorageStatus();

  /**
   * Get information about project storage state
   *
   * @return null if module was never updated
   * @since 2.6
   */
  @CheckForNull
  ProjectStorageStatus getProjectStorageStatus(String projectKey);

  /**
   * Return all projects by key
   *
   * @since 2.0
   */
  Map<String, RemoteProject> allProjectsByKey();

  /**
   * Tries to find the best way to match files in a IDE project with files in the sonarqube project identified
   * with {@code projectKey}, by finding file path prefixes to be used later in other interactions with the project storage.
   * Requires the storage of the project to be up to date.
   *
   * @param ideFilePaths List of relative paths of all file belonging to the project in the IDE
   * @since 3.10
   */
  ProjectBinding calculatePathPrefixes(String projectKey, Collection<String> ideFilePaths);

  // REQUIRES SERVER TO BE REACHABLE

  /**
   * Attempts to download and store the list of projects and to return all projects by key
   *
   * @throws DownloadException if it fails to download
   * @since 2.5
   */
  Map<String, RemoteProject> downloadAllProjects(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor);

  /**
   * Update current server.
   *
   * @throws UnsupportedServerException if server version is too low
   * @throws CanceledException          if the update task was cancelled
   * @since 2.0
   */
  UpdateResult update(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor);

  /**
   * Update given project.
   *
   * @since 2.0
   */
  void updateProject(ServerConfiguration serverConfig, String projectKey, @Nullable ProgressMonitor monitor);

  /**
   * Check server to see if global storage need updates.
   *
   * @throws GlobalUpdateRequiredException if global storage is not initialized or stale (see {@link #getGlobalStorageStatus()})
   * @throws DownloadException             if it fails to download
   * @since 2.6
   */
  StorageUpdateCheckResult checkIfGlobalStorageNeedUpdate(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor);

  /**
   * Check server to see if project storage need updates.
   *
   * @throws StorageException  if project storage is not initialized or stale (see {@link #getProjectStorageStatus(String)})
   * @throws DownloadException if it fails to download
   * @since 2.6
   */
  StorageUpdateCheckResult checkIfProjectStorageNeedUpdate(ServerConfiguration serverConfig, String projectKey, @Nullable ProgressMonitor monitor);

  /**
   * Downloads, stores and returns server issues for a given file.
   *
   * @param projectBinding information about the project (must have been previously updated with {@link #updateProject(ServerConfiguration, String, ProgressMonitor)})
   * @param ideFilePath    relative to the project in the IDE.
   * @return All server issues in the local storage for the given file. If file has no issues, an empty list is returned.
   * @throws DownloadException if it fails to download
   * @since 2.5
   */
  List<ServerIssue> downloadServerIssues(ServerConfiguration serverConfig, ProjectBinding projectBinding, String ideFilePath);

  /**
   * Downloads and stores server issues for a given project.
   *
   * @param serverConfig form which to download issues
   * @param projectKey   key of the project (must have been previously updated with {@link #updateProject(ServerConfiguration, String, ProgressMonitor)})
   * @since 2.9
   */
  void downloadServerIssues(ServerConfiguration serverConfig, String projectKey);

  /**
   * Get information about the analyzers that are currently loaded.
   * Should only be called when engine is started.
   */
  Collection<LoadedAnalyzer> getLoadedAnalyzers();

  <G> List<G> getExcludedFiles(String projectKey, Collection<G> files, Function<G, String> filePathExtractor, Predicate<G> testFilePredicate);

}
