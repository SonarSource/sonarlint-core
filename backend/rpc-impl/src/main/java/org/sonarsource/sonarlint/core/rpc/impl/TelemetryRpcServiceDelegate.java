/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.GetStatusResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddQuickFixAppliedForRuleParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddReportedRulesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisDoneOnSingleLanguageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisReportingTriggeredParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.DevNotificationsClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionResolvedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.HelpAndFeedbackClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ToolCalledParams;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;

class TelemetryRpcServiceDelegate extends AbstractRpcServiceDelegate implements TelemetryRpcService {

  public TelemetryRpcServiceDelegate(SonarLintRpcServerImpl server) {
    super(server);
  }

  @Override
  public CompletableFuture<GetStatusResponse> getStatus() {
    return requestAsync(cancelMonitor -> getBean(TelemetryService.class).getStatus());
  }

  @Override
  public void enableTelemetry() {
    notify(() -> getBean(TelemetryService.class).enableTelemetry());
  }

  @Override
  public void disableTelemetry() {
    notify(() -> getBean(TelemetryService.class).disableTelemetry());
  }

  @Override
  public void analysisDoneOnSingleLanguage(AnalysisDoneOnSingleLanguageParams params) {
    notify(() -> getBean(TelemetryService.class).analysisDoneOnSingleLanguage(params.getLanguage(), params.getAnalysisTimeMs()));
  }

  @Override
  public void analysisDoneOnMultipleFiles() {
    notify(() -> getBean(TelemetryService.class).analysisDoneOnMultipleFiles());
  }

  @Override
  public void devNotificationsClicked(DevNotificationsClickedParams params) {
    notify(() -> getBean(TelemetryService.class).smartNotificationsClicked(params.getEventType()));
  }

  @Override
  public void taintVulnerabilitiesInvestigatedLocally() {
    notify(() -> getBean(TelemetryService.class).taintVulnerabilitiesInvestigatedLocally());
  }

  @Override
  public void taintVulnerabilitiesInvestigatedRemotely() {
    notify(() -> getBean(TelemetryService.class).taintVulnerabilitiesInvestigatedRemotely());
  }

  @Override
  public void addReportedRules(AddReportedRulesParams params) {
    notify(() -> getBean(TelemetryService.class).addReportedRules(params.getRuleKeys()));
  }

  @Override
  public void addQuickFixAppliedForRule(AddQuickFixAppliedForRuleParams params) {
    notify(() -> getBean(TelemetryService.class).addQuickFixAppliedForRule(params.getRuleKey()));
  }

  @Override
  public void helpAndFeedbackLinkClicked(HelpAndFeedbackClickedParams params) {
    notify(() -> getBean(TelemetryService.class).helpAndFeedbackLinkClicked(params));
  }

  @Override
  public void toolCalled(ToolCalledParams params) {
    notify(() -> getBean(TelemetryService.class).toolCalled(params));
  }

  @Override
  public void analysisReportingTriggered(AnalysisReportingTriggeredParams params) {
    notify(() -> getBean(TelemetryService.class).analysisReportingTriggered(params));
  }

  @Override
  public void fixSuggestionResolved(FixSuggestionResolvedParams params) {
    notify(() -> getBean(TelemetryService.class).fixSuggestionResolved(params));
  }

  @Override
  public void addedManualBindings() {
    notify(() -> getBean(TelemetryService.class).addedManualBindings());
  }

  @Override
  public void addedImportedBindings() {
    notify(() -> getBean(TelemetryService.class).addedImportedBindings());
  }

  @Override
  public void addedAutomaticBindings() {
    notify(() -> getBean(TelemetryService.class).addedAutomaticBindings());
  }

  @Override
  public void taintInvestigatedLocally() {
    notify(() -> getBean(TelemetryService.class).taintInvestigatedLocally());
  }

  @Override
  public void taintInvestigatedRemotely() {
    notify(() -> getBean(TelemetryService.class).taintInvestigatedRemotely());
  }

  @Override
  public void hotspotInvestigatedLocally() {
    notify(() -> getBean(TelemetryService.class).hotspotInvestigatedLocally());
  }

  @Override
  public void hotspotInvestigatedRemotely() {
    notify(() -> getBean(TelemetryService.class).hotspotInvestigatedRemotely());
  }

  @Override
  public void issueInvestigatedLocally() {
    notify(() -> getBean(TelemetryService.class).issueInvestigatedLocally());
  }
}
