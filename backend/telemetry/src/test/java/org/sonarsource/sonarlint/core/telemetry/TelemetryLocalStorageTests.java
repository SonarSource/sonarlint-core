/*
 * SonarLint Core - Telemetry
 * Copyright (C) 2016-2023 SonarSource SA
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
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorage.isOlder;
import static org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorage.validateAndMigrate;

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

    assertThat(validateAndMigrate(data).installTime()).is(within3SecOfNow);

    data.setInstallTime(now.plusDays(1));
    assertThat(validateAndMigrate(data).installTime()).is(within3SecOfNow);
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
    assertThat(validateAndMigrate(data).lastUseDate()).isEqualTo(today);
  }

  @Test
  void should_migrate_installDate() {
    var data = new TelemetryLocalStorage();
    data.setInstallDate(LocalDate.now().minusDays(5));
    assertThat(TelemetryLocalStorage.validateAndMigrate(data).installTime()).is(about5DaysAgo);
  }

  @Test
  void validate_should_reset_lastUseDate_if_before_installTime() {
    var data = new TelemetryLocalStorage();
    var now = OffsetDateTime.now();

    data.setInstallTime(now);
    data.setLastUseDate(now.minusDays(1).toLocalDate());
    assertThat(validateAndMigrate(data).lastUseDate()).isEqualTo(LocalDate.now());
  }

  @Test
  void validate_should_reset_numDays_if_lastUseDate_unset() {
    var data = new TelemetryLocalStorage();
    data.setNumUseDays(3);

    var valid = validateAndMigrate(data);
    assertThat(valid.lastUseDate()).isNull();
    assertThat(valid.numUseDays()).isZero();
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

    var valid = validateAndMigrate(data);
    assertThat(valid.numUseDays()).isEqualTo(numUseDays);
    assertThat(valid.installTime()).isEqualTo(installTime);
    assertThat(valid.lastUseDate()).isEqualTo(lastUseDate);
  }
}
