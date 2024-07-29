/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiSuggestionSource;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.sonarsource.sonarlint.core.telemetry.TelemetryUtils.isGracePeriodElapsedAndDayChanged;

class TelemetryUtilsTests {
  @Test
  void dayChanged_should_return_true_for_null() {
    assertThat(isGracePeriodElapsedAndDayChanged(null)).isTrue();
  }

  @Test
  void dayChanged_should_return_true_if_older() {
    assertThat(isGracePeriodElapsedAndDayChanged(LocalDate.now().minusDays(1))).isTrue();
  }

  @Test
  void should_create_telemetry_performance_payload() {
    Map<String, TelemetryAnalyzerPerformance> analyzers = new HashMap<>();
    var perf = new TelemetryAnalyzerPerformance();
    perf.registerAnalysis(10);
    perf.registerAnalysis(500);
    perf.registerAnalysis(500);

    analyzers.put("java", perf);
    var payload = TelemetryUtils.toPayload(analyzers);
    assertThat(payload).hasSize(1);
    assertThat(payload[0].language()).isEqualTo("java");
    assertThat(payload[0].distribution()).containsOnly(
      entry("0-300", new BigDecimal("33.33")),
      entry("300-500", new BigDecimal("0.00")),
      entry("500-1000", new BigDecimal("66.67")),
      entry("1000-2000", new BigDecimal("0.00")),
      entry("2000-4000", new BigDecimal("0.00")),
      entry("4000+", new BigDecimal("0.00")));
  }

  @Test
  void dayChanged_should_return_false_if_same() {
    assertThat(isGracePeriodElapsedAndDayChanged(LocalDate.now())).isFalse();
  }

  @Test
  void dayChanged_with_hours_should_return_true_for_null() {
    assertThat(TelemetryUtils.isGracePeriodElapsedAndDayChanged(null, 1)).isTrue();
  }

  @Test
  void dayChanged_with_hours_should_return_false_if_day_same() {
    assertThat(TelemetryUtils.isGracePeriodElapsedAndDayChanged(LocalDateTime.now(), 100)).isFalse();
  }

  @Test
  void create_analyzer_performance_payload() {
    var perf = new TelemetryAnalyzerPerformance();
    for (var i = 0; i < 10; i++) {
      perf.registerAnalysis(1000);
    }
    for (var i = 0; i < 20; i++) {
      perf.registerAnalysis(2000);
    }
    for (var i = 0; i < 20; i++) {
      perf.registerAnalysis(200);
    }
    assertThat(perf.analysisCount()).isEqualTo(50);
    var payload = TelemetryUtils.toPayload(Collections.singletonMap("java", perf));
    assertThat(payload).hasSize(1);
    assertThat(payload[0].language()).isEqualTo("java");
    assertThat(payload[0].distribution()).containsExactly(
      entry("0-300", new BigDecimal("40.00")),
      entry("300-500", new BigDecimal("0.00")),
      entry("500-1000", new BigDecimal("0.00")),
      entry("1000-2000", new BigDecimal("20.00")),
      entry("2000-4000", new BigDecimal("40.00")),
      entry("4000+", new BigDecimal("0.00")));

  }

  @Test
  void dayChanged_with_hours_should_return_false_if_different_day_but_within_hours() {
    var date = LocalDateTime.now().minusDays(1);
    var hours = date.until(LocalDateTime.now(), ChronoUnit.HOURS);
    assertThat(TelemetryUtils.isGracePeriodElapsedAndDayChanged(date, hours + 1)).isFalse();
  }

  @Test
  void dayChanged_with_hours_should_return_true_if_different_day_and_beyond_hours() {
    var date = LocalDateTime.now().minusDays(1);
    var hours = date.until(LocalDateTime.now(), ChronoUnit.HOURS);
    assertThat(TelemetryUtils.isGracePeriodElapsedAndDayChanged(date, hours)).isTrue();
  }

  @Test
  void should_create_telemetry_fixSuggestions_payload() {
    var suggestionId1 = UUID.randomUUID().toString();
    var counter1 = new TelemetryFixSuggestionReceivedCounter(AiSuggestionSource.SONARCLOUD);
    counter1.incrementFixSuggestionReceivedCount();
    counter1.incrementFixSuggestionReceivedCount();

    var suggestionId2 = UUID.randomUUID().toString();
    var counter2 = new TelemetryFixSuggestionReceivedCounter(AiSuggestionSource.SONARCLOUD);
    counter2.incrementFixSuggestionReceivedCount();

    var fixSuggestionReceivedCounter = Map.of(
      suggestionId1, counter1,
      suggestionId2, counter2
    );

    var fixSuggestionResolvedStatus1 = new TelemetryFixSuggestionResolvedStatus(FixSuggestionStatus.ACCEPTED, 0);
    var fixSuggestionResolvedStatus2 = new TelemetryFixSuggestionResolvedStatus(FixSuggestionStatus.ACCEPTED, 1);
    var fixSuggestionResolved = Map.of(suggestionId1, List.of(fixSuggestionResolvedStatus1, fixSuggestionResolvedStatus2));

    var result = TelemetryUtils.toFixSuggestionResolvedPayload(fixSuggestionReceivedCounter, fixSuggestionResolved);

    assertThat(result).hasSize(2);
    assertThat(result[0].getSuggestionId()).isEqualTo(suggestionId1);
    assertThat(result[0].getAiSuggestionOpenedFrom()).isEqualTo(AiSuggestionSource.SONARCLOUD);
    assertThat(result[0].getOpenedSuggestionsCount()).isEqualTo(2);
    assertThat(result[0].getSnippets()).hasSize(2);

    assertThat(result[1].getSuggestionId()).isEqualTo(suggestionId2);
    assertThat(result[1].getAiSuggestionOpenedFrom()).isEqualTo(AiSuggestionSource.SONARCLOUD);
    assertThat(result[1].getOpenedSuggestionsCount()).isEqualTo(1);
    assertThat(result[1].getSnippets()).isEmpty();
  }
}
