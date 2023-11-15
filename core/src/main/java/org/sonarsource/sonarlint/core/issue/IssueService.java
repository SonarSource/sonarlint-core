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

import java.time.Duration;
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
import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.Transition;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.event.SonarServerEventReceivedEvent;
import org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueStorageService;
import org.sonarsource.sonarlint.core.local.only.XodusLocalOnlyIssueStore;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.BackendErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenAllIssuesForFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectServerIssueStore;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.tracking.LocalOnlyIssueRepository;
import org.springframework.context.event.EventListener;

import static org.sonarsource.sonarlint.core.utils.FutureUtils.waitForHttpRequest;
import static org.sonarsource.sonarlint.core.utils.FutureUtils.waitForTask;
import static org.sonarsource.sonarlint.core.utils.FutureUtils.waitForTaskWithResult;

@Named
@Singleton
public class IssueService {

  private static final String STATUS_CHANGE_PERMISSION_MISSING_REASON = "Marking an issue as resolved requires the 'Administer Issues' permission";
  private static final String UNSUPPORTED_SQ_VERSION_REASON = "Marking a local-only issue as resolved requires SonarQube 10.2+";
  private static final Version SQ_ANTICIPATED_TRANSITIONS_MIN_VERSION = Version.create("10.2");
  private static final Map<ResolutionStatus, Transition> transitionByResolutionStatus = Map.of(
    ResolutionStatus.WONT_FIX, Transition.WONT_FIX,
    ResolutionStatus.FALSE_POSITIVE, Transition.FALSE_POSITIVE);
  private static final Set<String> requiredTransitions = transitionByResolutionStatus.values().stream().map(Transition::getStatus).collect(Collectors.toSet());

  private final ConfigurationRepository configurationRepository;
  private final ServerApiProvider serverApiProvider;
  private final StorageService storageService;
  private final LocalOnlyIssueStorageService localOnlyIssueStorageService;
  private final LocalOnlyIssueRepository localOnlyIssueRepository;
  private final TelemetryService telemetryService;

  public IssueService(ConfigurationRepository configurationRepository, ServerApiProvider serverApiProvider,
    StorageService storageService, LocalOnlyIssueStorageService localOnlyIssueStorageService,
    TelemetryService telemetryService, LocalOnlyIssueRepository localOnlyIssueRepository) {
    this.configurationRepository = configurationRepository;
    this.serverApiProvider = serverApiProvider;
    this.storageService = storageService;
    this.localOnlyIssueStorageService = localOnlyIssueStorageService;
    this.localOnlyIssueRepository = localOnlyIssueRepository;
    this.telemetryService = telemetryService;
  }

  public void changeStatus(String configurationScopeId, String issueKey, ResolutionStatus newStatus, boolean isTaintIssue, CancelChecker cancelChecker) {
    var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
    var serverApi = serverApiProvider.getServerApiOrThrow(binding.getConnectionId());
    var reviewStatus = transitionByResolutionStatus.get(newStatus);
    var projectServerIssueStore = storageService.binding(binding).findings();
    boolean isServerIssue = projectServerIssueStore.containsIssue(issueKey, isTaintIssue);
    if (isServerIssue) {
      waitForHttpRequest(cancelChecker, serverApi.issue().changeStatusAsync(issueKey, reviewStatus), "change status");
      projectServerIssueStore.updateIssueResolutionStatus(issueKey, isTaintIssue, true)
        .ifPresent(issue -> telemetryService.issueStatusChanged(issue.getRuleKey()));
    } else {
      var localIssueOpt = asUUID(issueKey)
        .flatMap(localOnlyIssueRepository::findByKey);
      if (localIssueOpt.isEmpty()) {
        var error = new ResponseError(BackendErrorCode.ISSUE_NOT_FOUND, "Issue key " + issueKey + " was not found", issueKey);
        throw new ResponseErrorException(error);
      }
      var coreStatus = org.sonarsource.sonarlint.core.commons.IssueStatus.valueOf(newStatus.name());
      var issue = localIssueOpt.get();
      issue.resolve(coreStatus);
      var localOnlyIssueStore = localOnlyIssueStorageService.get();
      waitForHttpRequest(cancelChecker, serverApi.issue()
        .anticipatedTransitions(binding.getSonarProjectKey(), concat(localOnlyIssueStore.loadAll(configurationScopeId), issue)), "update anticipated transitions");
      localOnlyIssueStore.storeLocalOnlyIssue(configurationScopeId, issue);
      telemetryService.issueStatusChanged(issue.getRuleKey());
    }
  }

  private static List<LocalOnlyIssue> concat(List<LocalOnlyIssue> issues, LocalOnlyIssue issue) {
    return Stream.concat(issues.stream(), Stream.of(issue)).collect(Collectors.toList());
  }

