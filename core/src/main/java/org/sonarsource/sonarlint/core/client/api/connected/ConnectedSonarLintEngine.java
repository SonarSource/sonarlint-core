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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRulesService;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverapi.push.ServerEvent;
import org.sonarsource.sonarlint.core.serverconnection.DownloadException;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

/**
 * Entry point for SonarLint.
 */
public interface ConnectedSonarLintEngine extends SonarLintEngine {

  void stop(boolean deleteStorage);

  /**
   * Return rule details in the context of a given project (severity may have been overridden in the quality profile).
   * @param projectKey if null, the default QP will be considered
   * @deprecated use {@link ActiveRulesService#getActiveRuleDetails(String, String)} instead
   */
  @Deprecated(since = "8.12")
  CompletableFuture<ConnectedRuleDetails> getActiveRuleDetails(EndpointParams endpoint, HttpClient client, String ruleKey, @Nullable String projectKey);

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
   */
  List<ServerIssue> getServerIssues(ProjectBinding projectBinding, String branchName, String filePath);

  /**
   * Gets locally stored server taint issues for a given file.
   *
   * @param projectBinding information about the project (must have been previously updated with {@link #updateProject(EndpointParams, HttpClient, String, ClientProgressMonitor)})
   * @param branchName     branch name
   * @param filePath       relative to the project.
   * @return All server taint issues in the local storage for the given file. If file has no issues, an empty list is returned.
   */
  List<ServerTaintIssue> getServerTaintIssues(ProjectBinding projectBinding, String branchName, String filePath);

  /**
   * Gets locally stored server taint issues.
   *
   * @param projectBinding information about the project (must have been previously updated with {@link #updateProject(EndpointParams, HttpClient, String, ClientProgressMonitor)})
   * @param branchName     branch name
   * @return All server taint issues in the local storage for the given branch.
   */
  List<ServerTaintIssue> getAllServerTaintIssues(ProjectBinding projectBinding, String branchName);

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
   * Attempts to download the list of projects and to return all projects by key
   *
   * @throws DownloadException if it fails to download
   * @since 2.5
   */
  Map<String, ServerProject> downloadAllProjects(EndpointParams endpoint, HttpClient client, @Nullable ClientProgressMonitor monitor);

  void sync(EndpointParams endpoint, HttpClient client, Set<String> projectKeys, @Nullable ClientProgressMonitor monitor);

  /**
   * Update given project.
   *
   * @since 2.0
   */
  void updateProject(EndpointParams endpoint, HttpClient client, String projectKey, @Nullable ClientProgressMonitor monitor);

  /**
   * Downloads and stores server issues for a given file. Starting from SQ 9.6, this is noop as issues updates are coming by SSE.
   *
   * @param projectBinding information about the project (must have been previously updated with {@link #updateProject(EndpointParams, HttpClient, String, ClientProgressMonitor)})
   * @param ideFilePath    relative to the project in the IDE.
   * @throws DownloadException if it fails to download
   */
  void downloadAllServerIssuesForFile(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath, String branchName,
    @Nullable ClientProgressMonitor monitor);

  /**
   * Downloads and stores server taint issues for a given file. Starting from SQ 9.6, this is noop as taint issues updates are coming by SSE.
   *
   * @param projectBinding information about the project (must have been previously updated with {@link #updateProject(EndpointParams, HttpClient, String, ClientProgressMonitor)})
   * @param ideFilePath    relative to the project in the IDE.
   * @throws DownloadException if it fails to download
   */
  void downloadAllServerTaintIssuesForFile(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath, String branchName,
    @Nullable ClientProgressMonitor monitor);

  /**
   * Downloads and stores all server issues for a given project.
   *
   * @param endpoint from which to download issues
   * @param projectKey   key of the project (must have been previously updated with {@link #updateProject(EndpointParams, HttpClient, String, ClientProgressMonitor)})
   */
  void downloadAllServerIssues(EndpointParams endpoint, HttpClient client, String projectKey, String branchName, @Nullable ClientProgressMonitor monitor);

  /**
   * Sync server issues incrementally for a given project (will only work for supported servers).
   *
   * @param endpoint from which to download issues
   * @param projectKey   key of the project
   */
  void syncServerIssues(EndpointParams endpoint, HttpClient client, String projectKey, String branchName, @Nullable ClientProgressMonitor monitor);

  /**
   * Sync server taint issues incrementally for a given project (will only work for supported servers).
   *
   * @param endpoint from which to download issues
   * @param projectKey   key of the project
   */
  void syncServerTaintIssues(EndpointParams endpoint, HttpClient client, String projectKey, String branchName, @Nullable ClientProgressMonitor monitor);

  /**
   * Download all hotspots, regardless of their status, from a project.
   * Download will be made only for servers that return enough data to achieve local hotspot tracking.
   */
  void downloadAllServerHotspots(EndpointParams endpoint, HttpClient client, String projectKey, String branchName, @Nullable ClientProgressMonitor monitor);

  /**
   * Fetch all hotspots, regardless of their status, from a project on the server, and store them in local storage.
   * Download will be made only for servers that return enough data to achieve local hotspot tracking.
   */
  void downloadAllServerHotspotsForFile(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath, String branchName,
    @Nullable ClientProgressMonitor monitor);

  /**
   * Returns locally stored hotspots that were previously downloaded from the server.
   */
  Collection<ServerHotspot> getServerHotspots(ProjectBinding projectBinding, String branchName, String ideFilePath);

  boolean isSecurityHotspotsDetectionSupported();

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

  void subscribeForEvents(EndpointParams endpoint, HttpClient client, Set<String> projectKeys, Consumer<ServerEvent> eventConsumer, @Nullable ClientLogOutput clientLogOutput);

}
