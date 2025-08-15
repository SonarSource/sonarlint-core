/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry.measures.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ReportIssuesAsOverrideLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ReportIssuesAsErrorLevel;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLiveAttributes;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorage;
import org.sonarsource.sonarlint.core.telemetry.TelemetryReportIssuesAsOverride;

import static org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresValueGranularity.DAILY;
import static org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresValueType.INTEGER;

public class TelemetryMeasuresBuilder {

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

    addReportIssuesAsErrorMeasures(values);

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

  private void addReportIssuesAsErrorMeasures(ArrayList<TelemetryMeasuresValue> values) {
    addReportIssuesAsErrorLevelCount(values, ReportIssuesAsErrorLevel.NONE);
    addReportIssuesAsErrorLevelCount(values, ReportIssuesAsErrorLevel.MEDIUM_AND_ABOVE);
    addReportIssuesAsErrorLevelCount(values, ReportIssuesAsErrorLevel.ALL);

    addReportIssuesAsOverrideCount(values, ReportIssuesAsOverrideLevel.WARNING);
    addReportIssuesAsOverrideCount(values, ReportIssuesAsOverrideLevel.ERROR);
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
    }
  }

  private void addNewBindingsMeasures(ArrayList<TelemetryMeasuresValue> values) {
    if (liveAttributes.usesConnectedMode()) {
      values.add(new TelemetryMeasuresValue("new_bindings.manual", String.valueOf(storage.getNewBindingsManualCount()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("new_bindings.accepted_suggestion_remote_url", String.valueOf(storage.getSuggestedRemoteBindingsCount()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("new_bindings.accepted_suggestion_properties_file", String.valueOf(storage.getNewBindingsPropertiesFileCount()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("new_bindings.accepted_suggestion_shared_config_file",
        String.valueOf(storage.getNewBindingsSharedConfigurationCount()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("new_bindings.accepted_suggestion_project_name", String.valueOf(storage.getNewBindingsProjectNameCount()), INTEGER, DAILY));
    }
  }

  private void addBindingSuggestionClueMeasures(ArrayList<TelemetryMeasuresValue> values) {
    values.add(new TelemetryMeasuresValue("binding_suggestion_clue.remote_url", String.valueOf(storage.getSuggestedRemoteBindingsCount()), INTEGER, DAILY));
  }

  private void addReportIssuesAsErrorLevelCount(List<TelemetryMeasuresValue> values, ReportIssuesAsErrorLevel level) {
    storage.getReportedIssuesAsErrorCountPerLevel().entrySet().stream()
      .filter(e -> e.getValue() > 0 && e.getKey() == level)
      .map(e -> new TelemetryMeasuresValue(
        "reported_issues_as_error_level." + level.name().toLowerCase(Locale.ROOT),
        String.valueOf(e.getValue()),
        INTEGER,
        DAILY
      ))
      .forEach(values::add);
  }

  private void addReportIssuesAsOverrideCount(List<TelemetryMeasuresValue> values, ReportIssuesAsOverrideLevel level) {
    storage.getReportedIssuesAsOverridePerLevel().entrySet().stream()
      .filter(e -> e.getKey() == level)
      .forEach(entry -> {
        var allOverrideCount = entry.getValue().stream().map(TelemetryReportIssuesAsOverride::getCount)
          .mapToInt(Integer::intValue)
          .sum();

        if (allOverrideCount > 0) {
          values.add(new TelemetryMeasuresValue(
            "reported_issues_as_override." + level.name().toLowerCase(Locale.ROOT),
            Integer.toString(allOverrideCount),
            INTEGER,
            DAILY
          ));
        }
      });
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

  private void addToolsMeasures(ArrayList<TelemetryMeasuresValue> values) {
    var calledToolsByName = storage.getCalledToolsByName();
    calledToolsByName.forEach((key, toolCallCounter) -> {
      values.add(new TelemetryMeasuresValue("tools." + key + "_success_count", Integer.toString(toolCallCounter.getSuccess()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("tools." + key + "_error_count", Integer.toString(toolCallCounter.getError()), INTEGER, DAILY));
    });
  }

  private void addFindingsFilteredMeasures(ArrayList<TelemetryMeasuresValue> values) {
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
}
