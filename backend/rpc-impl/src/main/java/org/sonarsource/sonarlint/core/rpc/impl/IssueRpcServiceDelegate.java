/*
 * SonarLint Core - RPC Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.issue.IssueService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.AddIssueCommentParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ChangeIssueStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckAnticipatedStatusChangeSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckAnticipatedStatusChangeSupportedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.IssueRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenAllIssuesForFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenAllIssuesForFileResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenIssueResponse;

public class IssueRpcServiceDelegate extends AbstractRpcServiceDelegate implements IssueRpcService {
  public IssueRpcServiceDelegate(SonarLintRpcServerImpl server) {
    super(server);
  }

  @Override
  public CompletableFuture<Void> changeStatus(ChangeIssueStatusParams params) {
    return runAsync(cancelMonitor -> getBean(IssueService.class).changeStatus(params.getConfigurationScopeId(), params.getIssueKey(), params.getNewStatus(), params.isTaintIssue(),
      cancelMonitor), params.getConfigurationScopeId());
  }

  @Override
  public CompletableFuture<Void> addComment(AddIssueCommentParams params) {
    return runAsync(cancelMonitor -> getBean(IssueService.class).addComment(params.getConfigurationScopeId(), params.getIssueKey(), params.getText(), cancelMonitor),
      params.getConfigurationScopeId());
  }

  @Override
  public CompletableFuture<CheckAnticipatedStatusChangeSupportedResponse> checkAnticipatedStatusChangeSupported(CheckAnticipatedStatusChangeSupportedParams params) {
    return requestAsync(cancelMonitor -> new CheckAnticipatedStatusChangeSupportedResponse(
      getBean(IssueService.class).checkAnticipatedStatusChangeSupported(params.getConfigScopeId())), params.getConfigScopeId());
  }

  @Override
  public CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(CheckStatusChangePermittedParams params) {
    return requestAsync(cancelMonitor -> getBean(IssueService.class).checkStatusChangePermitted(params.getConnectionId(), params.getIssueKey(), cancelMonitor));
  }

  @Override
  public CompletableFuture<ReopenIssueResponse> reopenIssue(ReopenIssueParams params) {
    return requestAsync(
      cancelMonitor -> new ReopenIssueResponse(
        getBean(IssueService.class).reopenIssue(params.getConfigurationScopeId(), params.getIssueId(), params.isTaintIssue(), cancelMonitor)),
      params.getConfigurationScopeId());
  }

  @Override
  public CompletableFuture<ReopenAllIssuesForFileResponse> reopenAllIssuesForFile(ReopenAllIssuesForFileParams params) {
    return requestAsync(cancelMonitor -> new ReopenAllIssuesForFileResponse(getBean(IssueService.class).reopenAllIssuesForFile(params, cancelMonitor)),
      params.getConfigurationScopeId());
  }
}
