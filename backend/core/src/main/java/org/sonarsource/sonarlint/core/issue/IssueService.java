/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.ConnectionManager;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.NewCodeDefinition;
import org.sonarsource.sonarlint.core.commons.Transition;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.LocalOnlyIssueStatusChangedEvent;
import org.sonarsource.sonarlint.core.event.ServerIssueStatusChangedEvent;
import org.sonarsource.sonarlint.core.event.SonarServerEventReceivedEvent;
import org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueStorageService;
import org.sonarsource.sonarlint.core.local.only.XodusLocalOnlyIssueStore;
import org.sonarsource.sonarlint.core.mode.SeverityModeService;
import org.sonarsource.sonarlint.core.newcode.NewCodeService;
import org.sonarsource.sonarlint.core.reporting.FindingReportingService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.EffectiveIssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenAllIssuesForFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rules.RuleDetails;
import org.sonarsource.sonarlint.core.rules.RuleDetailsAdapter;
import org.sonarsource.sonarlint.core.rules.RuleNotFoundException;
import org.sonarsource.sonarlint.core.rules.RulesService;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverconnection.ServerInfoSynchronizer;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectServerIssueStore;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.tracking.LocalOnlyIssueRepository;
import org.sonarsource.sonarlint.core.tracking.TaintVulnerabilityTrackingService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

@Named
@Singleton
public class IssueService {

  private static final String STATUS_CHANGE_PERMISSION_MISSING_REASON = "Marking an issue as resolved requires the 'Administer Issues' permission";
  private static final String UNSUPPORTED_SQ_VERSION_REASON = "Marking a local-only issue as resolved requires SonarQube Server 10.2+";
  private static final Version SQ_ANTICIPATED_TRANSITIONS_MIN_VERSION = Version.create("10.2");

  /**
   * With SQ 10.4 the transitions changed from "Won't fix" to "Accept"
   */
  private static final Version SQ_ACCEPTED_TRANSITION_MIN_VERSION = Version.create("10.4");
  private static final List<ResolutionStatus> NEW_RESOLUTION_STATUSES = List.of(ResolutionStatus.ACCEPT, ResolutionStatus.FALSE_POSITIVE);
  private static final List<ResolutionStatus> OLD_RESOLUTION_STATUSES = List.of(ResolutionStatus.WONT_FIX, ResolutionStatus.FALSE_POSITIVE);
  private static final Map<ResolutionStatus, Transition> transitionByResolutionStatus = Map.of(
    ResolutionStatus.ACCEPT, Transition.ACCEPT,
    ResolutionStatus.WONT_FIX, Transition.WONT_FIX,
    ResolutionStatus.FALSE_POSITIVE, Transition.FALSE_POSITIVE);

  private final ConfigurationRepository configurationRepository;
  private final ConnectionManager connectionManager;
  private final StorageService storageService;
  private final LocalOnlyIssueStorageService localOnlyIssueStorageService;
  private final LocalOnlyIssueRepository localOnlyIssueRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final FindingReportingService findingReportingService;
  private final SeverityModeService severityModeService;
  private final NewCodeService newCodeService;
  private final RulesService rulesService;
  private final TaintVulnerabilityTrackingService taintVulnerabilityTrackingService;

  public IssueService(ConfigurationRepository configurationRepository, ConnectionManager connectionManager, StorageService storageService,
    LocalOnlyIssueStorageService localOnlyIssueStorageService, LocalOnlyIssueRepository localOnlyIssueRepository,
    ApplicationEventPublisher eventPublisher, FindingReportingService findingReportingService, SeverityModeService severityModeService,
    NewCodeService newCodeService, RulesService rulesService, TaintVulnerabilityTrackingService taintVulnerabilityTrackingService) {
    this.configurationRepository = configurationRepository;
    this.connectionManager = connectionManager;
    this.storageService = storageService;
    this.localOnlyIssueStorageService = localOnlyIssueStorageService;
    this.localOnlyIssueRepository = localOnlyIssueRepository;
    this.eventPublisher = eventPublisher;
    this.findingReportingService = findingReportingService;
    this.severityModeService = severityModeService;
    this.newCodeService = newCodeService;
    this.rulesService = rulesService;
    this.taintVulnerabilityTrackingService = taintVulnerabilityTrackingService;
  }

