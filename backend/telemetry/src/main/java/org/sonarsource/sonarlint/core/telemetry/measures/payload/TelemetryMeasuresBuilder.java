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
import org.sonarsource.sonarlint.core.telemetry.TelemetryLiveAttributes;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorage;

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

    addHelpAndFeedbackMeasures(values);

    addQuickFixMeasures(values);

    addIssuesMeasures(values);

    return new TelemetryMeasuresPayload(UUID.randomUUID().toString(), platform, storage.installTime(), product, TelemetryMeasuresDimension.INSTALLATION, values);
  }

  private void addConnectedModeMeasures(ArrayList<TelemetryMeasuresValue> values) {
    if (liveAttributes.usesConnectedMode()) {
      values.add(new TelemetryMeasuresValue("shared_connected_mode.manual", String.valueOf(storage.getManualAddedBindingsCount()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("shared_connected_mode.imported", String.valueOf(storage.getImportedAddedBindingsCount()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("shared_connected_mode.auto", String.valueOf(storage.getAutoAddedBindingsCount()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("shared_connected_mode.exported", String.valueOf(storage.getExportedConnectedModeCount()), INTEGER, DAILY));

      values.add(new TelemetryMeasuresValue("bindings.server_count", String.valueOf(liveAttributes.countSonarQubeServerBindings()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("bindings.cloud_eu_count", String.valueOf(liveAttributes.countSonarQubeCloudEUBindings()), INTEGER, DAILY));
      values.add(new TelemetryMeasuresValue("bindings.cloud_us_count", String.valueOf(liveAttributes.countSonarQubeCloudUSBindings()), INTEGER, DAILY));
    }
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
    var newIssuesFound = storage.getNewIssueFoundCount();
    if (newIssuesFound > 0) {
      values.add(new TelemetryMeasuresValue("ide_issues.found", Integer.toString(newIssuesFound), INTEGER, DAILY));
    }
  }
}