  private static List<LocalOnlyIssue> subtract(List<LocalOnlyIssue> allIssues, List<LocalOnlyIssue> issueToSubtract) {
    return allIssues.stream()
      .filter(it -> issueToSubtract.stream().noneMatch(issue -> issue.getId().equals(it.getId())))
      .collect(Collectors.toList());
  }

  public boolean checkAnticipatedStatusChangeSupported(String configScopeId) {
    var binding = configurationRepository.getEffectiveBindingOrThrow(configScopeId);
    var connectionId = binding.getConnectionId();
    var serverApi = serverApiProvider.getServerApiOrThrow(binding.getConnectionId());
    return checkAnticipatedStatusChangeSupported(serverApi, connectionId);
  }

  /**
   * Check if the anticipated transitions are supported on the server side (requires SonarQube 10.2+)
   *
   * @param api          used for checking if server is a SonarQube instance
   * @param connectionId required to get the version information from the server
   * @return whether server is SonarQube instance and matches version requirement
   */
  private boolean checkAnticipatedStatusChangeSupported(ServerApi api, String connectionId) {
    return !api.isSonarCloud() && storageService.connection(connectionId).serverInfo().read()
      .map(version -> version.getVersion().satisfiesMinRequirement(SQ_ANTICIPATED_TRANSITIONS_MIN_VERSION))
      .orElse(false);
  }

  public CheckStatusChangePermittedResponse checkStatusChangePermitted(String connectionId, String issueKey, CancelChecker cancelChecker) {
    var serverApi = serverApiProvider.getServerApiOrThrow(connectionId);
    return asUUID(issueKey)
      .flatMap(localOnlyIssueRepository::findByKey)
      .map(r -> {
        var anticipateTransitionsSupported = checkAnticipatedStatusChangeSupported(serverApi, connectionId);
        return toResponse(anticipateTransitionsSupported, UNSUPPORTED_SQ_VERSION_REASON);
      })
      .orElseGet(() -> {
        Issues.Issue issue = waitForTaskWithResult(cancelChecker, serverApi.issue().searchByKey(issueKey), "check status change permitted", Duration.ofSeconds(10));
        return toResponse(hasAdministerIssuePermission(issue), STATUS_CHANGE_PERMISSION_MISSING_REASON);
      });
  }

  private static CheckStatusChangePermittedResponse toResponse(boolean permitted, String reason) {
    return new CheckStatusChangePermittedResponse(permitted,
      permitted ? null : reason,
      // even if not permitted, return the possible statuses, if clients still want to show users what's supported
      Arrays.asList(ResolutionStatus.values()));
  }

  private static boolean hasAdministerIssuePermission(@Nullable Issues.Issue issue) {
    // the 2 required transitions are not available when the 'Administer Issues' permission is missing
    // normally the 'Browse' permission is also required, but we assume it's present as the client knows the issue key
    if (issue == null) {
      return false;
    }
    var possibleTransitions = new HashSet<>(issue.getTransitions().getTransitionsList());
    return possibleTransitions.containsAll(requiredTransitions);
  }

  public void addComment(String configurationScopeId, String issueKey, String text, CancelChecker cancelChecker) {
    var optionalId = asUUID(issueKey);
    if (optionalId.isPresent()) {
      setCommentOnLocalOnlyIssue(configurationScopeId, optionalId.get(), text, cancelChecker);
    } else {
      addCommentOnServerIssue(configurationScopeId, issueKey, text, cancelChecker);
    }
  }

  public boolean reopenIssue(String configurationScopeId, String issueId, boolean isTaintIssue, CancelChecker cancelChecker) {
    var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
    var serverApiConnection = serverApiProvider.getServerApiOrThrow(binding.getConnectionId());
    var projectServerIssueStore = storageService.binding(binding).findings();
    boolean isServerIssue = projectServerIssueStore.containsIssue(issueId, isTaintIssue);
    if (isServerIssue) {
      return reopenServerIssue(serverApiConnection, issueId, projectServerIssueStore, isTaintIssue, cancelChecker);
    } else {
      return reopenLocalIssue(issueId, configurationScopeId, cancelChecker);
    }
  }

  public boolean reopenAllIssuesForFile(ReopenAllIssuesForFileParams params, CancelChecker cancelChecker) {
    var configurationScopeId = params.getConfigurationScopeId();
    var filePath = params.getRelativePath();
    var localOnlyIssueStore = localOnlyIssueStorageService.get();
    waitForTask(cancelChecker, removeAllIssuesForFile(localOnlyIssueStore, configurationScopeId, filePath), "Reopen all issues for file", Duration.ofMinutes(1));
    return localOnlyIssueStorageService.get().removeAllIssuesForFile(configurationScopeId, filePath);
  }

