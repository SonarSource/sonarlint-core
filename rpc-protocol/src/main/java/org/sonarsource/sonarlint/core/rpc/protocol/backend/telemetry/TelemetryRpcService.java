/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AcceptedBindingSuggestionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddQuickFixAppliedForRuleParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddReportedRulesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisDoneOnSingleLanguageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisReportingTriggeredParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.DevNotificationsClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.IdeLabsExternalLinkClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FindingsFilteredParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionResolvedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.IdeLabsFeedbackLinkClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.HelpAndFeedbackClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.McpTransportModeUsedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ToggleIdeLabsEnablementParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ToolCalledParams;

@JsonSegment("telemetry")
public interface TelemetryRpcService {

  @JsonRequest
  CompletableFuture<GetStatusResponse> getStatus();

  @JsonNotification
  void enableTelemetry();

  @JsonNotification
  void disableTelemetry();

  /**
   * @deprecated managed automatically when using {@link org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService#analyzeFilesAndTrack(AnalyzeFilesAndTrackParams)}
   * it is still used by VS because of the C# analysis handled on the client side
   */
  @JsonNotification
  @Deprecated(since = "10.1")
  void analysisDoneOnSingleLanguage(AnalysisDoneOnSingleLanguageParams params);

  /**
   * @deprecated managed automatically when using {@link org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService#analyzeFilesAndTrack(AnalyzeFilesAndTrackParams)}
   * it is still used by VS because of the C# analysis handled on the client side
   */
  @JsonNotification
  @Deprecated(since = "10.1")
  void analysisDoneOnMultipleFiles();

  @JsonNotification
  void devNotificationsClicked(DevNotificationsClickedParams params);

  @JsonNotification
  void taintVulnerabilitiesInvestigatedLocally();

  @JsonNotification
  void taintVulnerabilitiesInvestigatedRemotely();

  /**
   * @deprecated managed automatically when using {@link org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService#analyzeFilesAndTrack(AnalyzeFilesAndTrackParams)}
   * it is still used by VS because of the C# analysis handled on the client side
   */
  @JsonNotification
  @Deprecated(since = "10.1")
  void addReportedRules(AddReportedRulesParams params);

  @JsonNotification
  void addQuickFixAppliedForRule(AddQuickFixAppliedForRuleParams params);

  @JsonNotification
  void helpAndFeedbackLinkClicked(HelpAndFeedbackClickedParams params);

  /**
   * To be called from SonarQube MCP Server when SQ:IDE integration is enabled and valid
   * This is tracking if SQ:IDE integration was enabled at least once during the day
   */
  @JsonNotification
  void mcpIntegrationEnabled();

  /**
   * To be called from SonarQube MCP Server after initialization
   * This is tracking the transport type on which the MCP is running
   */
  @JsonNotification
  void mcpTransportModeUsed(McpTransportModeUsedParams params);

  @JsonNotification
  void toolCalled(ToolCalledParams params);

  /**
   * Should be used to track the usage of specific types of analysis
   * This includes analysis of VCS changed files, pre-commit analysis or all project files analysis
   */
  @JsonNotification
  void analysisReportingTriggered(AnalysisReportingTriggeredParams params);

  @JsonNotification
  void fixSuggestionResolved(FixSuggestionResolvedParams params);

  /**
   * This method should be called when binding is created manually (not through binding suggestion or assistance).
   */
  @JsonNotification
  void addedManualBindings();

  /**
   * This method should be called when binding is created from a suggestion and .
   */
  @JsonNotification
  void acceptedBindingSuggestion(AcceptedBindingSuggestionParams origin);

  /**
   * @deprecated avoid calling this method if possible, since it will be removed once all the clients are migrated.
   * Rely on providing the {@link org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingMode)} and
   * {@link org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionOrigin)} while calling the
   * {@link org.sonarsource.sonarlint.core.rpc.protocol.backend.config.ConfigurationRpcService#didUpdateBinding(DidUpdateBindingParams)} )}
   * within the DidUpdateBindingParams.
   */
  @Deprecated(forRemoval = true)
  @JsonNotification
  void addedImportedBindings();

  /**
   * @deprecated avoid calling this method if possible, since it will be removed once all the clients are migrated.
   * Rely on providing the {@link org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingMode)} and
   * {@link org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionOrigin)} while calling the
   * {@link org.sonarsource.sonarlint.core.rpc.protocol.backend.config.ConfigurationRpcService#didUpdateBinding(DidUpdateBindingParams)} )}
   * within the DidUpdateBindingParams.
   */
  @Deprecated(forRemoval = true)
  @JsonNotification
  void addedAutomaticBindings();

  @JsonNotification
  void taintInvestigatedLocally();

  @JsonNotification
  void taintInvestigatedRemotely();

  @JsonNotification
  void hotspotInvestigatedLocally();

  @JsonNotification
  void hotspotInvestigatedRemotely();

  @JsonNotification
  void issueInvestigatedLocally();

  @JsonNotification
  void dependencyRiskInvestigatedLocally();

  @JsonNotification
  void findingsFiltered(FindingsFilteredParams params);

  @JsonNotification
  void toggleIdeLabsEnablement(ToggleIdeLabsEnablementParams params);

  @JsonNotification
  void ideLabsExternalLinkClicked(IdeLabsExternalLinkClickedParams params);

  @JsonNotification
  void ideLabsFeedbackLinkClicked(IdeLabsFeedbackLinkClickedParams params);
}
