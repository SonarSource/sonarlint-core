/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.storage.local;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.storage.adapter.LocalDateAdapter;
import org.sonarsource.sonarlint.core.commons.storage.adapter.LocalDateTimeAdapter;
import org.sonarsource.sonarlint.core.commons.storage.adapter.OffsetDateTimeAdapter;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class FileStorageManagerTest {

  private Path filePath;

  @BeforeEach
  void setUp(@TempDir Path temp) {
    filePath = temp.resolve("test");
  }

  @Test
  void should_update() {
    var storage = new FileStorageManager<>(filePath, Dummy::new, Dummy.class);

    storage.getStorage();
    assertThat(filePath).doesNotExist();

    storage.tryUpdateAtomically(dummy -> dummy.data = "update");
    assertThat(filePath).exists();

    var dummy = storage.getStorage();

    assertThat(dummy.data).isEqualTo("update");
  }

  @Test
  void supportConcurrentUpdates() {
    var storage = new FileStorageManager<>(filePath, Dummy::new, Dummy.class);
    int nThreads = 10;
    var executorService = Executors.newFixedThreadPool(nThreads);
    CountDownLatch latch = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    // Each thread will attempt to increment the counter by one
    IntStream.range(0, nThreads).forEach(i -> {
      futures.add(executorService.submit(() -> {
        try {
          latch.await();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        storage.tryUpdateAtomically(data -> data.counter++);
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
    assertThat(storage.getStorage().counter).isEqualTo(nThreads);
  }

  @Test
  void tryUpdateAtomically_should_not_crash_if_too_many_read_write_requests() {
    var storageManager = new FileStorageManager<>(filePath, Dummy::new, Dummy.class);

    Runnable read = () -> storageManager.getStorage().getCounter();
    Runnable write = () -> storageManager.tryUpdateAtomically(dummy -> dummy.counter++);
    Stream.of(
        IntStream.range(0, 100).mapToObj(operand -> CompletableFuture.runAsync(write)),
        IntStream.range(0, 100).mapToObj(value -> CompletableFuture.runAsync(read)),
        IntStream.range(0, 100).mapToObj(operand -> CompletableFuture.runAsync(write)),
        IntStream.range(0, 100).mapToObj(value -> CompletableFuture.runAsync(read))
      ).flatMap(Function.identity())
      .forEach(CompletableFuture::join);

    assertThat(storageManager.getStorage().counter).isEqualTo(200);
  }

  @Test
  void tryRead_should_be_aware_of_file_deletion() {
    var storageManager = new FileStorageManager<>(filePath, Dummy::new, Dummy.class);

    assertThat(storageManager.getStorage().counter).isZero();

    storageManager.tryUpdateAtomically(dummy -> dummy.counter++);
    assertThat(storageManager.getStorage().counter).isEqualTo(1);

    filePath.toFile().delete();

    assertThat(storageManager.getStorage().counter).isZero();
  }

  /**
   * Disabled on Windows because it doesn't always give the file modification time correctly
   */
  @Test
  @DisabledOnOs(OS.WINDOWS)
  void tryRead_should_be_aware_of_file_modification() throws IOException {
    var storageManager = new FileStorageManager<>(filePath, Dummy::new, Dummy.class);

    assertThat(storageManager.getStorage().counter).isZero();

    storageManager.tryUpdateAtomically(dummy -> dummy.counter++);
    assertThat(storageManager.getStorage().counter).isEqualTo(1);

    var dummy = new Dummy();
    dummy.counter = 2;
    writeToLocalStorageFile(dummy);

    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(storageManager.getStorage().counter).isEqualTo(2));
  }

  private void writeToLocalStorageFile(Object newStorage) throws IOException {
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

    var storageManager = new FileStorageManager<>(filePath, Dummy::new, Dummy.class);
    assertThat(storageManager.getStorage().data).isEqualTo("default");
    assertThat(storageManager.getStorage().counter).isZero();
  }

  private static class Dummy implements LocalStorage {

    private String data;
    private int counter = 0;

    Dummy() {
      this("default");
    }

    Dummy(String data) {
      this.data = data;
    }

    public int getCounter() {
      return counter;
    }
  }
}
