/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry.measures.payload;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLiveAttributes;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorage;

import static org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresValueGranularity.DAILY;
import static org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresValueType.BOOLEAN;
import static org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresValueType.INTEGER;
import static org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresValueType.STRING;

public class TelemetryMeasuresBuilder {

  private static final String LINK_CLICKED_BASE_NAME = "link_clicked_count_";
  private static final String FEEDBACK_CLICKED_BASE_NAME = "feedback_link_clicked_count_";

  private final String platform;
  private final String product;
  private final TelemetryLocalStorage storage;
  private final TelemetryLiveAttributes liveAttributes;

  public TelemetryMeasuresBuilder(String platform, String product, TelemetryLocalStorage storage, TelemetryLiveAttributes liveAttributes) {
    this.platform = platform;
    this.product = product;
    this.storage = storage;
    this.liveAttributes = liveAttributes;
  }

  public TelemetryMeasuresPayload build() {
    var values = new ArrayList<TelemetryMeasuresValue>();

    addConnectedModeMeasures(values);

    addNewBindingsMeasures(values);

    addBindingSuggestionClueMeasures(values);

    addHelpAndFeedbackMeasures(values);

    addAnalysisReportingMeasures(values);

    addQuickFixMeasures(values);

    addIssuesMeasures(values);

    addToolsMeasures(values);

    addFindingsFilteredMeasures(values);

    addPerformanceMeasures(values);

    addFindingInvestigationMeasures(values);

    addAutomaticAnalysisMeasures(values);

    addMCPMeasures(values);

    addLabsMeasures(values);

    addAiHooksMeasures(values);

    addCampaignsMeasures(values);

    return new TelemetryMeasuresPayload(UUID.randomUUID().toString(), platform, storage.installTime(), product, TelemetryMeasuresDimension.INSTALLATION, values);
  }

