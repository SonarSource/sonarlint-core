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
   * <p> It changes a status of an existing issues. In detail, it is responsible for:
   * <ul>
   *   <li>Changes the status of an existing issue (identified by {@link ChangeIssueStatusParams#getIssueKey()} )}</li>
   *   <li>Updates the issue status in the local storage</li>
   *   <li>Increments the 'issue.status_changed_count' counter for telemetry</li>
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
   * Checks if the user has permission to change the issue status. Also returns the list of allowed statuses.
   * <p>
   * This method will fail if:
   * <ul>
   *   <li>the connectionId provided as a parameter is unknown</li>
   * </ul>
   * In those cases, a failed future will be returned.
   * </p>
   */
  @JsonRequest
  CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(CheckStatusChangePermittedParams params);
}
