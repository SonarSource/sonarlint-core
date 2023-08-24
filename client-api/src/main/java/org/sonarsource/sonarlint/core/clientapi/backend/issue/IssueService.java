/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.backend.issue;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

public interface IssueService {

  /**
   * <p> It changes a status of an issue that is existing on the server or local-only. In detail, it is responsible for:
   * <ul>
   *   <li>Changes the status of an issue (identified by {@link ChangeIssueStatusParams#getIssueKey()} )}</li>
   *   <li>Updates the issue status in the local storage</li>
   *   <li>In case of a local-only issue, it stores the issue in the xodus database for local-only issues</li>
   *   <li>Increments the 'issue.status_changed_count' counter for telemetry when issue exists in the server</li>
   * </ul>
   *</p>
   * It silently deals with the following conditions:
   * <ul>
   *   <li>the provided configuration scope (identified by {@link ChangeIssueStatusParams#getConfigurationScopeId()} is unknown</li>
   *   <li>the connection bound to the configuration scope is unknown</li>
   *   <li>the issueKey is not found in the local storage</li>
   * </ul>
   * In those cases a completed future will be returned.
   * </p>
   * <p>
   * It returns a failed future if:
   * <ul>
   *   <li>the issue is not found either on the server or in the local-only storage for issues</li>
   *   <li>there is a communication problem with the server: network outage, server is down, unauthorized</li>
   * </ul>
   * </p>
   */
  @JsonRequest
  CompletableFuture<Void> changeStatus(ChangeIssueStatusParams params);

  /**
   * <p>
   * Adds a new comment to an existing issue (identified by {@link AddIssueCommentParams#getIssueKey()})
   * </p>
   * <p>
   * If no binding is found for the provided configuration scope (identified by {@link AddIssueCommentParams#getConfigurationScopeId()})
   * then returns a future completed with <code>null</code>
   * </p>
   * <p>
   * It returns a failed future if:
   * <ul>
   *   <li>there is a communication problem with the server: network outage, server is down, unauthorized</li>
   * </ul>
   * </p>
   */
  @JsonRequest
  CompletableFuture<Void> addComment(AddIssueCommentParams params);

  /**
   * Checks if the user can change the issue status. They are allowed in two cases:
   * <ul>
   *   <li>If it is a server-matched issue, users need the 'Administer Issues' permission</li>
   *   <li>If it is a local-only issue, the provided connection should link to a SonarQube 10.2+ instance</li>
   * </ul>Also returns the list of allowed statuses.
   * <p>
   * This method will fail if:
   * <ul>
   *   <li>the connectionId provided as a parameter is unknown</li>
   *   <li>there is a communication problem with the server: network outage, server is down, unauthorized</li>
   * </ul>
   * In those cases, a failed future will be returned.
   * </p>
   */
  @JsonRequest
  CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(CheckStatusChangePermittedParams params);

  /**
   * Reopens the issue, two cases are possible:
   * <ul>
   *   <li>If it is a server-matched issue, it is reopened on the server</li>
   *   <li>If it is a local-only issue, it is deleted from the local storage</li>
   * </ul>
   * @return true if issue was found and actually reopened on the server or deleted locally, false otherwise
   */
  @JsonRequest
  CompletableFuture<ReopenIssueResponse> reopenIssue(ReopenIssueParams params);

  /**
   * Notifying server that anticipated issues for given file should be removed and removes them from local storage
   * @return true if entity for file was found and actually deleted and false otherwise
   */
  @JsonRequest
  CompletableFuture<ReopenIssueResponse> reopenAllIssuesForFile(ReopenAllIssuesForFileParams params);
}