  public void changeStatus(String configurationScopeId, String issueKey, ResolutionStatus newStatus, boolean isTaintIssue, SonarLintCancelMonitor cancelMonitor) {
    var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
    var serverConnection = connectionManager.getConnectionOrThrow(binding.getConnectionId());
    var reviewStatus = transitionByResolutionStatus.get(newStatus);
    var projectServerIssueStore = storageService.binding(binding).findings();
    boolean isServerIssue = projectServerIssueStore.containsIssue(issueKey);
    if (isServerIssue) {
      serverConnection.withClientApi(serverApi -> serverApi.issue().changeStatus(issueKey, reviewStatus, cancelMonitor));
      projectServerIssueStore.updateIssueResolutionStatus(issueKey, isTaintIssue, true)
        .ifPresent(issue -> eventPublisher.publishEvent(new ServerIssueStatusChangedEvent(binding.getConnectionId(), binding.getSonarProjectKey(), issue)));
    } else {
      var localIssueOpt = asUUID(issueKey)
        .flatMap(localOnlyIssueRepository::findByKey);
      if (localIssueOpt.isEmpty()) {
        throw issueNotFoundException(issueKey);
      }
      var coreStatus = org.sonarsource.sonarlint.core.commons.IssueStatus.valueOf(newStatus.name());
      var issue = localIssueOpt.get();
      issue.resolve(coreStatus);
      var localOnlyIssueStore = localOnlyIssueStorageService.get();
      serverConnection.withClientApi(serverApi -> serverApi.issue()
        .anticipatedTransitions(binding.getSonarProjectKey(), concat(localOnlyIssueStore.loadAll(configurationScopeId), issue), cancelMonitor));
      localOnlyIssueStore.storeLocalOnlyIssue(configurationScopeId, issue);
      eventPublisher.publishEvent(new LocalOnlyIssueStatusChangedEvent(issue));
    }
  }

  private static List<LocalOnlyIssue> concat(List<LocalOnlyIssue> issues, LocalOnlyIssue issue) {
    return Stream.concat(issues.stream(), Stream.of(issue)).toList();
  }

  private static List<LocalOnlyIssue> subtract(List<LocalOnlyIssue> allIssues, List<LocalOnlyIssue> issueToSubtract) {
    return allIssues.stream()
      .filter(it -> issueToSubtract.stream().noneMatch(issue -> issue.getId().equals(it.getId())))
      .toList();
  }

