/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.telemetry;

import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.PreDestroy;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.AnalysisFinishedEvent;
import org.sonarsource.sonarlint.core.analysis.AutomaticAnalysisSettingChangedEvent;
import org.sonarsource.sonarlint.core.analysis.IssuesRaisedEvent;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.util.FailSafeExecutors;
import org.sonarsource.sonarlint.core.event.FixSuggestionReceivedEvent;
import org.sonarsource.sonarlint.core.event.LocalOnlyIssueStatusChangedEvent;
import org.sonarsource.sonarlint.core.event.MatchingSessionEndedEvent;
import org.sonarsource.sonarlint.core.event.ServerIssueStatusChangedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionOrigin;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.GetStatusResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisReportingTriggeredParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionResolvedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.HelpAndFeedbackClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.McpTransportMode;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ToolCalledParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.springframework.context.event.EventListener;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.TELEMETRY;

public class TelemetryService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final long TELEMETRY_UPLOAD_DELAY = TimeUnit.HOURS.toMinutes(TelemetryManager.MIN_HOURS_BETWEEN_UPLOAD + 1L);

  private final ScheduledExecutorService scheduledExecutor;
  private final TelemetryManager telemetryManager;
  private final TelemetryServerAttributesProvider telemetryServerAttributesProvider;
  private final SonarLintRpcClient client;
  private final boolean isTelemetryFeatureEnabled;

  public TelemetryService(InitializeParams initializeParams, SonarLintRpcClient sonarlintClient,
    TelemetryServerAttributesProvider telemetryServerAttributesProvider, TelemetryManager telemetryManager) {
    this.isTelemetryFeatureEnabled = initializeParams.getBackendCapabilities().contains(TELEMETRY);
    this.client = sonarlintClient;
    this.telemetryServerAttributesProvider = telemetryServerAttributesProvider;
    this.telemetryManager = telemetryManager;
    this.scheduledExecutor = FailSafeExecutors.newSingleThreadScheduledExecutor("SonarLint Telemetry");

    initTelemetryAndScheduleUpload(initializeParams);
  }

  private void initTelemetryAndScheduleUpload(InitializeParams initializeParams) {
    if (!isTelemetryFeatureEnabled) {
      LOG.info("Telemetry disabled on server startup");
      return;
    }
    updateTelemetry(localStorage -> {
      localStorage.setInitialNewCodeFocus(initializeParams.isFocusOnNewCode());
      localStorage.setInitialAutomaticAnalysisEnablement(initializeParams.isAutomaticAnalysisEnabled());
    });
    var initialDelay = Integer.parseInt(System.getProperty("sonarlint.internal.telemetry.initialDelay", "1"));
    scheduledExecutor.scheduleWithFixedDelay(this::upload, initialDelay, TELEMETRY_UPLOAD_DELAY, MINUTES);
  }

  private void upload() {
    var telemetryLiveAttributes = getTelemetryLiveAttributes();
    if (Objects.nonNull(telemetryLiveAttributes)) {
      telemetryManager.uploadAndClearTelemetry(telemetryLiveAttributes);
    }
  }

  public GetStatusResponse getStatus() {
    return new GetStatusResponse(isEnabled());
  }

  public void enableTelemetry() {
    if (!isTelemetryFeatureEnabled) {
      LOG.warn("Telemetry was disabled on server startup. Ignoring client request.");
      return;
    }
    var telemetryLiveAttributes = getTelemetryLiveAttributes();
    if (Objects.nonNull(telemetryLiveAttributes)) {
      telemetryManager.enable(telemetryLiveAttributes);
    }
  }

  public void disableTelemetry() {
    var telemetryLiveAttributes = getTelemetryLiveAttributes();
    if (Objects.nonNull(telemetryLiveAttributes)) {
      telemetryManager.disable(telemetryLiveAttributes);
    }
  }

  @Nullable
  private TelemetryLiveAttributes getTelemetryLiveAttributes() {
    try {
      var serverLiveAttributes = telemetryServerAttributesProvider.getTelemetryServerLiveAttributes();
      var clientLiveAttributes = client.getTelemetryLiveAttributes().get(10, TimeUnit.SECONDS);
      return new TelemetryLiveAttributes(serverLiveAttributes, clientLiveAttributes);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      if (InternalDebug.isEnabled()) {
        LOG.error("Failed to fetch telemetry payload", e);
      }
    } catch (Exception e) {
      if (InternalDebug.isEnabled()) {
        LOG.error("Failed to fetch telemetry payload", e);
      }
    }
    return null;
  }

  public boolean isEnabled() {
    return isTelemetryFeatureEnabled && telemetryManager.isTelemetryEnabledByUser();
  }

  private void updateTelemetry(Consumer<TelemetryLocalStorage> updater) {
    if (isEnabled()) {
      telemetryManager.updateTelemetry(updater);
    }
  }

  public void hotspotOpenedInBrowser() {
    updateTelemetry(TelemetryLocalStorage::incrementOpenHotspotInBrowserCount);
  }

  public void showHotspotRequestReceived() {
    updateTelemetry(TelemetryLocalStorage::incrementShowHotspotRequestCount);
  }

  public void showIssueRequestReceived() {
    updateTelemetry(TelemetryLocalStorage::incrementShowIssueRequestCount);
  }

  public void taintVulnerabilitiesInvestigatedLocally() {
    updateTelemetry(TelemetryLocalStorage::incrementTaintVulnerabilitiesInvestigatedLocallyCount);
  }

  public void taintVulnerabilitiesInvestigatedRemotely() {
    updateTelemetry(TelemetryLocalStorage::incrementTaintVulnerabilitiesInvestigatedRemotelyCount);
  }

  public void helpAndFeedbackLinkClicked(HelpAndFeedbackClickedParams params) {
    updateTelemetry(localStorage -> localStorage.helpAndFeedbackLinkClicked(params.getItemId()));
  }

  public void analysisReportingTriggered(AnalysisReportingTriggeredParams params) {
    updateTelemetry(localStorage -> localStorage.analysisReportingTriggered(params.getAnalysisType()));
  }

  public void fixSuggestionResolved(FixSuggestionResolvedParams params) {
    updateTelemetry(localStorage -> localStorage.fixSuggestionResolved(params.getSuggestionId(), params.getStatus(), params.getSnippetIndex()));
  }

  public void smartNotificationsReceived(String eventType) {
    updateTelemetry(localStorage -> localStorage.incrementDevNotificationsCount(eventType));
  }

  public void analysisDoneOnSingleLanguage(@Nullable Language language, int analysisTimeMs) {
    updateTelemetry(localStorage -> {
      var languageName = ofNullable(language)
        .map(Enum::name)
        .map(SonarLanguage::valueOf)
        .map(SonarLanguage::getSonarLanguageKey)
        .orElse("others");
      localStorage.setUsedAnalysis(languageName, analysisTimeMs);
    });
  }

  public void analysisDoneOnMultipleFiles() {
    updateTelemetry(TelemetryLocalStorage::setUsedAnalysis);
  }

  public void smartNotificationsClicked(String eventType) {
    updateTelemetry(localStorage -> localStorage.incrementDevNotificationsClicked(eventType));
  }

  public void addQuickFixAppliedForRule(String ruleKey) {
    updateTelemetry(localStorage -> localStorage.addQuickFixAppliedForRule(ruleKey));
  }

  public void addReportedRules(Set<String> ruleKeys) {
    updateTelemetry(s -> s.addReportedRules(ruleKeys));
  }

  public void hotspotStatusChanged() {
    updateTelemetry(TelemetryLocalStorage::incrementHotspotStatusChangedCount);
  }

  public void newCodeFocusChanged() {
    updateTelemetry(TelemetryLocalStorage::incrementNewCodeFocusChange);
  }

  private void issueStatusChanged(String ruleKey) {
    updateTelemetry(telemetryLocalStorage -> telemetryLocalStorage.addIssueStatusChanged(ruleKey));
  }

  public void addedManualBindings() {
    updateTelemetry(TelemetryLocalStorage::incrementManualAddedBindingsCount);
  }

  public void addedImportedBindings() {
    updateTelemetry(TelemetryLocalStorage::incrementImportedAddedBindingsCount);
  }

  public void addedAutomaticBindings() {
    updateTelemetry(TelemetryLocalStorage::incrementAutoAddedBindingsCount);
  }

  public void acceptedBindingSuggestion(BindingSuggestionOrigin bindingSuggestionOrigin) {
    if (bindingSuggestionOrigin.equals(BindingSuggestionOrigin.REMOTE_URL)) {
      updateTelemetry(TelemetryLocalStorage::incrementNewBindingsRemoteUrlCount);
    }

    if (bindingSuggestionOrigin.equals(BindingSuggestionOrigin.PROJECT_NAME)) {
      updateTelemetry(TelemetryLocalStorage::incrementNewBindingsProjectNameCount);
    }

    if (bindingSuggestionOrigin.equals(BindingSuggestionOrigin.SHARED_CONFIGURATION)) {
      updateTelemetry(TelemetryLocalStorage::incrementNewBindingsSharedConfigurationCount);
    }

    if (bindingSuggestionOrigin.equals(BindingSuggestionOrigin.PROPERTIES_FILE)) {
      updateTelemetry(TelemetryLocalStorage::incrementNewBindingsPropertiesFileCount);
    }
  }

  public void exportedConnectedMode() {
    updateTelemetry(TelemetryLocalStorage::incrementExportedConnectedModeCount);
  }

  public void suggestedRemoteBinding() {
    updateTelemetry(TelemetryLocalStorage::incrementSuggestedRemoteBindingsCount);
  }

  public void mcpIntegrationEnabled() {
    updateTelemetry(storage -> storage.setMcpIntegrationEnabled(true));
  }

  public void mcpTransportModeUsed(McpTransportMode transportMode) {
    updateTelemetry(storage -> storage.setMcpTransportModeUsed(transportMode));
  }

  public void toolCalled(ToolCalledParams params) {
    updateTelemetry(storage -> storage.incrementToolCalledCount(params.getToolName(), params.isSucceeded()));
  }

  public void taintInvestigatedLocally() {
    updateTelemetry(TelemetryLocalStorage::incrementTaintInvestigatedLocallyCount);
  }

  public void taintInvestigatedRemotely() {
    updateTelemetry(TelemetryLocalStorage::incrementTaintInvestigatedRemotelyCount);
  }

  public void hotspotInvestigatedLocally() {
    updateTelemetry(TelemetryLocalStorage::incrementHotspotInvestigatedLocallyCount);
  }

  public void hotspotInvestigatedRemotely() {
    updateTelemetry(TelemetryLocalStorage::incrementHotspotInvestigatedRemotelyCount);
  }

  public void issueInvestigatedLocally() {
    updateTelemetry(TelemetryLocalStorage::incrementIssueInvestigatedLocallyCount);
  }

  public void dependencyRiskInvestigatedRemotely() {
    updateTelemetry(TelemetryLocalStorage::incrementDependencyRiskInvestigatedRemotelyCount);
  }

  public void dependencyRiskInvestigatedLocally() {
    updateTelemetry(TelemetryLocalStorage::incrementDependencyRiskInvestigatedLocallyCount);
  }

  public void findingsFiltered(String filterType) {
    updateTelemetry(localStorage -> localStorage.findingsFiltered(filterType));
  }

  public void automaticAnalysisSettingToggled() {
    updateTelemetry(TelemetryLocalStorage::incrementAutomaticAnalysisToggledCount);
  }

  public void flightRecorderStarted() {
    updateTelemetry(TelemetryLocalStorage::incrementFlightRecorderSessionsCount);
  }

  public void mcpServerConfigurationRequested() {
    updateTelemetry(TelemetryLocalStorage::incrementMcpServerConfigurationRequestedCount);
  }

  public void ideLabsLinkClicked(String linkId) {
    updateTelemetry(storage -> storage.ideLabsLinkClicked(linkId));
  }

  public void ideLabsFeedbackLinkClicked(String featureId) {
    updateTelemetry(storage -> storage.ideLabsFeedbackLinkClicked(featureId));
  }

  public void aiHookInstalled(AiAgent aiAgent) {
    updateTelemetry(storage -> storage.aiHookInstalled(aiAgent));
  }

  @EventListener
  public void onMatchingSessionEnded(MatchingSessionEndedEvent event) {
    updateTelemetry(telemetryLocalStorage -> {
      telemetryLocalStorage.addNewlyFoundIssues(event.newIssuesFound());
      telemetryLocalStorage.addFixedIssues(event.issuesFixed());
    });
  }

  @EventListener
  public void onAutomaticAnalysisSettingChanged(AutomaticAnalysisSettingChangedEvent event) {
    automaticAnalysisSettingToggled();
  }

  @EventListener
  public void onServerIssueStatusChanged(ServerIssueStatusChangedEvent event) {
    issueStatusChanged(event.getFinding().getRuleKey());
  }

  @EventListener
  public void onLocalOnlyIssueStatusChanged(LocalOnlyIssueStatusChangedEvent event) {
    issueStatusChanged(event.getIssue().getRuleKey());
  }

  @EventListener
  public void onAnalysisFinished(AnalysisFinishedEvent event) {
    var languagePerFile = event.getLanguagePerFile();
    if (languagePerFile.size() == 1 && event.succeededForAllFiles()) {
      var fileLanguage = languagePerFile.entrySet().iterator().next().getValue();
      analysisDoneOnSingleLanguage(fileLanguage == null ? null : Language.valueOf(fileLanguage.name()), (int) event.getAnalysisDuration().toMillis());
    } else {
      analysisDoneOnMultipleFiles();
    }
    addReportedRules(event.getReportedRuleKeys());
  }

  @EventListener
  public void onFixSuggestionReceived(FixSuggestionReceivedEvent event) {
    updateTelemetry(localStorage -> localStorage.fixSuggestionReceived(
      event.fixSuggestionId(),
      event.source(),
      event.snippetsCount(),
      event.wasGeneratedFromIde()));
  }

  @EventListener
  public void onIssuesRaised(IssuesRaisedEvent event) {
    var issuesToReport = event.issues().stream()
      .filter(RaisedIssueDto::isAiCodeFixable)
      .map(RaisedFindingDto::getId)
      .collect(Collectors.toSet());
    updateTelemetry(localStorage -> localStorage.addIssuesWithPossibleAiFixFromIde(issuesToReport));
  }

  @PreDestroy
  public void close() {
    if ((!MoreExecutors.shutdownAndAwaitTermination(scheduledExecutor, 1, TimeUnit.SECONDS)) && (InternalDebug.isEnabled())) {
      LOG.error("Failed to stop telemetry executor");
    }
  }

  public void updateListFilesPerformance(int size, long timeMs) {
    if (!isTelemetryFeatureEnabled) {
      LOG.info("Telemetry disabled on server startup");
      return;
    }
    updateTelemetry(localStorage -> localStorage.updateListFilesPerformance(size, timeMs));
  }
}
