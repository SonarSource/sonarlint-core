/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.monitoring;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonitoringUserIdStoreTests {

  @RegisterExtension
  static final SonarLintLogTester logTester = new SonarLintLogTester();

  private Path userHome;
  private Path userIdFilePath;

  @BeforeEach
  void setUp(@TempDir Path temp) {
    userHome = temp;
    userIdFilePath = userHome.resolve("id");
  }

  private MonitoringUserIdStore createStore() {
    var userPaths = mock(UserPaths.class);
    when(userPaths.getUserHome()).thenReturn(userHome);
    return new MonitoringUserIdStore(userPaths);
  }

  @Test
  void should_create_file_and_uuid_on_first_call() throws IOException {
    var store = createStore();

    assertThat(userIdFilePath).doesNotExist();

    var userId = store.getOrCreate();

    assertThat(userId).isPresent();
    assertThat(userIdFilePath).exists();
    var decodedContent = new String(Base64.getDecoder().decode(Files.readString(userIdFilePath)), StandardCharsets.UTF_8);
    assertThat(decodedContent).isEqualTo(userId.get().toString());
  }

  @Test
  void should_reuse_existing_uuid() throws IOException {
    var existingUuid = UUID.randomUUID();
    writeEncodedUuid(existingUuid);

    var store = createStore();
    var userId = store.getOrCreate();

    assertThat(userId)
      .isPresent()
      .contains(existingUuid);
  }

  @Test
  void should_return_cached_uuid_on_subsequent_calls() {
    var store = createStore();

    var firstCall = store.getOrCreate();
    var secondCall = store.getOrCreate();

    assertThat(firstCall).isPresent();
    assertThat(secondCall).isPresent();
    assertThat(firstCall).contains(secondCall.get());
  }

  @Test
  void should_overwrite_invalid_content_with_new_uuid() throws IOException {
    Files.writeString(userIdFilePath, "not-a-valid-base64-or-uuid");

    var store = createStore();
    var userId = store.getOrCreate();

    assertThat(userId).isPresent();
    var decodedContent = new String(Base64.getDecoder().decode(Files.readString(userIdFilePath)), StandardCharsets.UTF_8);
    assertThat(decodedContent).isEqualTo(userId.get().toString());
  }

  @Test
  void should_overwrite_empty_file_with_new_uuid() throws IOException {
    Files.writeString(userIdFilePath, "");

    var store = createStore();
    var userId = store.getOrCreate();

    assertThat(userId).isPresent();
    var decodedContent = new String(Base64.getDecoder().decode(Files.readString(userIdFilePath)), StandardCharsets.UTF_8);
    assertThat(decodedContent).isEqualTo(userId.get().toString());
  }

  @Test
  void should_trim_whitespace_when_reading_uuid() throws IOException {
    var existingUuid = UUID.randomUUID();
    var encoded = Base64.getEncoder().encodeToString(("  " + existingUuid.toString() + "\n  ").getBytes(StandardCharsets.UTF_8));
    Files.writeString(userIdFilePath, encoded);

    var store = createStore();
    var userId = store.getOrCreate();

    assertThat(userId)
      .isPresent()
      .contains(existingUuid);
  }

  private void writeEncodedUuid(UUID uuid) throws IOException {
    var encoded = Base64.getEncoder().encodeToString(uuid.toString().getBytes(StandardCharsets.UTF_8));
    Files.writeString(userIdFilePath, encoded);
  }

  @Test
  void concurrent_calls_should_return_same_uuid() {
    var store = createStore();

    int numberOfThreads = 10;
    var executorService = Executors.newFixedThreadPool(numberOfThreads);
    CountDownLatch latch = new CountDownLatch(1);
    List<Future<UUID>> futures = new ArrayList<>();

    IntStream.range(0, numberOfThreads).forEach(i -> {
      futures.add(executorService.submit(() -> {
        try {
          latch.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        return store.getOrCreate().orElse(null);
      }));
    });

    latch.countDown();

    var results = futures.stream().map(f -> {
      try {
        return f.get();
      } catch (ExecutionException e) {
        fail(e.getCause());
        return null;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }).toList();

    assertThat(results)
      .hasSize(numberOfThreads)
      .allMatch(uuid -> uuid != null && uuid.equals(results.get(0)));
  }
}
