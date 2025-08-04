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
package org.sonarsource.sonarlint.core.telemetry;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryMigrationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ReportIssuesAsOverrideLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ReportIssuesAsErrorLevel;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelemetryLocalStorageManagerTests {

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
    InternalDebug.setEnabled(false);
    assertThatCode(() -> new TelemetryLocalStorageManager(temp, mock(InitializeParams.class)).tryRead())
      .doesNotThrowAnyException();

  }

  @Test
  void should_not_crash_when_cannot_write_storage(@TempDir Path temp) {
    InternalDebug.setEnabled(false);
    assertThatCode(() -> new TelemetryLocalStorageManager(temp, mock(InitializeParams.class)).tryUpdateAtomically(d -> {}))
      .doesNotThrowAnyException();
  }

  @Test
  void supportConcurrentUpdates() {
    var storage = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));
    // Put some data to avoid migration
    storage.tryUpdateAtomically(data -> {
      data.setInstallTime(OffsetDateTime.now().minusDays(50));
      data.setLastUseDate(today);
      data.setNumUseDays(0);
    });
    int nThreads = 10;
    var executorService = Executors.newFixedThreadPool(nThreads);
    CountDownLatch latch = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    // Each thread will attempt to increment the numUseDays by one
    IntStream.range(0, nThreads).forEach(i -> {
      futures.add(executorService.submit(() -> {
        try {
          latch.await();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        storage.tryUpdateAtomically(data -> {
          data.setNumUseDays(data.numUseDays() + 1);
        });
      }));
    });
    latch.countDown();
    futures.forEach(f -> {
      try {
        f.get();
      } catch (ExecutionException e) {
        fail(e.getCause());
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    assertThat(storage.tryRead().numUseDays()).isEqualTo(nThreads);
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
  void should_increment_reported_issues_as_override() {
    var storage = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));
    storage.tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.reportIssuesAsOverride(ReportIssuesAsOverrideLevel.ERROR, "java:S123"));
    storage.tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.reportIssuesAsOverride(ReportIssuesAsOverrideLevel.ERROR, "java:S123"));
    storage.tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.reportIssuesAsOverride(ReportIssuesAsOverrideLevel.WARNING, "php:S123"));
    storage.tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.reportIssuesAsOverride(ReportIssuesAsOverrideLevel.WARNING, "javascript:S123"));

    var data = storage.tryRead();
    assertThat(data.getReportedIssuesAsOverridePerLevel().get(ReportIssuesAsOverrideLevel.ERROR)).hasSize(1);
    assertThat(data.getReportedIssuesAsOverridePerLevel().get(ReportIssuesAsOverrideLevel.ERROR).get(0).getRuleKey()).isEqualTo("java:S123");
    assertThat(data.getReportedIssuesAsOverridePerLevel().get(ReportIssuesAsOverrideLevel.ERROR).get(0).getCount()).isEqualTo(2);
    assertThat(data.getReportedIssuesAsOverridePerLevel().get(ReportIssuesAsOverrideLevel.WARNING)).hasSize(2);
    assertThat(data.getReportedIssuesAsOverridePerLevel().get(ReportIssuesAsOverrideLevel.WARNING).get(0).getCount()).isEqualTo(1);
    assertThat(data.getReportedIssuesAsOverridePerLevel().get(ReportIssuesAsOverrideLevel.WARNING).get(1).getCount()).isEqualTo(1);
  }

  @Test
  void should_increment_reported_issues_as_error_level() {
    var storage = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));
    storage.tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.reportIssuesAsErrorLevel(ReportIssuesAsErrorLevel.NONE));
    storage.tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.reportIssuesAsErrorLevel(ReportIssuesAsErrorLevel.MEDIUM_AND_ABOVE));
    storage.tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.reportIssuesAsErrorLevel(ReportIssuesAsErrorLevel.MEDIUM_AND_ABOVE));

    var data = storage.tryRead();
    assertThat(data.getReportedIssuesAsErrorCountPerLevel().get(ReportIssuesAsErrorLevel.NONE)).isEqualTo(1);
    assertThat(data.getReportedIssuesAsErrorCountPerLevel().get(ReportIssuesAsErrorLevel.MEDIUM_AND_ABOVE)).isEqualTo(2);
    assertThat(data.getReportedIssuesAsErrorCountPerLevel().get(ReportIssuesAsErrorLevel.ALL)).isNull();
  }

  @Test
  void tryUpdateAtomically_should_not_crash_if_too_many_read_write_requests() {
    var storageManager = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));

    Runnable read = storageManager::lastUploadTime;
    Runnable write = () -> storageManager.tryUpdateAtomically(TelemetryLocalStorage::incrementShowIssueRequestCount);
    Stream.of(
        IntStream.range(0, 100).mapToObj(operand -> CompletableFuture.runAsync(write)),
        IntStream.range(0, 100).mapToObj(value -> CompletableFuture.runAsync(read)),
        IntStream.range(0, 100).mapToObj(operand -> CompletableFuture.runAsync(write)),
        IntStream.range(0, 100).mapToObj(value -> CompletableFuture.runAsync(read))
      ).flatMap(Function.identity())
      .map(CompletableFuture::join)
      .toList();

    assertThat(storageManager.tryRead().getShowIssueRequestsCount()).isEqualTo(200);
  }

  @Test
  void tryRead_should_be_aware_of_file_deletion() {
    var storageManager = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));

    assertThat(storageManager.tryRead().getShowIssueRequestsCount()).isZero();

    storageManager.tryUpdateAtomically(TelemetryLocalStorage::incrementShowIssueRequestCount);
    assertThat(storageManager.tryRead().getShowIssueRequestsCount()).isEqualTo(1);

    filePath.toFile().delete();

    assertThat(storageManager.tryRead().getShowIssueRequestsCount()).isZero();
  }

  /**
   * Disabled on Windows because it doesn't always give the file modification time correctly
   */
  @Test
  @DisabledOnOs(OS.WINDOWS)
  void tryRead_should_be_aware_of_file_modification() throws IOException {
    var storageManager = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));

    assertThat(storageManager.tryRead().getShowIssueRequestsCount()).isZero();

    storageManager.tryUpdateAtomically(TelemetryLocalStorage::incrementShowIssueRequestCount);
    assertThat(storageManager.tryRead().getShowIssueRequestsCount()).isEqualTo(1);

    TelemetryLocalStorage newStorage = new TelemetryLocalStorage();
    newStorage.incrementShowIssueRequestCount();
    newStorage.incrementShowIssueRequestCount();

    writeToLocalStorageFile(newStorage);

    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(storageManager.tryRead().getShowIssueRequestsCount()).isEqualTo(2));
  }

  private void writeToLocalStorageFile(TelemetryLocalStorage newStorage) throws IOException {
    var newJson = new GsonBuilder()
      .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter().nullSafe())
      .registerTypeAdapter(LocalDate.class, new LocalDateAdapter().nullSafe())
      .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter().nullSafe())
      .create().toJson(newStorage);
    var encoded = Base64.getEncoder().encode(newJson.getBytes(StandardCharsets.UTF_8));
    writeToLocalStorageFile(encoded);
  }

  private void writeToLocalStorageFile(byte[] encoded) throws IOException {
    FileUtils.writeByteArrayToFile(filePath.toFile(), encoded);
  }

  @Test
  void tryRead_returns_default_local_storage_if_file_is_empty() throws IOException {
    writeToLocalStorageFile(new byte[0]);
    assertThat(filePath.toFile()).isEmpty();

    var storageManager = new TelemetryLocalStorageManager(filePath, mock(InitializeParams.class));
    assertThat(storageManager.isEnabled()).isTrue();
    assertThat(storageManager.tryRead().numUseDays()).isZero();
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
