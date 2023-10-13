/*
 * SonarLint Core - Implementation
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
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueStorageService;
import org.sonarsource.sonarlint.core.local.only.XodusLocalOnlyIssueStore;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.BackendErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.AddIssueCommentParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ChangeIssueStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckAnticipatedStatusChangeSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckAnticipatedStatusChangeSupportedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.IssueService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenAllIssuesForFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenIssueResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverconnection.ServerInfoSynchronizer;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectServerIssueStore;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;
import org.sonarsource.sonarlint.core.tracking.LocalOnlyIssueRepository;

import static org.sonarsource.sonarlint.core.utils.FutureUtils.waitForHttpRequest;
import static org.sonarsource.sonarlint.core.utils.FutureUtils.waitForTask;
import static org.sonarsource.sonarlint.core.utils.FutureUtils.waitForTaskWithResult;

@Named
@Singleton
public class IssueServiceImpl implements IssueService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final String STATUS_CHANGE_PERMISSION_MISSING_REASON = "Marking an issue as resolved requires the 'Administer Issues' permission";
  private static final String UNSUPPORTED_SQ_VERSION_REASON = "Marking a local-only issue as resolved requires SonarQube 10.2+";
  private static final Version SQ_ANTICIPATED_TRANSITIONS_MIN_VERSION = Version.create("10.2");

  /** With SQ 10.4 the transitions changed from "Won't fix" to "Accept" */
  private static final Version SQ_ACCEPTED_TRANSITION_MIN_VERSION = Version.create("10.4");
  private static final List<ResolutionStatus> NEW_RESOLUTION_STATUSES = List.of(ResolutionStatus.ACCEPT, ResolutionStatus.FALSE_POSITIVE);
  private static final List<ResolutionStatus> OLD_RESOLUTION_STATUSES = List.of(ResolutionStatus.WONT_FIX, ResolutionStatus.FALSE_POSITIVE);
  private static final Map<ResolutionStatus, Transition> transitionByResolutionStatus = Map.of(
    ResolutionStatus.ACCEPT, Transition.ACCEPT,
    ResolutionStatus.WONT_FIX, Transition.WONT_FIX,
    ResolutionStatus.FALSE_POSITIVE, Transition.FALSE_POSITIVE
  );

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
    return CompletableFutures.computeAsync(cancelChecker -> {
      var configurationScopeId = params.getConfigurationScopeId();
      var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
      var serverApi = serverApiProvider.getServerApiOrThrow(binding.getConnectionId());
      var reviewStatus = transitionByResolutionStatus.get(params.getNewStatus());
      var projectServerIssueStore = storageService.binding(binding).findings();
      var issueKey = params.getIssueKey();
      boolean isServerIssue = projectServerIssueStore.containsIssue(issueKey, params.isTaintIssue());
      if (isServerIssue) {
        waitForHttpRequest(cancelChecker, serverApi.issue().changeStatusAsync(issueKey, reviewStatus), "change status");
        projectServerIssueStore.updateIssueResolutionStatus(issueKey, params.isTaintIssue(), true)
          .ifPresent(issue -> telemetryService.issueStatusChanged(issue.getRuleKey()));
      } else {
        var localIssueOpt = asUUID(issueKey)
          .flatMap(localOnlyIssueRepository::findByKey);
        if (localIssueOpt.isEmpty()) {
          var error = new ResponseError(BackendErrorCode.ISSUE_NOT_FOUND, "Issue key " + issueKey + " was not found", issueKey);
          throw new ResponseErrorException(error);
        }
        var coreStatus = org.sonarsource.sonarlint.core.commons.IssueStatus.valueOf(params.getNewStatus().name());
        var issue = localIssueOpt.get();
        issue.resolve(coreStatus);
        var localOnlyIssueStore = localOnlyIssueStorageService.get();
        waitForHttpRequest(cancelChecker, serverApi.issue()
          .anticipatedTransitions(binding.getSonarProjectKey(), concat(localOnlyIssueStore.loadAll(configurationScopeId), issue)), "update anticipated transitions");
        localOnlyIssueStore.storeLocalOnlyIssue(params.getConfigurationScopeId(), issue);
        telemetryService.issueStatusChanged(issue.getRuleKey());
      }
      return null;
    });
  }

  private static List<LocalOnlyIssue> concat(List<LocalOnlyIssue> issues, LocalOnlyIssue issue) {
    return Stream.concat(issues.stream(), Stream.of(issue)).collect(Collectors.toList());
  }

  private static List<LocalOnlyIssue> subtract(List<LocalOnlyIssue> allIssues, List<LocalOnlyIssue> issueToSubtract) {
    return allIssues.stream()
      .filter(it -> issueToSubtract.stream().noneMatch(issue -> issue.getId().equals(it.getId())))
      .collect(Collectors.toList());
  }

  @Override
  public CompletableFuture<CheckAnticipatedStatusChangeSupportedResponse> checkAnticipatedStatusChangeSupported(CheckAnticipatedStatusChangeSupportedParams params) {
    return CompletableFutures.computeAsync(cancelChecker -> {
      var configScopeId = params.getConfigScopeId();
      var binding = configurationRepository.getEffectiveBindingOrThrow(configScopeId);
      var connectionId = binding.getConnectionId();
      var serverApi = serverApiProvider.getServerApiOrThrow(binding.getConnectionId());
      return new CheckAnticipatedStatusChangeSupportedResponse(checkAnticipatedStatusChangeSupported(serverApi, connectionId));
    });
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

  @Override
  public CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(CheckStatusChangePermittedParams params) {
    return CompletableFutures.computeAsync(cancelChecker -> {
      var connectionId = params.getConnectionId();
      var serverApi = serverApiProvider.getServerApiOrThrow(connectionId);
      var issueKey = params.getIssueKey();
      return asUUID(issueKey)
        .flatMap(localOnlyIssueRepository::findByKey)
        .map(r -> {
          // For anticipated issues we currently don't get the information from SonarQube (as there is no web API
          // endpoint) regarding the available transitions. SonarCloud doesn't provide it currently anyway. That's why we
          // have to rely on the version check for SonarQube (>= 10.2 / >=10.4)
          List<ResolutionStatus> statuses = List.of();
          if (checkAnticipatedStatusChangeSupported(serverApi, connectionId)) {
            var is104orNewer = !serverApi.isSonarCloud() && is104orNewer(connectionId, serverApi);
            statuses = is104orNewer ? NEW_RESOLUTION_STATUSES : OLD_RESOLUTION_STATUSES;
          }

          return toResponse(statuses, UNSUPPORTED_SQ_VERSION_REASON);
        })
        .orElseGet(() -> {
          Issues.Issue issue = waitForTaskWithResult(cancelChecker, serverApi.issue().searchByKey(params.getIssueKey()), "check status change permitted", Duration.ofSeconds(10));
          return toResponse(getAdministerIssueTransitions(issue), STATUS_CHANGE_PERMISSION_MISSING_REASON);
        });
    });
  }

  /** For checking whether SonarQube is already on 10.4 or not. NEVER apply to SonarCloud as their version differs! */
  private boolean is104orNewer(String connectionId, ServerApi serverApi) {
    var serverVersionSynchronizer = new ServerInfoSynchronizer(storageService.connection(connectionId));
    var serverVersion = serverVersionSynchronizer.readOrSynchronizeServerInfo(serverApi);
    return serverVersion.getVersion().compareToIgnoreQualifier(SQ_ACCEPTED_TRANSITION_MIN_VERSION) >= 0;
  }

  private static CheckStatusChangePermittedResponse toResponse(List<ResolutionStatus> statuses, String reason) {
    var permitted = !statuses.isEmpty();

    // No status available means it is not permitted or not supported (e.g. SonarCloud for anticipated issues)
    return new CheckStatusChangePermittedResponse(permitted, permitted ? null : reason, statuses);
  }

  private static List<ResolutionStatus> getAdministerIssueTransitions(Issues.Issue issue) {
    // the 2 required transitions are not available when the 'Administer Issues' permission is missing
    // normally the 'Browse' permission is also required, but we assume it's present as the client knows the issue key
    var possibleTransitions = new HashSet<>(issue.getTransitions().getTransitionsList());

    if (possibleTransitions.containsAll(toTransitionStatus(NEW_RESOLUTION_STATUSES))) {
      return NEW_RESOLUTION_STATUSES;
    }

    // No transitions meaning you're not allowed. That's it.
    return possibleTransitions.containsAll(toTransitionStatus(OLD_RESOLUTION_STATUSES))
      ? OLD_RESOLUTION_STATUSES
      : List.of();
  }

  private static Set<String> toTransitionStatus(List<ResolutionStatus> resolutions) {
    return resolutions.stream()
      .map(resolution -> transitionByResolutionStatus.get(resolution).getStatus())
      .collect(Collectors.toSet());
  }

  @Override
  public CompletableFuture<Void> addComment(AddIssueCommentParams params) {
    return CompletableFutures.computeAsync(cancelChecker -> {
      var configurationScopeId = params.getConfigurationScopeId();
      var issueKey = params.getIssueKey();
      var optionalId = asUUID(issueKey);
      if (optionalId.isPresent()) {
        setCommentOnLocalOnlyIssue(configurationScopeId, optionalId.get(), params.getText(), cancelChecker);
      } else {
        addCommentOnServerIssue(configurationScopeId, issueKey, params.getText(), cancelChecker);
      }
      return null;
    });
  }

  @Override
  public CompletableFuture<ReopenIssueResponse> reopenIssue(ReopenIssueParams params) {
    return CompletableFutures.computeAsync(cancelChecker -> {
      var configurationScopeId = params.getConfigurationScopeId();
      var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
      var serverApiConnection = serverApiProvider.getServerApiOrThrow(binding.getConnectionId());
      var projectServerIssueStore = storageService.binding(binding).findings();
      var issueId = params.getIssueId();
      boolean isServerIssue = projectServerIssueStore.containsIssue(issueId, params.isTaintIssue());
      if (isServerIssue) {
        return waitForTaskWithResult(cancelChecker, reopenServerIssue(serverApiConnection, issueId, projectServerIssueStore, params.isTaintIssue()),
          "Reopen server issue", Duration.ofMinutes(1));
      } else {
        return waitForTaskWithResult(cancelChecker, reopenLocalIssue(issueId, configurationScopeId), "Reopen local issue", Duration.ofMinutes(1));
      }
    });
  }

  @Override
  public CompletableFuture<ReopenIssueResponse> reopenAllIssuesForFile(ReopenAllIssuesForFileParams params) {
    return CompletableFutures.computeAsync(cancelChecker -> {
      var configurationScopeId = params.getConfigurationScopeId();
      var filePath = params.getRelativePath();
      var localOnlyIssueStore = localOnlyIssueStorageService.get();
      waitForTask(cancelChecker, removeAllIssuesForFile(localOnlyIssueStore, configurationScopeId, filePath), "Reopen all issues for file", Duration.ofMinutes(1));
      var result = localOnlyIssueStorageService.get().removeAllIssuesForFile(configurationScopeId, filePath);
      return new ReopenIssueResponse(result);
    });
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

  private CompletableFuture<ReopenIssueResponse> reopenServerIssue(ServerApi connection, String issueId, ProjectServerIssueStore projectServerIssueStore, boolean isTaintIssue) {
    return CompletableFutures.computeAsync(cancelChecker -> {
      waitForHttpRequest(cancelChecker, connection.issue().changeStatusAsync(issueId, Transition.REOPEN), "Reopen server issue");
      var serverIssue = projectServerIssueStore.updateIssueResolutionStatus(issueId, isTaintIssue, false);
      serverIssue.ifPresent(issue -> telemetryService.issueStatusChanged(issue.getRuleKey()));
      return new ReopenIssueResponse(true);
    });
  }

  private CompletableFuture<ReopenIssueResponse> reopenLocalIssue(String issueId, String configurationScopeId) {
    return CompletableFutures.computeAsync(cancelChecker -> {
      var issueUuidOptional = asUUID(issueId);
      if (issueUuidOptional.isEmpty()) {
        return new ReopenIssueResponse(false);
      }
      var issueUuid = issueUuidOptional.get();
      var localOnlyIssueStore = localOnlyIssueStorageService.get();
      waitForTask(cancelChecker, removeIssueOnServer(localOnlyIssueStore, configurationScopeId, issueUuid), "Reopen local issue", Duration.ofMinutes(1));
      var result = localOnlyIssueStorageService.get().removeIssue(issueUuid);
      return new ReopenIssueResponse(result);
    });
  }

  private static Optional<UUID> asUUID(String key) {
    try {
      return Optional.of(UUID.fromString(key));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