  private void addPerformanceMeasures(ArrayList<TelemetryMeasuresValue> values) {
    values.add(new TelemetryMeasuresValue("performance.largest_file_count", String.valueOf(storage.getBiggestNumberOfFilesInConfigScope()), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("performance.largest_file_count_ms", String.valueOf(storage.getListingTimeForBiggestNumberConfigScopeFiles()), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("performance.longest_file_count_ms", String.valueOf(storage.getLongestListingTimeForConfigScopeFiles()), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("performance.longest_file_count", String.valueOf(storage.getNumberOfFilesForLongestFilesListingTimeConfigScope()), INTEGER, DAILY));
  }

  private void addFindingInvestigationMeasures(ArrayList<TelemetryMeasuresValue> values) {
    values.add(new TelemetryMeasuresValue("findings_investigation.taints_locally", String.valueOf(storage.getTaintInvestigatedLocallyCount()), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("findings_investigation.taints_remotely", String.valueOf(storage.getTaintInvestigatedRemotelyCount()), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("findings_investigation.hotspots_locally", String.valueOf(storage.getHotspotInvestigatedLocallyCount()), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("findings_investigation.hotspots_remotely", String.valueOf(storage.getHotspotInvestigatedRemotelyCount()), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("findings_investigation.issues_locally", String.valueOf(storage.getIssueInvestigatedLocallyCount()), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("findings_investigation.dependency_risks_locally", String.valueOf(storage.getDependencyRiskInvestigatedLocallyCount()), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("findings_investigation.dependency_risks_remotely",
      String.valueOf(storage.getDependencyRiskInvestigatedRemotelyCount()), INTEGER, DAILY));
  }

  private void addConnectedModeMeasures(ArrayList<TelemetryMeasuresValue> values) {
    if (liveAttributes.usesConnectedMode()) {
      values.add(new TelemetryMeasuresValue("shared_connected_mode.manual", String.valueOf(storage.getManualAddedBindingsCount()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("shared_connected_mode.imported", String.valueOf(storage.getImportedAddedBindingsCount()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("shared_connected_mode.auto", String.valueOf(storage.getAutoAddedBindingsCount()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("shared_connected_mode.exported", String.valueOf(storage.getExportedConnectedModeCount()), INTEGER, DAILY));

      values.add(new TelemetryMeasuresValue("bindings.child_count", String.valueOf(liveAttributes.countChildBindings()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("bindings.server_count", String.valueOf(liveAttributes.countSonarQubeServerBindings()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("bindings.cloud_eu_count", String.valueOf(liveAttributes.countSonarQubeCloudEUBindings()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("bindings.cloud_us_count", String.valueOf(liveAttributes.countSonarQubeCloudUSBindings()), INTEGER, DAILY));

      if (!liveAttributes.getConnectionsAttributes().isEmpty()) {
        values.add(new TelemetryMeasuresValue("connections.attributes", new Gson().toJson(liveAttributes.getConnectionsAttributes()), STRING, DAILY));
      }
    }
  }

  private void addNewBindingsMeasures(ArrayList<TelemetryMeasuresValue> values) {
    if (liveAttributes.usesConnectedMode()) {
      values.add(new TelemetryMeasuresValue("new_bindings.manual", String.valueOf(storage.getManualAddedBindingsCount()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("new_bindings.accepted_suggestion_remote_url", String.valueOf(storage.getNewBindingsRemoteUrlCount()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("new_bindings.accepted_suggestion_properties_file", String.valueOf(storage.getNewBindingsPropertiesFileCount()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("new_bindings.accepted_suggestion_shared_config_file",
        String.valueOf(storage.getNewBindingsSharedConfigurationCount()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("new_bindings.accepted_suggestion_project_name", String.valueOf(storage.getNewBindingsProjectNameCount()), INTEGER, DAILY));
    }
  }

  private void addBindingSuggestionClueMeasures(ArrayList<TelemetryMeasuresValue> values) {
    values.add(new TelemetryMeasuresValue("binding_suggestion_clue.remote_url", String.valueOf(storage.getSuggestedRemoteBindingsCount()), INTEGER, DAILY));
  }

  private void addHelpAndFeedbackMeasures(List<TelemetryMeasuresValue> values) {
    storage.getHelpAndFeedbackLinkClickedCounter().entrySet().stream()
      .filter(e -> e.getValue().getHelpAndFeedbackLinkClickedCount() > 0)
      .map(e -> new TelemetryMeasuresValue(
        "help_and_feedback." + e.getKey().toLowerCase(Locale.ROOT),
        String.valueOf(e.getValue().getHelpAndFeedbackLinkClickedCount()),
        INTEGER,
        DAILY
      ))
      .forEach(values::add);
  }

  private void addAnalysisReportingMeasures(List<TelemetryMeasuresValue> values) {
    storage.getAnalysisReportingCountersByType().entrySet().stream()
      .filter(e -> e.getValue().getAnalysisReportingCount() > 0)
      .map(e -> new TelemetryMeasuresValue(
        "analysis_reporting." + e.getKey().getId(),
        String.valueOf(e.getValue().getAnalysisReportingCount()),
        INTEGER,
        DAILY
      ))
      .forEach(values::add);
  }

  private void addQuickFixMeasures(List<TelemetryMeasuresValue> values) {
    var allQuickFixCount = storage.getQuickFixCountByRuleKey().values().stream()
      .mapToInt(Integer::intValue)
      .sum();
    if (allQuickFixCount > 0) {
      values.add(new TelemetryMeasuresValue(
        "quick_fix.applied_count",
        Integer.toString(allQuickFixCount),
        INTEGER,
        DAILY
      ));
    }
  }

  private void addIssuesMeasures(List<TelemetryMeasuresValue> values) {
    var newIssuesFound = storage.getNewIssuesFoundCount();
    if (newIssuesFound > 0) {
      values.add(new TelemetryMeasuresValue("ide_issues.found", Long.toString(newIssuesFound), INTEGER, DAILY));
    }
    var issuesFixed = storage.getIssuesFixedCount();
    if (issuesFixed > 0) {
      values.add(new TelemetryMeasuresValue("ide_issues.fixed", Long.toString(issuesFixed), INTEGER, DAILY));
    }
  }

  private void addToolsMeasures(List<TelemetryMeasuresValue> values) {
    var calledToolsByName = storage.getCalledToolsByName();
    calledToolsByName.forEach((key, toolCallCounter) -> {
      values.add(new TelemetryMeasuresValue("tools." + key + "_success_count", Integer.toString(toolCallCounter.getSuccess()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("tools." + key + "_error_count", Integer.toString(toolCallCounter.getError()), INTEGER, DAILY));
    });
  }

  private void addFindingsFilteredMeasures(List<TelemetryMeasuresValue> values) {
    storage.getFindingsFilteredCountersByType().entrySet().stream()
      .filter(e -> e.getValue().getFindingsFilteredCount() > 0)
      .map(e -> new TelemetryMeasuresValue(
        "findings_filtered." + e.getKey().toLowerCase(Locale.ROOT),
        String.valueOf(e.getValue().getFindingsFilteredCount()),
        INTEGER,
        DAILY
      ))
      .forEach(values::add);
  }

  private void addAutomaticAnalysisMeasures(List<TelemetryMeasuresValue> values) {
    values.add(new TelemetryMeasuresValue("automatic_analysis.enabled", String.valueOf(storage.isAutomaticAnalysisEnabled()), BOOLEAN, DAILY));
    values.add(new TelemetryMeasuresValue("automatic_analysis.toggled_count", String.valueOf(storage.getAutomaticAnalysisToggledCount()), INTEGER, DAILY));
  }

  private void addMCPMeasures(List<TelemetryMeasuresValue> values) {
    values.add(new TelemetryMeasuresValue("mcp.configuration_requested", String.valueOf(storage.getMcpServerConfigurationRequestedCount()), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("mcp.rule_file_requested", String.valueOf(storage.getMcpRuleFileRequestedCount()), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("mcp.integration_enabled", Boolean.toString(storage.isMcpIntegrationEnabled()), BOOLEAN, DAILY));
    var mcpTransportModeUsed = storage.getMcpTransportModeUsed();
    if (mcpTransportModeUsed != null) {
      values.add(new TelemetryMeasuresValue("mcp.transport_mode", mcpTransportModeUsed.name(), STRING, DAILY));
    }
  }

  private void addLabsMeasures(ArrayList<TelemetryMeasuresValue> values) {
    values.add(new TelemetryMeasuresValue("ide_labs.joined", String.valueOf(liveAttributes.hasJoinedIdeLabs()), BOOLEAN, DAILY));
    values.add(new TelemetryMeasuresValue("ide_labs.enabled", String.valueOf(liveAttributes.hasEnabledIdeLabs()), BOOLEAN, DAILY));
    addAll(storage.getLabsLinkClickedCount(), LINK_CLICKED_BASE_NAME, values);
    addAll(storage.getLabsFeedbackLinkClickedCount(), FEEDBACK_CLICKED_BASE_NAME, values);
  }

  private static void addAll(Map<String, Integer> clickCounts, String baseName, List<TelemetryMeasuresValue> values) {
    clickCounts.entrySet().stream()
      .filter(entry -> entry.getValue() > 0)
      .map(entry -> new TelemetryMeasuresValue(
        "ide_labs." + baseName + entry.getKey(),
        String.valueOf(entry.getValue()),
        INTEGER,
        DAILY))
      .forEach(values::add);
  }

  private void addAiHooksMeasures(ArrayList<TelemetryMeasuresValue> values) {
    storage.getAiHooksInstalledCount().entrySet().stream()
      .filter(entry -> entry.getValue() > 0)
      .map(entry -> new TelemetryMeasuresValue(
        "ai_hooks." + entry.getKey().name().toLowerCase(Locale.ROOT) + "_installed",
        String.valueOf(entry.getValue()),
        INTEGER,
        DAILY))
      .forEach(values::add);
  }

  private void addCampaignsMeasures(ArrayList<TelemetryMeasuresValue> values) {
    storage.getCampaignsShown().entrySet().stream()
      .filter(entry -> entry.getValue() > 0)
      .map(campaignShown -> new TelemetryMeasuresValue(
        "campaigns."  + campaignShown.getKey() + "_shown",
        String.valueOf(campaignShown.getValue()),
        INTEGER,
        DAILY))
      .forEach(values::add);
    storage.getCampaignsResolutions().entrySet().stream()
      .map(campaignsResolution -> new TelemetryMeasuresValue(
        "campaigns."  + campaignsResolution.getKey() + "_resolution",
        campaignsResolution.getValue(),
        STRING,
        DAILY))
      .forEach(values::add);
  }
}