  public boolean checkAnticipatedStatusChangeSupported(String configScopeId) {
    var binding = configurationRepository.getEffectiveBindingOrThrow(configScopeId);
    var connectionId = binding.getConnectionId();
    return connectionManager.getConnectionOrThrow(binding.getConnectionId())
      .withClientApiAndReturn(serverApi -> checkAnticipatedStatusChangeSupported(serverApi, connectionId));
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

  public CheckStatusChangePermittedResponse checkStatusChangePermitted(String connectionId, String issueKey, SonarLintCancelMonitor cancelMonitor) {
    return connectionManager.getConnectionOrThrow(connectionId).withClientApiAndReturn(serverApi -> asUUID(issueKey)
      .flatMap(localOnlyIssueRepository::findByKey)
      .map(r -> {
        // For anticipated issues we currently don't get the information from SonarQube (as there is no web API
        // endpoint) regarding the available transitions. SonarCloud doesn't provide it currently anyway. That's why we
        // have to rely on the version check for SonarQube (>= 10.2 / >=10.4)
        List<ResolutionStatus> statuses = List.of();
        if (checkAnticipatedStatusChangeSupported(serverApi, connectionId)) {
          var is104orNewer = !serverApi.isSonarCloud() && is104orNewer(connectionId, serverApi, cancelMonitor);
          statuses = is104orNewer ? NEW_RESOLUTION_STATUSES : OLD_RESOLUTION_STATUSES;
        }

        return toResponse(statuses, UNSUPPORTED_SQ_VERSION_REASON);
      })
      .orElseGet(() -> {
        var issue = serverApi.issue().searchByKey(issueKey, cancelMonitor);
        return toResponse(getAdministerIssueTransitions(issue), STATUS_CHANGE_PERMISSION_MISSING_REASON);
      }));
  }

  /**
   * For checking whether SonarQube is already on 10.4 or not. NEVER apply to SonarCloud as their version differs!
   */
  private boolean is104orNewer(String connectionId, ServerApi serverApi, SonarLintCancelMonitor cancelMonitor) {
    var serverVersionSynchronizer = new ServerInfoSynchronizer(storageService.connection(connectionId));
    var serverVersion = serverVersionSynchronizer.readOrSynchronizeServerInfo(serverApi, cancelMonitor);
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

  public void addComment(String configurationScopeId, String issueKey, String text, SonarLintCancelMonitor cancelMonitor) {
    var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
    var projectServerIssueStore = storageService.binding(binding).findings();
    boolean isServerIssue = projectServerIssueStore.containsIssue(issueKey);
    if (isServerIssue) {
      addCommentOnServerIssue(configurationScopeId, issueKey, text, cancelMonitor);
    } else {
      var optionalId = asUUID(issueKey);
      if (optionalId.isPresent()) {
        setCommentOnLocalOnlyIssue(configurationScopeId, optionalId.get(), text, cancelMonitor);
      } else {
        throw issueNotFoundException(issueKey);
      }
    }
  }

  public boolean reopenIssue(String configurationScopeId, String issueId, boolean isTaintIssue, SonarLintCancelMonitor cancelMonitor) {
    var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
    var projectServerIssueStore = storageService.binding(binding).findings();
    boolean isServerIssue = projectServerIssueStore.containsIssue(issueId);
    if (isServerIssue) {
      return connectionManager.getConnectionOrThrow(binding.getConnectionId())
        .withClientApiAndReturn(serverApi -> reopenServerIssue(serverApi, binding, issueId, projectServerIssueStore, isTaintIssue, cancelMonitor));
    } else {
      return reopenLocalIssue(issueId, configurationScopeId, cancelMonitor);
    }
  }

  public boolean reopenAllIssuesForFile(ReopenAllIssuesForFileParams params, SonarLintCancelMonitor cancelMonitor) {
    var configurationScopeId = params.getConfigurationScopeId();
    var ideRelativePath = params.getIdeRelativePath();
    var localOnlyIssueStore = localOnlyIssueStorageService.get();
    removeAllIssuesForFile(localOnlyIssueStore, configurationScopeId, ideRelativePath, cancelMonitor);
    return localOnlyIssueStorageService.get().removeAllIssuesForFile(configurationScopeId, ideRelativePath);
  }

  private void removeAllIssuesForFile(XodusLocalOnlyIssueStore localOnlyIssueStore,
    String configurationScopeId, Path filePath, SonarLintCancelMonitor cancelMonitor) {
    var allIssues = localOnlyIssueStore.loadAll(configurationScopeId);
    var issuesForFile = localOnlyIssueStore.loadForFile(configurationScopeId, filePath);
    var issuesToSync = subtract(allIssues, issuesForFile);
    var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
    connectionManager.getConnectionOrThrow(binding.getConnectionId())
        .withClientApi(serverApi -> serverApi.issue().anticipatedTransitions(binding.getSonarProjectKey(), issuesToSync, cancelMonitor));
  }

  private void removeIssueOnServer(XodusLocalOnlyIssueStore localOnlyIssueStore,
    String configurationScopeId, UUID issueId, SonarLintCancelMonitor cancelMonitor) {
    var allIssues = localOnlyIssueStore.loadAll(configurationScopeId);
    var issuesToSync = allIssues.stream().filter(it -> !it.getId().equals(issueId)).toList();
    var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
    connectionManager.getConnectionOrThrow(binding.getConnectionId())
        .withClientApi(serverApi -> serverApi.issue().anticipatedTransitions(binding.getSonarProjectKey(), issuesToSync, cancelMonitor));
  }

  private void setCommentOnLocalOnlyIssue(String configurationScopeId, UUID issueId, String comment, SonarLintCancelMonitor cancelMonitor) {
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
        connectionManager.getConnectionOrThrow(binding.getConnectionId())
            .withClientApi(serverApi -> serverApi.issue().anticipatedTransitions(binding.getSonarProjectKey(), issuesToSync, cancelMonitor));
        localOnlyIssueStore.storeLocalOnlyIssue(configurationScopeId, commentedIssue);
      }
    } else {
      throw issueNotFoundException(issueId.toString());
    }
  }

