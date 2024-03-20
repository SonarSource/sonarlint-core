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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.fail;

class TelemetryLocalStorageManagerTests {

  private final LocalDate today = LocalDate.now();
  private Path filePath;

  @BeforeEach
  void setUp(@TempDir Path temp) {
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
    assertThat(data2.installTime()).isEqualTo(tenDaysAgo);
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
  void should_not_crash_when_cannot_read_storage(@TempDir Path temp) {
    InternalDebug.setEnabled(false);
    assertThatCode(() -> new TelemetryLocalStorageManager(temp).tryRead())
      .doesNotThrowAnyException();

  }

  @Test
  void should_not_crash_when_cannot_write_storage(@TempDir Path temp) {
    InternalDebug.setEnabled(false);
    assertThatCode(() -> new TelemetryLocalStorageManager(temp).tryUpdateAtomically(d -> {}))
      .doesNotThrowAnyException();
  }

  @Test
  void supportConcurrentUpdates() {
    var storage = new TelemetryLocalStorageManager(filePath);
    // Put some data to avoid migration
    storage.tryUpdateAtomically(data -> {
      data.setInstallTime(OffsetDateTime.now().minus(50, ChronoUnit.DAYS));
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
    var storage = new TelemetryLocalStorageManager(filePath);

    storage.tryUpdateAtomically(TelemetryLocalStorage::incrementOpenHotspotInBrowserCount);
    storage.tryUpdateAtomically(TelemetryLocalStorage::incrementOpenHotspotInBrowserCount);

    var data2 = storage.tryRead();
    assertThat(data2.openHotspotInBrowserCount()).isEqualTo(2);
  }

  @Test
  void should_increment_hotspot_status_changed() {
    var storage = new TelemetryLocalStorageManager(filePath);

    storage.tryUpdateAtomically(TelemetryLocalStorage::incrementHotspotStatusChangedCount);
    storage.tryUpdateAtomically(TelemetryLocalStorage::incrementHotspotStatusChangedCount);
    storage.tryUpdateAtomically(TelemetryLocalStorage::incrementHotspotStatusChangedCount);

    var data = storage.tryRead();
    assertThat(data.hotspotStatusChangedCount()).isEqualTo(3);
  }

  @Test
  void should_increment_issue_status_changed() {
    var storage = new TelemetryLocalStorageManager(filePath);

    storage.tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.addIssueStatusChanged("ruleKey1"));
    storage.tryUpdateAtomically(telemetryLocalStorage -> telemetryLocalStorage.addIssueStatusChanged("ruleKey2"));

    var data = storage.tryRead();
    assertThat(data.issueStatusChangedCount()).isEqualTo(2);
    assertThat(data.issueStatusChangedRuleKeys()).containsExactlyInAnyOrder("ruleKey1", "ruleKey2");
  }

  @Test
  void tryUpdateAtomically_should_not_crash_if_too_many_read_write_requests() {
    var storageManager = new TelemetryLocalStorageManager(filePath);

    Runnable read = storageManager::lastUploadTime;
    Runnable write = () -> storageManager.tryUpdateAtomically(TelemetryLocalStorage::incrementShowIssueRequestCount);
    List<Void> ignored = Stream.of(
        IntStream.range(0, 100).mapToObj(operand -> CompletableFuture.runAsync(write)),
        IntStream.range(0, 100).mapToObj(value -> CompletableFuture.runAsync(read)),
        IntStream.range(0, 100).mapToObj(operand -> CompletableFuture.runAsync(write)),
        IntStream.range(0, 100).mapToObj(value -> CompletableFuture.runAsync(read))
      ).flatMap(Function.identity())
      .map(CompletableFuture::join)
      .collect(Collectors.toList());

    assertThat(storageManager.tryRead().getShowIssueRequestsCount()).isEqualTo(200);
  }

  @Test
  void tryRead_should_be_aware_of_file_deletion() {
    var storageManager = new TelemetryLocalStorageManager(filePath);

    assertThat(storageManager.tryRead().getShowIssueRequestsCount()).isZero();

    storageManager.tryUpdateAtomically(TelemetryLocalStorage::incrementShowIssueRequestCount);
    assertThat(storageManager.tryRead().getShowIssueRequestsCount()).isEqualTo(1);

    filePath.toFile().delete();

    assertThat(storageManager.tryRead().getShowIssueRequestsCount()).isZero();
  }

  @Test
  void tryRead_should_be_aware_of_file_modification() throws IOException {
    var storageManager = new TelemetryLocalStorageManager(filePath);

    assertThat(storageManager.tryRead().getShowIssueRequestsCount()).isZero();

    storageManager.tryUpdateAtomically(TelemetryLocalStorage::incrementShowIssueRequestCount);
    assertThat(storageManager.tryRead().getShowIssueRequestsCount()).isEqualTo(1);

    TelemetryLocalStorage newStorage = new TelemetryLocalStorage();
    IntStream.range(0, 100).forEach(value -> newStorage.incrementShowIssueRequestCount());
    writeToLocalStorageFile(newStorage);

    assertThat(storageManager.tryRead().getShowIssueRequestsCount()).isEqualTo(100);
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

    var storageManager = new TelemetryLocalStorageManager(filePath);
    assertThat(storageManager.isEnabled()).isTrue();
    assertThat(storageManager.tryRead().numUseDays()).isZero();
  }
}
