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
package org.sonarsource.sonarlint.core.issue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.AddIssueCommentParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ChangeIssueStatusParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.IssueService;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.IssueStatus;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverconnection.StorageService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;

@Named
@Singleton
public class IssueServiceImpl implements IssueService {

  private static final String STATUS_CHANGE_PERMISSION_MISSING_REASON = "Marking an issue as resolved requires the 'Administer Issues' permission";
  private static final Map<IssueStatus, String> transitionByIssueStatus = Map.of(
    IssueStatus.WONT_FIX, "wontfix",
    IssueStatus.FALSE_POSITIVE, "falsepositive");

  private static final Set<String> requiredTransitions = new HashSet<>(transitionByIssueStatus.values());

  private final ConfigurationRepository configurationRepository;
  private final ServerApiProvider serverApiProvider;
  private final StorageService storageService;
  private final TelemetryServiceImpl telemetryService;

  public IssueServiceImpl(ConfigurationRepository configurationRepository, ServerApiProvider serverApiProvider,
    StorageService storageService, TelemetryServiceImpl telemetryService) {
    this.configurationRepository = configurationRepository;
    this.serverApiProvider = serverApiProvider;
    this.storageService = storageService;
    this.telemetryService = telemetryService;
  }

  @Override
  public CompletableFuture<Void> changeStatus(ChangeIssueStatusParams params) {
    var configurationScopeId = params.getConfigurationScopeId();
    var optionalBinding = configurationRepository.getEffectiveBinding(configurationScopeId);
    return optionalBinding
      .flatMap(effectiveBinding -> serverApiProvider.getServerApi(effectiveBinding.getConnectionId()))
      .map(connection -> {
        var reviewStatus = transitionByIssueStatus.get(params.getNewStatus());
        return connection.issue().changeStatusAsync(params.getIssueKey(), reviewStatus)
          .thenAccept(nothing -> {
            storageService.binding(optionalBinding.get())
              .findings()
              .markIssueAsResolved(params.getIssueKey(), params.isTaintIssue());
            telemetryService.issueStatusChanged();
          })
          .exceptionally(throwable -> {
            throw new IssueStatusChangeException(throwable);
          });
      })
      .orElseGet(() -> CompletableFuture.completedFuture(null));
  }

  @Override
  public CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(CheckStatusChangePermittedParams params) {
    var connectionId = params.getConnectionId();
    var serverApiOpt = serverApiProvider.getServerApi(connectionId);
    if (serverApiOpt.isEmpty()) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("Connection with ID '" + connectionId + "' does not exist"));
    }
    return serverApiOpt.get().issue().searchByKey(params.getIssueKey())
      .thenApply(IssueServiceImpl::toResponse);
  }

  private static CheckStatusChangePermittedResponse toResponse(Issues.Issue issue) {
    var permitted = hasChangePermission(issue);
    return new CheckStatusChangePermittedResponse(permitted,
      permitted ? null : STATUS_CHANGE_PERMISSION_MISSING_REASON,
      // even if not permitted, return the possible statuses, if clients still want to show users what's supported
      Arrays.asList(IssueStatus.values()));
  }

  private static boolean hasChangePermission(Issues.Issue issue) {
    // the 2 required transitions are not available when the 'Administer Issues' permission is missing
    // normally the 'Browse' permission is also required, but we assume it's present as the client knows the issue key
    var possibleTransitions = new HashSet<>(issue.getTransitions().getTransitionsList());
    return possibleTransitions.containsAll(requiredTransitions);
  }

  @Override
  public CompletableFuture<Void> addComment(AddIssueCommentParams params) {
    var configurationScopeId = params.getConfigurationScopeId();
    var optionalBinding = configurationRepository.getEffectiveBinding(configurationScopeId);
    return optionalBinding
      .flatMap(effectiveBinding -> serverApiProvider.getServerApi(effectiveBinding.getConnectionId()))
      .map(connection -> {
        var issueKey = params.getIssueKey();
        var text = params.getText();
        return connection.issue().addComment(issueKey, text)
          .exceptionally(throwable -> {
            throw new AddIssueCommentException(throwable);
          });
      })
      .orElseGet(() -> CompletableFuture.completedFuture(null));
  }
}
