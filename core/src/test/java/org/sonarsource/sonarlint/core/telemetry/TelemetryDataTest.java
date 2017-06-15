/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
import java.time.temporal.ChronoUnit;
import org.junit.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.sonarsource.sonarlint.core.telemetry.TelemetryData.isOlder;
import static org.sonarsource.sonarlint.core.telemetry.TelemetryData.validate;

public class TelemetryDataTest {
  @Test
  public void usedAnalysis_should_increment_num_days_on_first_run() {
    TelemetryData data = new TelemetryData();
    assertThat(data.numUseDays()).isEqualTo(0);

    data.usedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);
  }

  @Test
  public void usedAnalysis_should_not_increment_num_days_on_same_day() {
    TelemetryData data = new TelemetryData();
    assertThat(data.numUseDays()).isEqualTo(0);

    data.usedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);

    data.usedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);
  }

  @Test
  public void usedAnalysis_should_increment_num_days_when_day_changed() {
    TelemetryData data = new TelemetryData();
    assertThat(data.numUseDays()).isEqualTo(0);

    data.usedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);

    data.usedAnalysis();
    assertThat(data.numUseDays()).isEqualTo(1);

    data.setLastUseDate(LocalDate.now().minusDays(1));
    data.usedAnalysis();
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
    LocalDate date = LocalDate.now();
    data2.setNumUseDays(numUseDays);
    data2.setInstallDate(date);

    data1.mergeFrom(data2);
    assertThat(data1.numUseDays()).isEqualTo(numUseDays);
    assertThat(data1.installDate()).isEqualTo(date);

    data1.setNumUseDays(numUseDays - 1);
    data1.setInstallDate(date.minusDays(1));
    data1.mergeFrom(data2);
    assertThat(data1.numUseDays()).isEqualTo(numUseDays);
    assertThat(data1.installDate()).isEqualTo(date);

    data1.setNumUseDays(numUseDays);
    data1.setInstallDate(date.plusDays(1));
    data1.mergeFrom(data2);
    assertThat(data1.numUseDays()).isEqualTo(numUseDays);
    assertThat(data1.installDate()).isAfter(date);
  }

  @Test
  public void validate_should_reset_installDate_if_in_future() {
    TelemetryData data = new TelemetryData();
    LocalDate today = LocalDate.now();

    assertThat(validate(data).installDate()).isEqualTo(today);

    data.setInstallDate(today.plusDays(1));
    assertThat(validate(data).installDate()).isEqualTo(today);
  }

  @Test
  public void validate_should_reset_lastUseDate_if_in_future() {
    TelemetryData data = new TelemetryData();
    LocalDate today = LocalDate.now();

    data.setLastUseDate(today.plusDays(1));
    assertThat(validate(data).lastUseDate()).isEqualTo(today);
  }

  @Test
  public void validate_should_reset_lastUseDate_if_before_installDate() {
    TelemetryData data = new TelemetryData();
    LocalDate today = LocalDate.now();

    data.setInstallDate(today);
    data.setLastUseDate(today.minusDays(1));
    assertThat(validate(data).lastUseDate()).isEqualTo(today);
  }

  @Test
  public void validate_should_reset_numDays_if_lastUseDate_unset() {
    TelemetryData data = new TelemetryData();
    data.setNumUseDays(3);

    TelemetryData valid = validate(data);
    assertThat(valid.lastUseDate()).isNull();
    assertThat(valid.numUseDays()).isEqualTo(0);
  }

  @Test
  public void validate_should_fix_numDays_if_incorrect() {
    TelemetryData data = new TelemetryData();
    LocalDate installDate = LocalDate.now().minusDays(10);
    LocalDate lastUseDate = installDate.plusDays(3);
    data.setInstallDate(installDate);
    data.setLastUseDate(lastUseDate);
    long numUseDays = installDate.until(lastUseDate, ChronoUnit.DAYS) + 1;
    data.setNumUseDays(numUseDays * 2);

    TelemetryData valid = validate(data);
    assertThat(valid.numUseDays()).isEqualTo(numUseDays);
    assertThat(valid.installDate()).isEqualTo(installDate);
    assertThat(valid.lastUseDate()).isEqualTo(lastUseDate);
  }
}
