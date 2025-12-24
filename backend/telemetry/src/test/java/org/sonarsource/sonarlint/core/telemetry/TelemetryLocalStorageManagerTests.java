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

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryMigrationDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelemetryLocalStorageManagerTests {

  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private final LocalDate today = LocalDate.now();
  private Path filePath;

  @BeforeEach
  void setUp(@TempDir Path temp) {
    filePath = temp.resolve("usage");
  }

  @Test
  void test_default_data() {
    var storage = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));

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
    var storage = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));

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
    var storage = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));

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
    var storage = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));

    var tenDaysAgo = OffsetDateTime.now().minusDays(10);

    storage.tryUpdateAtomically(data -> {
      data.setInstallTime(tenDaysAgo);
      data.setLastUseDate(today);
      data.setNumUseDays(100);
    });

    var data2 = storage.tryRead();
    assertThat(data2.installTime()).isEqualTo(tenDaysAgo);
    assertThat(data2.lastUseDate()).isEqualTo(today);
    assertThat(data2.numUseDays()).isEqualTo(11);
  }

  @Test
  void should_fix_dates_in_future() {
    var storage = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));

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
  void should_not_crash_when_cannot_read_storage(@TempDir Path temp) {
    assertThatCode(() -> new TelemetryLocalStorageManager(temp, mock(InitializeParams.class)).tryRead())
      .doesNotThrowAnyException();

  }

  @Test
  void should_not_crash_when_cannot_write_storage(@TempDir Path temp) {
    assertThatCode(() -> new TelemetryLocalStorageManager(temp, mock(InitializeParams.class)).tryUpdateAtomically(d -> {}))
      .doesNotThrowAnyException();
  }

  @Test
  void should_increment_open_hotspot_in_browser() {
    var storage = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));

    storage.tryUpdateAtomically(TelemetryLocalStorage::incrementOpenHotspotInBrowserCount);
    storage.tryUpdateAtomically(TelemetryLocalStorage::incrementOpenHotspotInBrowserCount);

    var data2 = storage.tryRead();
    assertThat(data2.openHotspotInBrowserCount()).isEqualTo(2);
  }

  @Test
  void should_increment_hotspot_status_changed() {
    var storage = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));

    storage.tryUpdateAtomically(TelemetryLocalStorage::incrementHotspotStatusChangedCount);
    storage.tryUpdateAtomically(TelemetryLocalStorage::incrementHotspotStatusChangedCount);
    storage.tryUpdateAtomically(TelemetryLocalStorage::incrementHotspotStatusChangedCount);

    var data = storage.tryRead();
    assertThat(data.hotspotStatusChangedCount()).isEqualTo(3);
  }

  @Test
  void should_increment_issue_status_changed() {
    var storage = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));

    storage.tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.addIssueStatusChanged("ruleKey1"));
    storage.tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.addIssueStatusChanged("ruleKey2"));

    var data = storage.tryRead();
    assertThat(data.issueStatusChangedCount()).isEqualTo(2);
    assertThat(data.issueStatusChangedRuleKeys()).containsExactlyInAnyOrder("ruleKey1", "ruleKey2");
  }

  @Test
  void should_increment_issue_ai_fixable() {
    var storage = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));
    var uuid1 = UUID.randomUUID();
    var uuid2 = UUID.randomUUID();
    var uuid3 = UUID.randomUUID();
    storage.tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.addIssuesWithPossibleAiFixFromIde(Set.of(uuid1, uuid2)));
    storage.tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.addIssuesWithPossibleAiFixFromIde(Set.of(uuid1, uuid3)));

    var data = storage.tryRead();
    assertThat(data.getCountIssuesWithPossibleAiFixFromIde()).isEqualTo(3);
  }

  @Test
  void should_migrate_telemetry() {
    var initializeParams = mock(InitializeParams.class);
    var expectedInstallTime = OffsetDateTime.now();
    when(initializeParams.getTelemetryMigration()).thenReturn(new TelemetryMigrationDto(expectedInstallTime, 42, false));

    var storageManager = new TelemetryLocalStorageManager(filePath, initializeParams);

    var localStorage = storageManager.tryRead();
    var actualInstallTime = localStorage.installTime();
    var numUseDays = localStorage.numUseDays();
    var enabled = localStorage.enabled();

    assertThat(enabled).isFalse();
    assertThat(numUseDays).isEqualTo(42);
    assertThat(actualInstallTime).isEqualTo(expectedInstallTime);
  }
}
