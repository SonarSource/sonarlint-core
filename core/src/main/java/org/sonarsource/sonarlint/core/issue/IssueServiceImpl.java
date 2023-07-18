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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.AddIssueCommentParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ChangeIssueStatusParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.IssueService;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.IssueStatus;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueStorageService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverconnection.StorageService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;
import org.sonarsource.sonarlint.core.tracking.LocalOnlyIssueRepository;

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
  private final LocalOnlyIssueStorageService localOnlyIssueStorageService;
  private final LocalOnlyIssueRepository localOnlyIssueRepository;
  private final TelemetryServiceImpl telemetryService;

  public IssueServiceImpl(ConfigurationRepository configurationRepository, ServerApiProvider serverApiProvider,
    StorageService storageService, LocalOnlyIssueStorageService localOnlyIssueStorageService,
    TelemetryServiceImpl telemetryService, LocalOnlyIssueRepository localOnlyIssueRepository) {
    this.configurationRepository = configurationRepository;
    this.serverApiProvider = serverApiProvider;
    this.storageService = storageService;
    this.localOnlyIssueStorageService = localOnlyIssueStorageService;
    this.localOnlyIssueRepository = localOnlyIssueRepository;
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
        var binding = optionalBinding.get();
        var projectServerIssueStore = storageService.binding(binding).findings();
        var issueKey = params.getIssueKey();
        boolean isServerIssue = projectServerIssueStore.containsIssue(issueKey, params.isTaintIssue());
        if (isServerIssue) {
          return connection.issue().changeStatusAsync(issueKey, reviewStatus)
            .thenAccept(nothing -> {
              projectServerIssueStore.markIssueAsResolved(issueKey, params.isTaintIssue());
              telemetryService.issueStatusChanged();
            })
            .exceptionally(throwable -> {
              throw new IssueStatusChangeException(throwable);
            });
        }
        return asUUID(issueKey)
          .flatMap(localOnlyIssueRepository::findByKey)
          .map(issue -> {
            var coreStatus = org.sonarsource.sonarlint.core.commons.IssueStatus.valueOf(params.getNewStatus().name());
            issue.resolve(coreStatus);
            var localOnlyIssueStore = localOnlyIssueStorageService.get();
            return connection.issue()
              .anticipateTransitions(binding.getSonarProjectKey(), concat(localOnlyIssueStore.load(configurationScopeId, issue.getServerRelativePath()), issue))
              .thenAccept(nothing -> localOnlyIssueStore.storeLocalOnlyIssue(params.getConfigurationScopeId(), issue));
          }).orElseThrow(() -> new IssueStatusChangeException("Issue key " + issueKey + " was not found"));
      })
      .orElseGet(() -> CompletableFuture.completedFuture(null));
  }

  private static List<LocalOnlyIssue> concat(List<LocalOnlyIssue> issues, LocalOnlyIssue issue) {
    return Stream.concat(issues.stream(), Stream.of(issue)).collect(Collectors.toList());
  }

  @Override
  public CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(CheckStatusChangePermittedParams params) {
    var connectionId = params.getConnectionId();
    var serverApiOpt = serverApiProvider.getServerApi(connectionId);
    if (serverApiOpt.isEmpty()) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("Connection with ID '" + connectionId + "' does not exist"));
    }
    var issueKey = params.getIssueKey();
    return asUUID(issueKey)
      .flatMap(localOnlyIssueRepository::findByKey)
      .map(r -> {
        // always permitted to change the status, might fail later when pushing to SQ
        return CompletableFuture.completedFuture(toResponse(true));
      })
      .orElseGet(() -> serverApiOpt.get().issue().searchByKey(params.getIssueKey())
        .thenApply(IssueServiceImpl::toResponse));
  }

  private static CheckStatusChangePermittedResponse toResponse(Issues.Issue issue) {
    return toResponse(hasChangePermission(issue));
  }

  private static CheckStatusChangePermittedResponse toResponse(boolean permitted) {
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
    var issueKey = params.getIssueKey();
    return asUUID(issueKey)
      .flatMap(issueId -> setCommentOnLocalOnlyIssue(configurationScopeId, issueId, params.getText()))
      .orElseGet(() -> addCommentOnServerIssue(configurationScopeId, issueKey, params.getText()));
  }

  private Optional<CompletableFuture<Void>> setCommentOnLocalOnlyIssue(String configurationScopeId, UUID issueId, String comment) {
    var localOnlyIssueStore = localOnlyIssueStorageService.get();
    return localOnlyIssueStore.find(issueId)
      .flatMap(commentedIssue -> {
        var resolution = commentedIssue.getResolution();
        if (resolution != null) {
          // should always be true, we store only resolved local-only issues
          resolution.setComment(comment);
          var issuesToSync = localOnlyIssueStore.load(configurationScopeId, commentedIssue.getServerRelativePath());
          issuesToSync.replaceAll(issue -> issue.getId().equals(issueId) ? commentedIssue : issue);
          var optionalBinding = configurationRepository.getEffectiveBinding(configurationScopeId);
          return optionalBinding
            .flatMap(effectiveBinding -> serverApiProvider.getServerApi(effectiveBinding.getConnectionId()))
            .map(connection -> connection.issue().anticipateTransitions(optionalBinding.get().getSonarProjectKey(), issuesToSync))
            .map(future -> future.thenAccept(nothing -> localOnlyIssueStore.storeLocalOnlyIssue(configurationScopeId, commentedIssue)));
        }
        return Optional.empty();
      });
  }

  private CompletableFuture<Void> addCommentOnServerIssue(String configurationScopeId, String issueKey, String comment) {
    var optionalBinding = configurationRepository.getEffectiveBinding(configurationScopeId);
    return optionalBinding
      .flatMap(effectiveBinding -> serverApiProvider.getServerApi(effectiveBinding.getConnectionId()))
      .map(connection -> connection.issue().addComment(issueKey, comment)
        .exceptionally(throwable -> {
          throw new AddIssueCommentException(throwable);
        }))
      .orElseGet(() -> CompletableFuture.completedFuture(null));
  }

  private static Optional<UUID> asUUID(String key) {
    try {
      return Optional.of(UUID.fromString(key));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
