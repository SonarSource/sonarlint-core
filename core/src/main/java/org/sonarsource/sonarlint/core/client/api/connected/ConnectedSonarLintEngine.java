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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.exceptions.CanceledException;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalStorageUpdateRequiredException;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.plugin.common.log.LogOutput;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.project.ServerProject;

/**
 * Entry point for SonarLint.
 */
public interface ConnectedSonarLintEngine extends SonarLintEngine {

  enum State {
    UNKNOWN,
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
   * Trigger an analysis
   */
  AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, Consumer<Issue> issueListener, @Nullable LogOutput logOutput, @Nullable ProgressMonitor monitor);

  /**
   * Gets locally stored server issues for a given file.
   *
   * @param projectBinding information about the project (must have been previously updated with {@link #updateProject(EndpointParams, HttpClient, String, ProgressMonitor)})
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
  Map<String, ServerProject> allProjectsByKey();

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
  Map<String, ServerProject> downloadAllProjects(EndpointParams endpoint, HttpClient client, @Nullable ProgressMonitor monitor);

  /**
   * Update current server.
   *
   * @throws UnsupportedServerException if server version is too low
   * @throws CanceledException          if the update task was cancelled
   * @since 2.0
   */
  UpdateResult update(EndpointParams endpoint, HttpClient client, @Nullable ProgressMonitor monitor);

  /**
   * Update given project.
   *
   * @since 2.0
   */
  void updateProject(EndpointParams endpoint, HttpClient client, String projectKey, boolean fetchTaintVulnerabilities, @Nullable ProgressMonitor monitor);

  /**
   * Check server to see if global storage need updates.
   *
   * @throws GlobalStorageUpdateRequiredException if global storage is not initialized or stale (see {@link #getGlobalStorageStatus()})
   * @throws DownloadException             if it fails to download
   * @since 2.6
   */
  StorageUpdateCheckResult checkIfGlobalStorageNeedUpdate(EndpointParams endpoint, HttpClient client, @Nullable ProgressMonitor monitor);

  /**
   * Check server to see if project storage need updates.
   *
   * @throws StorageException  if project storage is not initialized or stale (see {@link #getProjectStorageStatus(String)})
   * @throws DownloadException if it fails to download
   * @since 2.6
   */
  StorageUpdateCheckResult checkIfProjectStorageNeedUpdate(EndpointParams endpoint, HttpClient client, String projectKey, @Nullable ProgressMonitor monitor);

  /**
   * Downloads, stores and returns server issues for a given file.
   *
   * @param projectBinding information about the project (must have been previously updated with {@link #updateProject(EndpointParams, HttpClient, String, ProgressMonitor)})
   * @param ideFilePath    relative to the project in the IDE.
   * @return All server issues in the local storage for the given file. If file has no issues, an empty list is returned.
   * @throws DownloadException if it fails to download
   * @since 2.5
   */
  List<ServerIssue> downloadServerIssues(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath,
    boolean fetchTaintVulnerabilities, @Nullable ProgressMonitor monitor);

  /**
   * Downloads and stores server issues for a given project.
   *
   * @param endpoint from which to download issues
   * @param projectKey   key of the project (must have been previously updated with {@link #updateProject(EndpointParams, HttpClient, String, ProgressMonitor)})
   * @since 2.9
   */
  void downloadServerIssues(EndpointParams endpoint, HttpClient client, String projectKey, boolean fetchTaintVulnerabilities, @Nullable ProgressMonitor monitor);

  /**
   * Get a list of files that are excluded from analysis, out of the provided files.
   *
   * @param projectBinding    Specifies the binding to which the files belong.
   * @param files             The files that will be process to detect which ones are excluded from analysis
   * @param ideFilePathExtractor Provides a IDE path of each file. The path will be processes using the binding prefixes.
   * @param testFilePredicate Indicates whether a file is a test file.
   * @return The list of files that are excluded from analysis.
   */
  <G> List<G> getExcludedFiles(ProjectBinding projectBinding, Collection<G> files, Function<G, String> ideFilePathExtractor, Predicate<G> testFilePredicate);

}