  private static ResponseErrorException issueNotFoundException(String issueId) {
    var error = new ResponseError(SonarLintRpcErrorCode.ISSUE_NOT_FOUND, "Issue key " + issueId + " was not found", issueId);
    throw new ResponseErrorException(error);
  }

  private void addCommentOnServerIssue(String configurationScopeId, String issueKey, String comment, SonarLintCancelMonitor cancelMonitor) {
    var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
    connectionManager.getConnectionOrThrow(binding.getConnectionId())
      .withClientApi(serverApi -> serverApi.issue().addComment(issueKey, comment, cancelMonitor));
  }

  private boolean reopenServerIssue(ServerApi connection, Binding binding, String issueId, ProjectServerIssueStore projectServerIssueStore, boolean isTaintIssue,
    SonarLintCancelMonitor cancelMonitor) {
    connection.issue().changeStatus(issueId, Transition.REOPEN, cancelMonitor);
    var serverIssue = projectServerIssueStore.updateIssueResolutionStatus(issueId, isTaintIssue, false);
    serverIssue.ifPresent(issue -> eventPublisher.publishEvent(new ServerIssueStatusChangedEvent(binding.getConnectionId(), binding.getSonarProjectKey(), issue)));
    return true;
  }

  private boolean reopenLocalIssue(String issueId, String configurationScopeId, SonarLintCancelMonitor cancelMonitor) {
    var issueUuidOptional = asUUID(issueId);
    if (issueUuidOptional.isEmpty()) {
      return false;
    }
    var issueUuid = issueUuidOptional.get();
    var localOnlyIssueStore = localOnlyIssueStorageService.get();
    removeIssueOnServer(localOnlyIssueStore, configurationScopeId, issueUuid, cancelMonitor);
    return localOnlyIssueStorageService.get().removeIssue(issueUuid);
  }

  public EffectiveIssueDetailsDto getEffectiveIssueDetails(String configurationScopeId, UUID findingId, SonarLintCancelMonitor cancelMonitor)
    throws IssueNotFoundException, RuleNotFoundException {
    var effectiveBinding = configurationRepository.getEffectiveBinding(configurationScopeId);
    String connectionId = null;
    if (effectiveBinding.isPresent()) {
      connectionId = effectiveBinding.get().getConnectionId();
    }
    var isMQRMode = severityModeService.isMQRModeForConnection(connectionId);
    var newCodeDefinition = newCodeService.getFullNewCodeDefinition(configurationScopeId).orElseGet(NewCodeDefinition::withAlwaysNew);
    var maybeIssue = findingReportingService.findReportedIssue(findingId, newCodeDefinition, isMQRMode);
    var maybeHotspot = findingReportingService.findReportedHotspot(findingId, newCodeDefinition, isMQRMode);
    var maybeTaint = taintVulnerabilityTrackingService.getTaintVulnerability(configurationScopeId, findingId, cancelMonitor);

    if (maybeIssue != null) {
      return getFindingDetails(maybeIssue, configurationScopeId, cancelMonitor);
    } else if (maybeHotspot != null) {
      return getFindingDetails(maybeHotspot, configurationScopeId, cancelMonitor);
    } else if (maybeTaint.isPresent()) {
      return getTaintDetails(maybeTaint.get(), configurationScopeId, cancelMonitor);
    }
    throw new IssueNotFoundException("Failed to retrieve finding details. Finding with key '"
      + findingId + "' not found.", findingId);
  }

