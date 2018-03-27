/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.sonarsource.sonarlint.core.telemetry.TelemetryData.isOlder;
import static org.sonarsource.sonarlint.core.telemetry.TelemetryData.validateAndMigrate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import org.assertj.core.api.Condition;
import org.junit.Test;

public class TelemetryDataTest {
  @Test
  public void usedAnalysis_should_increment_num_days_on_first_run() {
    TelemetryData data = new TelemetryData();
    assertThat(data.numUseDays()).isEqualTo(0);

    data.setUsedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);
  }

  @Test
  public void usedAnalysis_should_not_increment_num_days_on_same_day() {
    TelemetryData data = new TelemetryData();
    assertThat(data.numUseDays()).isEqualTo(0);

    data.setUsedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);

    data.setUsedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);
  }

  @Test
  public void usedAnalysis_with_duration_should_register_analyzer_performance() {
    TelemetryData data = new TelemetryData();
    assertThat(data.numUseDays()).isEqualTo(0);
    assertThat(data.analyzers()).hasSize(0);

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
  public void usedAnalysis_should_increment_num_days_when_day_changed() {
    TelemetryData data = new TelemetryData();
    assertThat(data.numUseDays()).isEqualTo(0);

    data.setUsedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);

    data.setUsedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);

    data.setLastUseDate(LocalDate.now().minusDays(1));
    data.setUsedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(2);
  }

  @Test
  public void mergeFrom_should_overwrite_usedConnectedMode_if_set() {
    TelemetryData data1 = new TelemetryData();
    TelemetryData data2 = new TelemetryData();

    data1.setUsedConnectedMode(true);
    data1.mergeFrom(data2);
    assertThat(data1.usedConnectedMode()).isTrue();

    data1.setUsedConnectedMode(false);
    data2.setUsedConnectedMode(true);
    data1.mergeFrom(data2);
    assertThat(data1.usedConnectedMode()).isTrue();
  }

  @Test
  public void test_isOlder_LocalDate() {
    LocalDate date = LocalDate.now();

    assertThat(isOlder((LocalDate) null, null)).isTrue();
    assertThat(isOlder(null, date)).isTrue();
    assertThat(isOlder(date, null)).isFalse();
    assertThat(isOlder(date, date)).isFalse();
    assertThat(isOlder(date, date.plusDays(1))).isTrue();
  }

  @Test
  public void test_isOlder_LocalDateTime() {
    LocalDateTime date = LocalDateTime.now();

    assertThat(isOlder((LocalDateTime) null, null)).isTrue();
    assertThat(isOlder(null, date)).isTrue();
    assertThat(isOlder(date, null)).isFalse();
    assertThat(isOlder(date, date)).isFalse();
    assertThat(isOlder(date, date.plusDays(1))).isTrue();
  }

  @Test
  public void mergeFrom_should_overwrite_lastUseDate_if_newer() {
    TelemetryData data1 = new TelemetryData();
    TelemetryData data2 = new TelemetryData();

    LocalDate date = LocalDate.now();

    data1.setLastUseDate(date);
    data1.mergeFrom(data2);
    assertThat(data1.lastUseDate()).isEqualTo(date);

    data1.setLastUseDate(null);
    data2.setLastUseDate(date);
    data1.mergeFrom(data2);
    assertThat(data1.lastUseDate()).isEqualTo(date);

    data1.setLastUseDate(date.minusDays(1));
    data2.setLastUseDate(date);
    data1.mergeFrom(data2);
    assertThat(data1.lastUseDate()).isEqualTo(date);
  }

  @Test
  public void mergeFrom_should_overwrite_lastUploadTime_if_newer() {
    TelemetryData data1 = new TelemetryData();
    TelemetryData data2 = new TelemetryData();

    LocalDateTime date = LocalDateTime.now();

    data1.setLastUploadTime(date);
    data1.mergeFrom(data2);
    assertThat(data1.lastUploadTime()).isEqualTo(date);

    data1.setLastUploadTime(null);
    data2.setLastUploadTime(date);
    data1.mergeFrom(data2);
    assertThat(data1.lastUploadTime()).isEqualTo(date);

    data1.setLastUploadTime(date.minusDays(1));
    data2.setLastUploadTime(date);
    data1.mergeFrom(data2);
    assertThat(data1.lastUploadTime()).isEqualTo(date);
  }

  @Test
  public void mergeFrom_should_overwrite_numUseDays_if_greater() {
    TelemetryData data1 = new TelemetryData();
    TelemetryData data2 = new TelemetryData();

    long numUseDays = 3;
    OffsetDateTime now = OffsetDateTime.now();
    data2.setNumUseDays(numUseDays);
    data2.setInstallTime(now);

    data1.mergeFrom(data2);
    assertThat(data1.numUseDays()).isEqualTo(numUseDays);
    assertThat(data1.installTime()).isEqualTo(now);

    data1.setNumUseDays(numUseDays - 1);
    data1.setInstallTime(now.minusDays(1));
    data1.mergeFrom(data2);
    assertThat(data1.numUseDays()).isEqualTo(numUseDays);
    assertThat(data1.installTime()).isEqualTo(now);

    data1.setNumUseDays(numUseDays);
    data1.setInstallTime(now.plusDays(1));
    data1.mergeFrom(data2);
    assertThat(data1.numUseDays()).isEqualTo(numUseDays);
    assertThat(data1.installTime()).isAfter(now);
  }

  @Test
  public void validate_should_reset_installTime_if_in_future() {
    TelemetryData data = new TelemetryData();
    OffsetDateTime now = OffsetDateTime.now();

    assertThat(validateAndMigrate(data).installTime()).is(within3SecOfNow);

    data.setInstallTime(now.plusDays(1));
    assertThat(validateAndMigrate(data).installTime()).is(within3SecOfNow);
  }

  private Condition<OffsetDateTime> within3SecOfNow = new Condition<>(p -> {
    OffsetDateTime now = OffsetDateTime.now();
    return Math.abs(p.until(now, ChronoUnit.SECONDS)) < 3;
  }, "within3Sec");

  private Condition<OffsetDateTime> about5DaysAgo = new Condition<>(p -> {
    OffsetDateTime fiveDaysAgo = OffsetDateTime.now().minusDays(5);
    return Math.abs(p.until(fiveDaysAgo, ChronoUnit.SECONDS)) < 3;
  }, "about5DaysAgo");

  @Test
  public void validate_should_reset_lastUseDate_if_in_future() {
    TelemetryData data = new TelemetryData();
    LocalDate today = LocalDate.now();

    data.setLastUseDate(today.plusDays(1));
    assertThat(validateAndMigrate(data).lastUseDate()).isEqualTo(today);
  }

  @Test
  public void should_migrate_installDate() {
    TelemetryData data = new TelemetryData();
    data.setInstallDate(LocalDate.now().minusDays(5));
    assertThat(data.validateAndMigrate(data).installTime()).is(about5DaysAgo);
  }

  @Test
  public void validate_should_reset_lastUseDate_if_before_installTime() {
    TelemetryData data = new TelemetryData();
    OffsetDateTime now = OffsetDateTime.now();

    data.setInstallTime(now);
    data.setLastUseDate(now.minusDays(1).toLocalDate());
    assertThat(validateAndMigrate(data).lastUseDate()).isEqualTo(LocalDate.now());
  }

  @Test
  public void validate_should_reset_numDays_if_lastUseDate_unset() {
    TelemetryData data = new TelemetryData();
    data.setNumUseDays(3);

    TelemetryData valid = validateAndMigrate(data);
    assertThat(valid.lastUseDate()).isNull();
    assertThat(valid.numUseDays()).isEqualTo(0);
  }

  @Test
  public void validate_should_fix_numDays_if_incorrect() {
    TelemetryData data = new TelemetryData();
    OffsetDateTime installTime = OffsetDateTime.now().minusDays(10);
    LocalDate lastUseDate = installTime.plusDays(3).toLocalDate();
    data.setInstallTime(installTime);
    data.setLastUseDate(lastUseDate);

    long numUseDays = installTime.toLocalDate().until(lastUseDate, ChronoUnit.DAYS) + 1;
    data.setNumUseDays(numUseDays * 2);

    TelemetryData valid = validateAndMigrate(data);
    assertThat(valid.numUseDays()).isEqualTo(numUseDays);
    assertThat(valid.installTime()).isEqualTo(installTime);
    assertThat(valid.lastUseDate()).isEqualTo(lastUseDate);
  }
}
