/*
 * SonarLint Telemetry
 * Copyright (C) 2016-2022 SonarSource SA
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

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryLocalStorageManagerTests {

  private final LocalDate today = LocalDate.now();
  private Path filePath;

  @BeforeEach
  void setUp(@TempDir Path temp) throws IOException {
    filePath = temp.resolve("usage");
  }

  @Test
  void test_default_data() {
    var storage = new TelemetryLocalStorageManager(filePath);

    var data = storage.tryRead();
    assertThat(filePath).doesNotExist();

    assertThat(data.installTime()).is(within3SecOfNow);
    assertThat(data.lastUseDate()).isNull();
    assertThat(data.numUseDays()).isZero();
    assertThat(data.enabled()).isTrue();
  }

  private final Condition<OffsetDateTime> within3SecOfNow = new Condition<>(p -> {
    var now = OffsetDateTime.now();
    return Math.abs(p.until(now, ChronoUnit.SECONDS)) < 3;
  }, "within3Sec");

  @Test
  void should_update_data() {
    var storage = new TelemetryLocalStorageManager(filePath);

    storage.tryRead();
    assertThat(filePath).doesNotExist();

    storage.tryUpdateAtomically(TelemetryLocalStorage::setUsedAnalysis);
    assertThat(filePath).exists();

    var data2 = storage.tryRead();

    assertThat(data2.lastUseDate()).isEqualTo(today);
    assertThat(data2.numUseDays()).isEqualTo(1);
  }

  @Test
  void should_fix_invalid_installTime() {
    var storage = new TelemetryLocalStorageManager(filePath);

    storage.tryUpdateAtomically(data -> {
      data.setInstallTime(null);
      data.setNumUseDays(100);
    });

    var data2 = storage.tryRead();
    assertThat(data2.installTime()).is(within3SecOfNow);
    assertThat(data2.lastUseDate()).isNull();
    assertThat(data2.numUseDays()).isZero();
  }

  @Test
  void should_fix_invalid_numDays() {
    var storage = new TelemetryLocalStorageManager(filePath);

    var tenDaysAgo = OffsetDateTime.now().minusDays(10);

    storage.tryUpdateAtomically(data -> {
      data.setInstallTime(tenDaysAgo);
      data.setLastUseDate(today);
      data.setNumUseDays(100);
    });

    var data2 = storage.tryRead();
    // Truncate because nano precision is lost during JSON serialization
    assertThat(data2.installTime()).isEqualTo(tenDaysAgo.truncatedTo(ChronoUnit.MILLIS));
    assertThat(data2.lastUseDate()).isEqualTo(today);
    assertThat(data2.numUseDays()).isEqualTo(11);
  }

  @Test
  void should_fix_dates_in_future() {
    var storage = new TelemetryLocalStorageManager(filePath);

    storage.tryUpdateAtomically(data -> {
      data.setInstallTime(OffsetDateTime.now().plusDays(5));
      data.setLastUseDate(today.plusDays(7));
      data.setNumUseDays(100);
    });

    var data2 = storage.tryRead();
    assertThat(data2.installTime()).is(within3SecOfNow);
    assertThat(data2.lastUseDate()).isEqualTo(today);
    assertThat(data2.numUseDays()).isEqualTo(1);
  }

  @Test
  void should_not_crash_when_cannot_read_storage(@TempDir Path temp) throws IOException {
    new TelemetryLocalStorageManager(temp).tryRead();
  }

  @Test
  void should_not_crash_when_cannot_write_storage(@TempDir Path temp) throws IOException {
    new TelemetryLocalStorageManager(temp).tryUpdateAtomically(d -> {
    });
  }
}