  private EffectiveIssueDetailsDto getFindingDetails(RaisedFindingDto finding, String configurationScopeId, SonarLintCancelMonitor cancelMonitor) throws RuleNotFoundException {
    var ruleKey = finding.getRuleKey();
    var ruleDetails = rulesService.getRuleDetails(configurationScopeId, ruleKey, cancelMonitor);
    var ruleDetailsEnrichedWithActualIssueSeverity = RuleDetails.merging(ruleDetails, finding);
    var effectiveRuleDetails = RuleDetailsAdapter.transform(ruleDetailsEnrichedWithActualIssueSeverity, finding.getRuleDescriptionContextKey());
    return new EffectiveIssueDetailsDto(ruleKey, effectiveRuleDetails.getName(), effectiveRuleDetails.getLanguage(),
      // users cannot customize vulnerability probability
      effectiveRuleDetails.getVulnerabilityProbability(),
      effectiveRuleDetails.getDescription(), effectiveRuleDetails.getParams(), finding.getSeverityMode(), finding.getRuleDescriptionContextKey());
  }

  private EffectiveIssueDetailsDto getTaintDetails(TaintVulnerabilityDto finding, String configurationScopeId, SonarLintCancelMonitor cancelMonitor) throws RuleNotFoundException {
    var ruleKey = finding.getRuleKey();
    var ruleDetails = rulesService.getRuleDetails(configurationScopeId, ruleKey, cancelMonitor);
    var ruleDetailsEnrichedWithActualIssueSeverity = RuleDetails.merging(ruleDetails, finding);
    var effectiveRuleDetails = RuleDetailsAdapter.transform(ruleDetailsEnrichedWithActualIssueSeverity, finding.getRuleDescriptionContextKey());
    return new EffectiveIssueDetailsDto(ruleKey, effectiveRuleDetails.getName(), effectiveRuleDetails.getLanguage(),
      effectiveRuleDetails.getVulnerabilityProbability(),
      effectiveRuleDetails.getDescription(), effectiveRuleDetails.getParams(), finding.getSeverityMode(), finding.getRuleDescriptionContextKey());
  }

  @EventListener
  public void onServerEventReceived(SonarServerEventReceivedEvent eventReceived) {
    var connectionId = eventReceived.getConnectionId();
    var serverEvent = eventReceived.getEvent();
    if (serverEvent instanceof IssueChangedEvent issueChangedEvent) {
      handleEvent(connectionId, issueChangedEvent);
    }
  }

  private void handleEvent(String connectionId, IssueChangedEvent event) {
    updateProjectIssueStorage(connectionId, event);
    republishPreviouslyRaisedIssues(connectionId, event);
  }

  private void republishPreviouslyRaisedIssues(String connectionId, IssueChangedEvent event) {
    var isMQRMode = severityModeService.isMQRModeForConnection(connectionId);
    var boundScopes = configurationRepository.getBoundScopesToConnectionAndSonarProject(connectionId, event.getProjectKey());
    boundScopes.forEach(scope -> {
      var scopeId = scope.getConfigScopeId();
      findingReportingService.updateAndReportIssues(scopeId, previouslyRaisedIssue -> raisedIssueUpdater(previouslyRaisedIssue, isMQRMode, event));
    });
  }

  public static RaisedIssueDto raisedIssueUpdater(RaisedIssueDto previouslyRaisedIssue, boolean isMQRMode, IssueChangedEvent event) {
    var updatedIssue = previouslyRaisedIssue;
    var resolved = event.getResolved();
    var userSeverity = event.getUserSeverity();
    var userType = event.getUserType();
    var impactedIssueKeys = event.getImpactedIssues().stream().map(IssueChangedEvent.Issue::getIssueKey).collect(Collectors.toSet());
    if (resolved != null) {
      UnaryOperator<RaisedIssueDto> issueUpdater = it -> it.builder().withResolution(resolved).buildIssue();
      updatedIssue = updateIssue(updatedIssue, impactedIssueKeys, issueUpdater);
    }
    if (updatedIssue.getSeverityMode().isLeft()) {
      // if the event does not match the local severity mode, we skip updating as we would only have partial information
      // the data will be updated at the next sync
      var standardModeDetails = updatedIssue.getSeverityMode().getLeft();
      if (userSeverity != null) {
        UnaryOperator<RaisedIssueDto> issueUpdater = it -> it.builder().withStandardModeDetails(IssueSeverity.valueOf(userSeverity.name()), standardModeDetails.getType())
          .buildIssue();
        updatedIssue = updateIssue(updatedIssue, impactedIssueKeys, issueUpdater);
      }
      if (userType != null) {
        UnaryOperator<RaisedIssueDto> issueUpdater = it -> it.builder().withStandardModeDetails(standardModeDetails.getSeverity(), RuleType.valueOf(userType.name()))
          .buildIssue();
        updatedIssue = updateIssue(updatedIssue, impactedIssueKeys, issueUpdater);
      }
    }
    for (var issue : event.getImpactedIssues()) {
      if (!issue.getImpacts().isEmpty() && isMQRMode && updatedIssue.getSeverityMode().isRight()) {
        var mqrModeDetails = updatedIssue.getSeverityMode().getRight();
        var impacts = issue.getImpacts().entrySet().stream()
          .map(impact -> new ImpactDto(
            SoftwareQuality.valueOf(impact.getKey().name()),
            org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity.valueOf(impact.getValue().name())))
          .toList();
        UnaryOperator<RaisedIssueDto> issueUpdater = it -> it.builder()
          .withMQRModeDetails(mqrModeDetails.getCleanCodeAttribute(), mergeImpacts(it.getSeverityMode().getRight().getImpacts(), impacts)).buildIssue();
        updatedIssue = updateIssue(updatedIssue, impactedIssueKeys, issueUpdater);
      }

    }
    return updatedIssue;
  }

