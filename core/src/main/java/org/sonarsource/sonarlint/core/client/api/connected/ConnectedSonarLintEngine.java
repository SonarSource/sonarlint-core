/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.progress.CanceledException;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverapi.exception.UnsupportedServerException;
import org.sonarsource.sonarlint.core.serverconnection.DownloadException;
import org.sonarsource.sonarlint.core.serverconnection.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.serverconnection.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

/**
 * Entry point for SonarLint.
 */
public interface ConnectedSonarLintEngine extends SonarLintEngine {

  void stop(boolean deleteStorage);

  /**
   * Return rule details in the context of a given project (severity may have been overridden in the quality profile).
   * @param projectKey if null, the default QP will be considered
   */
  CompletableFuture<ConnectedRuleDetails> getActiveRuleDetails(EndpointParams endpoint, HttpClient client, String ruleKey, @Nullable String projectKey);

  /**
   * Trigger an analysis
   */
  AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener, @Nullable ClientLogOutput logOutput, @Nullable ClientProgressMonitor monitor);

  /**
   * Gets locally stored server issues for a given file.
   *
   * @param projectBinding information about the project (must have been previously updated with {@link #updateProject(EndpointParams, HttpClient, String, boolean, String, ClientProgressMonitor)})
   * @param filePath       relative to the project.
   * @return All server issues in the local storage for the given file. If file has no issues, an empty list is returned.
   */
  List<ServerIssue> getServerIssues(ProjectBinding projectBinding, String branchName, String filePath);

  /**
   * Gets locally stored server taint issues for a given file.
   *
   * @param projectBinding information about the project (must have been previously updated with {@link #updateProject(EndpointParams, HttpClient, String, boolean, String, ClientProgressMonitor)})
   * @param filePath       relative to the project.
   * @return All server taint issues in the local storage for the given file. If file has no issues, an empty list is returned.
   */
  List<ServerTaintIssue> getServerTaintIssues(ProjectBinding projectBinding, String branchName, String filePath);

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

  /**
   * Returns analyzed branches for a given project. Requires a {@link #sync(EndpointParams, HttpClient, Set, ClientProgressMonitor)} to have been successfully done before for this project.
   *
   * @param projectKey
   * @return branches analyzed on the server for this project.
   */
  ProjectBranches getServerBranches(String projectKey);

  // REQUIRES SERVER TO BE REACHABLE

  /**
   * Attempts to download and store the list of projects and to return all projects by key
   *
   * @throws DownloadException if it fails to download
   * @since 2.5
   */
  Map<String, ServerProject> downloadAllProjects(EndpointParams endpoint, HttpClient client, @Nullable ClientProgressMonitor monitor);

  void sync(EndpointParams endpoint, HttpClient client, Set<String> projectKeys, @Nullable ClientProgressMonitor monitor);

  /**
   * Update current server.
   *
   * @throws UnsupportedServerException if server version is too low
   * @throws CanceledException          if the update task was cancelled
   * @since 2.0
   */
  UpdateResult update(EndpointParams endpoint, HttpClient client, @Nullable ClientProgressMonitor monitor);

  /**
   * Update given project.
   *
   * @since 2.0
   */
  void updateProject(EndpointParams endpoint, HttpClient client, String projectKey, @Nullable String branchName, @Nullable ClientProgressMonitor monitor);

  /**
   * Downloads, stores and returns server issues for a given file.
   *
   * @param projectBinding information about the project (must have been previously updated with {@link #updateProject(EndpointParams, HttpClient, String, boolean, String, ClientProgressMonitor)})
   * @param ideFilePath    relative to the project in the IDE.
   * @return All server issues in the local storage for the given file. If file has no issues, an empty list is returned.
   * @throws DownloadException if it fails to download
   * @since 2.5
   * @deprecated Starting from SQ 9.5 issues are pulled periodically + updated by SSE.
   */
  @Deprecated
  List<ServerIssue> downloadServerIssues(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath, @Nullable String branchName,
    @Nullable ClientProgressMonitor monitor);

  /**
   * Downloads and stores server issues for a given project.
   *
   * @param endpoint from which to download issues
   * @param projectKey   key of the project (must have been previously updated with {@link #updateProject(EndpointParams, HttpClient, String, boolean, String, ClientProgressMonitor)})
   * @since 2.9
   */
  void downloadServerIssues(EndpointParams endpoint, HttpClient client, String projectKey, String branchName, @Nullable ClientProgressMonitor monitor);

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

  void subscribeForEvents(EndpointParams endpoint, HttpClient client, Set<String> projectKeys, @Nullable ClientLogOutput clientLogOutput);

}
