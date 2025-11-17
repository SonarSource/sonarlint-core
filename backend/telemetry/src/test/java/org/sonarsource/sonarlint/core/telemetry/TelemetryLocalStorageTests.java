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
package org.sonarsource.sonarlint.core.telemetry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.McpTransportMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorage.isOlder;

class TelemetryLocalStorageTests {
  @Test
  void usedAnalysis_should_increment_num_days_on_first_run() {
    var data = new TelemetryLocalStorage();
    assertThat(data.numUseDays()).isZero();

    data.setUsedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);
  }

  @Test
  void usedAnalysis_should_not_increment_num_days_on_same_day() {
    var data = new TelemetryLocalStorage();
    assertThat(data.numUseDays()).isZero();

    data.setUsedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);

    data.setUsedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);
  }

  @Test
  void usedAnalysis_with_duration_should_register_analyzer_performance() {
    var data = new TelemetryLocalStorage();
    assertThat(data.numUseDays()).isZero();
    assertThat(data.analyzers()).isEmpty();

    data.setUsedAnalysis("java", 1000);
    data.setUsedAnalysis("js", 2000);

    assertThat(data.numUseDays()).isEqualTo(1);

    data.setUsedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);

    assertThat(data.analyzers()).hasSize(2);
    assertThat(data.analyzers().get("java").analysisCount()).isEqualTo(1);
    assertThat(data.analyzers().get("java").frequencies()).containsOnly(
      entry("0-300", 0),
      entry("300-500", 0),
      entry("500-1000", 0),
      entry("1000-2000", 1),
      entry("2000-4000", 0),
      entry("4000+", 0));

    assertThat(data.analyzers().get("js").analysisCount()).isEqualTo(1);
    assertThat(data.analyzers().get("js").frequencies()).containsOnly(
      entry("0-300", 0),
      entry("300-500", 0),
      entry("500-1000", 0),
      entry("1000-2000", 0),
      entry("2000-4000", 1),
      entry("4000+", 0));
  }

  @Test
  void usedAnalysis_should_increment_num_days_when_day_changed() {
    var data = new TelemetryLocalStorage();
    assertThat(data.numUseDays()).isZero();

    data.setUsedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);

    data.setUsedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);

    data.setLastUseDate(LocalDate.now().minusDays(1));
    data.setUsedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(2);
  }

  @Test
  void test_isOlder_LocalDate() {
    var date = LocalDate.now();

    assertThat(isOlder((LocalDate) null, null)).isTrue();
    assertThat(isOlder(null, date)).isTrue();
    assertThat(isOlder(date, null)).isFalse();
    assertThat(isOlder(date, date)).isFalse();
    assertThat(isOlder(date, date.plusDays(1))).isTrue();
  }

  @Test
  void test_isOlder_LocalDateTime() {
    var date = LocalDateTime.now();

    assertThat(isOlder((LocalDateTime) null, null)).isTrue();
    assertThat(isOlder(null, date)).isTrue();
    assertThat(isOlder(date, null)).isFalse();
    assertThat(isOlder(date, date)).isFalse();
    assertThat(isOlder(date, date.plusDays(1))).isTrue();
  }

  @Test
  void validate_should_reset_installTime_if_in_future() {
    var data = new TelemetryLocalStorage();
    var now = OffsetDateTime.now();

    data.validateAndMigrate();
    assertThat(data.installTime()).is(within3SecOfNow);

    data.setInstallTime(now.plusDays(1));
    data.validateAndMigrate();
    assertThat(data.installTime()).is(within3SecOfNow);
  }

  private final Condition<OffsetDateTime> within3SecOfNow = new Condition<>(p -> {
    var now = OffsetDateTime.now();
    return Math.abs(p.until(now, ChronoUnit.SECONDS)) < 3;
  }, "within3Sec");

  private final Condition<OffsetDateTime> about5DaysAgo = new Condition<>(p -> {
    var fiveDaysAgo = OffsetDateTime.now().minusDays(5);
    return Math.abs(p.until(fiveDaysAgo, ChronoUnit.SECONDS)) < 3;
  }, "about5DaysAgo");

  @Test
  void validate_should_reset_lastUseDate_if_in_future() {
    var data = new TelemetryLocalStorage();
    var today = LocalDate.now();

    data.setLastUseDate(today.plusDays(1));
    data.validateAndMigrate();
    assertThat(data.lastUseDate()).isEqualTo(today);
  }

  @Test
  void should_migrate_installDate() {
    var data = new TelemetryLocalStorage();
    data.setInstallDate(LocalDate.now().minusDays(5));
    data.validateAndMigrate();
    assertThat(data.installTime()).is(about5DaysAgo);
  }

  @Test
  void validate_should_reset_lastUseDate_if_before_installTime() {
    var data = new TelemetryLocalStorage();
    var now = OffsetDateTime.now();

    data.setInstallTime(now);
    data.setLastUseDate(now.minusDays(1).toLocalDate());
    data.validateAndMigrate();
    assertThat(data.lastUseDate()).isEqualTo(LocalDate.now());
  }

  @Test
  void validate_should_reset_numDays_if_lastUseDate_unset() {
    var data = new TelemetryLocalStorage();
    data.setNumUseDays(3);

    data.validateAndMigrate();
    assertThat(data.lastUseDate()).isNull();
    assertThat(data.numUseDays()).isZero();
  }

  @Test
  void validate_should_fix_numDays_if_incorrect() {
    var data = new TelemetryLocalStorage();
    var installTime = OffsetDateTime.now().minusDays(10);
    var lastUseDate = installTime.plusDays(3).toLocalDate();
    data.setInstallTime(installTime);
    data.setLastUseDate(lastUseDate);

    var numUseDays = installTime.toLocalDate().until(lastUseDate, ChronoUnit.DAYS) + 1;
    data.setNumUseDays(numUseDays * 2);

    data.validateAndMigrate();
    assertThat(data.numUseDays()).isEqualTo(numUseDays);
    assertThat(data.installTime()).isEqualTo(installTime);
    assertThat(data.lastUseDate()).isEqualTo(lastUseDate);
  }

  @Test
  void should_replace_fix_suggestion_snippet_status() {
    var data = new TelemetryLocalStorage();

    var suggestionId = UUID.randomUUID().toString();
    data.fixSuggestionResolved(suggestionId, FixSuggestionStatus.ACCEPTED, 0);

    assertThat(data.getFixSuggestionResolved().get(suggestionId)).extracting(TelemetryFixSuggestionResolvedStatus::getFixSuggestionResolvedStatus, TelemetryFixSuggestionResolvedStatus::getFixSuggestionResolvedSnippetIndex)
      .containsExactly(tuple(FixSuggestionStatus.ACCEPTED, 0));

    data.fixSuggestionResolved(suggestionId, FixSuggestionStatus.DECLINED, 0);
    assertThat(data.getFixSuggestionResolved().get(suggestionId)).extracting(TelemetryFixSuggestionResolvedStatus::getFixSuggestionResolvedStatus, TelemetryFixSuggestionResolvedStatus::getFixSuggestionResolvedSnippetIndex)
      .containsExactly(tuple(FixSuggestionStatus.DECLINED, 0));
  }

  @Test
  void should_track_findings_filtered_by_type() {
    var data = new TelemetryLocalStorage();
    assertThat(data.getFindingsFilteredCountersByType()).isEmpty();

    data.findingsFiltered("severity");

    data.findingsFiltered("severity");

    data.findingsFiltered("location");
    
    data.findingsFiltered("fix_availability");
    assertThat(data.getFindingsFilteredCountersByType()).hasSize(3);
    assertThat(data.getFindingsFilteredCountersByType().get("location").getFindingsFilteredCount()).isEqualTo(1);
    assertThat(data.getFindingsFilteredCountersByType().get("severity").getFindingsFilteredCount()).isEqualTo(2);
    assertThat(data.getFindingsFilteredCountersByType().get("fix_availability").getFindingsFilteredCount()).isEqualTo(1);
  }

  @Test
  void should_clear_findings_filtered_counters() {
    var data = new TelemetryLocalStorage();
    
    data.findingsFiltered("severity");
    data.findingsFiltered("location");
    assertThat(data.getFindingsFilteredCountersByType()).hasSize(2);

    data.clearAfterPing();
    assertThat(data.getFindingsFilteredCountersByType()).isEmpty();
  }

  @Test
  void should_track_dependency_risk_investigated() {
    var data = new TelemetryLocalStorage();
    assertThat(data.getDependencyRiskInvestigatedRemotelyCount()).isZero();
    assertThat(data.getDependencyRiskInvestigatedLocallyCount()).isZero();

    data.incrementDependencyRiskInvestigatedRemotelyCount();
    data.incrementDependencyRiskInvestigatedLocallyCount();
    assertThat(data.getDependencyRiskInvestigatedRemotelyCount()).isEqualTo(1);
    assertThat(data.getDependencyRiskInvestigatedLocallyCount()).isEqualTo(1);

    data.incrementDependencyRiskInvestigatedRemotelyCount();
    data.incrementDependencyRiskInvestigatedLocallyCount();
    assertThat(data.getDependencyRiskInvestigatedRemotelyCount()).isEqualTo(2);
    assertThat(data.getDependencyRiskInvestigatedLocallyCount()).isEqualTo(2);
  }

  @Test
  void should_clear_dependency_risk_investigated_counts_after_ping() {
    var data = new TelemetryLocalStorage();

    data.incrementDependencyRiskInvestigatedRemotelyCount();
    data.incrementDependencyRiskInvestigatedLocallyCount();
    assertThat(data.getDependencyRiskInvestigatedRemotelyCount()).isEqualTo(1);
    assertThat(data.getDependencyRiskInvestigatedLocallyCount()).isEqualTo(1);

    data.clearAfterPing();
    assertThat(data.getDependencyRiskInvestigatedRemotelyCount()).isZero();
    assertThat(data.getDependencyRiskInvestigatedLocallyCount()).isZero();
  }

  @Test
  void should_increment_new_bindings_counters_per_origin() {
    var data = new TelemetryLocalStorage();

    assertThat(data.getNewBindingsPropertiesFileCount()).isZero();
    assertThat(data.getNewBindingsRemoteUrlCount()).isZero();
    assertThat(data.getNewBindingsProjectNameCount()).isZero();
    assertThat(data.getNewBindingsSharedConfigurationCount()).isZero();

    data.incrementNewBindingsPropertiesFileCount();
    data.incrementNewBindingsRemoteUrlCount();
    data.incrementNewBindingsProjectNameCount();
    data.incrementNewBindingsSharedConfigurationCount();

    assertThat(data.getNewBindingsPropertiesFileCount()).isEqualTo(1);
    assertThat(data.getNewBindingsRemoteUrlCount()).isEqualTo(1);
    assertThat(data.getNewBindingsProjectNameCount()).isEqualTo(1);
    assertThat(data.getNewBindingsSharedConfigurationCount()).isEqualTo(1);
  }

  @Test
  void should_reset_new_bindings_counters_on_clear_after_ping() {
    var data = new TelemetryLocalStorage();
    data.incrementNewBindingsPropertiesFileCount();
    data.incrementNewBindingsRemoteUrlCount();
    data.incrementNewBindingsProjectNameCount();
    data.incrementNewBindingsSharedConfigurationCount();

    data.clearAfterPing();

    assertThat(data.getNewBindingsPropertiesFileCount()).isZero();
    assertThat(data.getNewBindingsRemoteUrlCount()).isZero();
    assertThat(data.getNewBindingsProjectNameCount()).isZero();
    assertThat(data.getNewBindingsSharedConfigurationCount()).isZero();
  }

  @Test
  void should_increment_suggested_remote_bindings_count() {
    var data = new TelemetryLocalStorage();
    assertThat(data.getSuggestedRemoteBindingsCount()).isZero();
    data.incrementSuggestedRemoteBindingsCount();
    data.incrementSuggestedRemoteBindingsCount();
    assertThat(data.getSuggestedRemoteBindingsCount()).isEqualTo(2);
  }

  @Test
  void should_increment_flight_recorder_sessions_count() {
    var data = new TelemetryLocalStorage();
    assertThat(data.getFlightRecorderSessionsCount()).isZero();
    data.incrementFlightRecorderSessionsCount();
    data.incrementFlightRecorderSessionsCount();
    data.incrementFlightRecorderSessionsCount();
    assertThat(data.getFlightRecorderSessionsCount()).isEqualTo(3);
  }

  @Test
  void should_increment_mcp_server_settings_requested_count() {
    var data = new TelemetryLocalStorage();
    assertThat(data.getMcpServerConfigurationRequestedCount()).isZero();
    data.incrementMcpServerConfigurationRequestedCount();
    data.incrementMcpServerConfigurationRequestedCount();
    assertThat(data.getMcpServerConfigurationRequestedCount()).isEqualTo(2);
  }

  @Test
  void should_find_mcp_integration_enabled() {
    var data = new TelemetryLocalStorage();
    assertThat(data.isMcpIntegrationEnabled()).isFalse();
    data.setMcpIntegrationEnabled(true);
    assertThat(data.isMcpIntegrationEnabled()).isTrue();
  }

  @Test
  void should_find_mcp_transport_mode_used() {
    var data = new TelemetryLocalStorage();
    assertThat(data.getMcpTransportModeUsed()).isNull();
    data.setMcpTransportModeUsed(McpTransportMode.HTTP);
    assertThat(data.getMcpTransportModeUsed()).isEqualTo(McpTransportMode.HTTP);
  }
}