  private static List<ImpactDto> mergeImpacts(List<ImpactDto> currentImpacts, List<ImpactDto> overriddenImpacts) {
    var mergedImpacts = new ArrayList<>(currentImpacts);
    for (var impact : overriddenImpacts) {
      mergedImpacts.removeIf(i -> i.getSoftwareQuality().equals(impact.getSoftwareQuality()));
      mergedImpacts.add(new ImpactDto(impact.getSoftwareQuality(), impact.getImpactSeverity()));
    }

    return mergedImpacts;
  }

  private static RaisedIssueDto updateIssue(RaisedIssueDto issue, Set<String> impactedIssueKeys, UnaryOperator<RaisedIssueDto> issueUpdater) {
    var serverKey = issue.getServerKey();
    if (serverKey != null && impactedIssueKeys.contains(serverKey)) {
      return issueUpdater.apply(issue);
    }
    return issue;
  }

  private void updateProjectIssueStorage(String connectionId, IssueChangedEvent event) {
    var findingsStorage = storageService.connection(connectionId).project(event.getProjectKey()).findings();
    event.getImpactedIssues().forEach(issue -> findingsStorage.updateIssue(issue.getIssueKey(), storedIssue -> {
      var userSeverity = event.getUserSeverity();
      if (userSeverity != null) {
        storedIssue.setUserSeverity(userSeverity);
      }
      var userType = event.getUserType();
      if (userType != null) {
        storedIssue.setType(userType);
      }
      var resolved = event.getResolved();
      if (resolved != null) {
        storedIssue.setResolved(resolved);
      }
      var impacts = issue.getImpacts();
      if (!impacts.isEmpty()) {
        storedIssue.setImpacts(mergeImpacts(storedIssue.getImpacts(), impacts));
      }
    }));
  }

  private static Map<org.sonarsource.sonarlint.core.commons.SoftwareQuality, ImpactSeverity> mergeImpacts(
    Map<org.sonarsource.sonarlint.core.commons.SoftwareQuality, ImpactSeverity> defaultImpacts,
    Map<org.sonarsource.sonarlint.core.commons.SoftwareQuality, ImpactSeverity> overriddenImpacts) {
    var mergedImpacts = new EnumMap<org.sonarsource.sonarlint.core.commons.SoftwareQuality, ImpactSeverity>(org.sonarsource.sonarlint.core.commons.SoftwareQuality.class);
    if (!defaultImpacts.isEmpty()) {
      mergedImpacts = new EnumMap<>(defaultImpacts);
    }

    for (var entry : overriddenImpacts.entrySet()) {
      var quality = org.sonarsource.sonarlint.core.commons.SoftwareQuality.valueOf(entry.getKey().name());
      var severity = ImpactSeverity.mapSeverity(entry.getValue().name());
      mergedImpacts.put(quality, severity);
    }

    return Collections.unmodifiableMap(mergedImpacts);
  }

  private static Optional<UUID> asUUID(String key) {
    try {
      return Optional.of(UUID.fromString(key));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