  private CompletableFuture<Void> removeAllIssuesForFile(XodusLocalOnlyIssueStore localOnlyIssueStore,
    String configurationScopeId, String filePath) {
    return CompletableFutures.computeAsync(cancelChecker -> {
      var allIssues = localOnlyIssueStore.loadAll(configurationScopeId);
      var issuesForFile = localOnlyIssueStore.loadForFile(configurationScopeId, filePath);
      var issuesToSync = subtract(allIssues, issuesForFile);
      var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
      var serverConnection = serverApiProvider.getServerApiOrThrow(binding.getConnectionId());
      waitForHttpRequest(cancelChecker, serverConnection.issue().anticipatedTransitions(binding.getSonarProjectKey(), issuesToSync), "Reopen all issues for file");
      return null;
    });
  }

  private CompletableFuture<Void> removeIssueOnServer(XodusLocalOnlyIssueStore localOnlyIssueStore,
    String configurationScopeId, UUID issueId) {
    return CompletableFutures.computeAsync(cancelChecker -> {
      var allIssues = localOnlyIssueStore.loadAll(configurationScopeId);
      var issuesToSync = allIssues.stream().filter(it -> !it.getId().equals(issueId)).collect(Collectors.toList());
      var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
      var serverConnection = serverApiProvider.getServerApiOrThrow(binding.getConnectionId());
      waitForHttpRequest(cancelChecker, serverConnection.issue().anticipatedTransitions(binding.getSonarProjectKey(), issuesToSync), "Remove issue on server");
      return null;
    });
  }

  private void setCommentOnLocalOnlyIssue(String configurationScopeId, UUID issueId, String comment, CancelChecker cancelChecker) {
    var localOnlyIssueStore = localOnlyIssueStorageService.get();
    var optionalLocalOnlyIssue = localOnlyIssueStore.find(issueId);
    if (optionalLocalOnlyIssue.isPresent()) {
      var commentedIssue = optionalLocalOnlyIssue.get();
      var resolution = commentedIssue.getResolution();
      if (resolution != null) {
        resolution.setComment(comment);
        var issuesToSync = localOnlyIssueStore.loadAll(configurationScopeId);
        issuesToSync.replaceAll(issue -> issue.getId().equals(issueId) ? commentedIssue : issue);
        var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
        var serverApi = serverApiProvider.getServerApiOrThrow(binding.getConnectionId());
        waitForHttpRequest(cancelChecker, serverApi.issue().anticipatedTransitions(binding.getSonarProjectKey(), issuesToSync), "Add comment to local issue");
        localOnlyIssueStore.storeLocalOnlyIssue(configurationScopeId, commentedIssue);
      }
    }
  }

  private void addCommentOnServerIssue(String configurationScopeId, String issueKey, String comment, CancelChecker cancelChecker) {
    var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
    var serverApi = serverApiProvider.getServerApiOrThrow(binding.getConnectionId());
    waitForHttpRequest(cancelChecker, serverApi.issue().addComment(issueKey, comment), "Add comment to server issue");
  }

  private boolean reopenServerIssue(ServerApi connection, String issueId, ProjectServerIssueStore projectServerIssueStore, boolean isTaintIssue,
    CancelChecker cancelChecker) {
    waitForHttpRequest(cancelChecker, connection.issue().changeStatusAsync(issueId, Transition.REOPEN), "Reopen server issue");
    var serverIssue = projectServerIssueStore.updateIssueResolutionStatus(issueId, isTaintIssue, false);
    serverIssue.ifPresent(issue -> telemetryService.issueStatusChanged(issue.getRuleKey()));
    return true;
  }

  private boolean reopenLocalIssue(String issueId, String configurationScopeId, CancelChecker cancelChecker) {
    var issueUuidOptional = asUUID(issueId);
    if (issueUuidOptional.isEmpty()) {
      return false;
    }
    var issueUuid = issueUuidOptional.get();
    var localOnlyIssueStore = localOnlyIssueStorageService.get();
    waitForTask(cancelChecker, removeIssueOnServer(localOnlyIssueStore, configurationScopeId, issueUuid), "Reopen local issue", Duration.ofMinutes(1));
    return localOnlyIssueStorageService.get().removeIssue(issueUuid);
  }

  @EventListener
  public void onServerEventReceived(SonarServerEventReceivedEvent eventReceived) {
    var connectionId = eventReceived.getConnectionId();
    var serverEvent = eventReceived.getEvent();
    if (serverEvent instanceof IssueChangedEvent) {
      updateStorage(connectionId, (IssueChangedEvent) serverEvent);
    }
  }

  private void updateStorage(String connectionId, IssueChangedEvent event) {
    var findingsStorage = storageService.connection(connectionId).project(event.getProjectKey()).findings();
    event.getImpactedIssueKeys().forEach(issueKey -> findingsStorage.updateIssue(issueKey, issue -> {
      var userSeverity = event.getUserSeverity();
      if (userSeverity != null) {
        issue.setUserSeverity(userSeverity);
      }
      var userType = event.getUserType();
      if (userType != null) {
        issue.setType(userType);
      }
      var resolved = event.getResolved();
      if (resolved != null) {
        issue.setResolved(resolved);
      }
    }));
  }

  private static Optional<UUID> asUUID(String key) {
    try {
      return Optional.of(UUID.fromString(key));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
