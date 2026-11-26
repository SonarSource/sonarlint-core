/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.plugin.resources;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.serverapi.plugins.StaticResource;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.serverconnection.storage.StaticResourceStorage;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectedResourceFetcherTests {

  @TempDir
  Path storage;

  @Test
  void reuses_a_cached_resource_with_the_recorded_hash() throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var calls = new AtomicInteger();
    var fetcher = fetcher(resource("linux-x64/native-tool", bytes), bytes, calls);

    var first = fetcher.fetch("javascript", "linux-x64/native-tool");
    var second = fetcher.fetch("javascript", "linux-x64/native-tool");

    assertThat(Files.readAllBytes(first)).isEqualTo(bytes);
    assertThat(second).isEqualTo(first);
    assertThat(first).isEqualTo(storage.resolve("resources").resolve(DigestUtils.sha256Hex(bytes)).resolve("native-tool"));
    assertThat(calls).hasValue(1);
  }

  @Test
  void reuses_a_cached_extraction_when_the_archive_is_gone() throws IOException {
    var bytes = zip("hello.txt", "hello");
    var calls = new AtomicInteger();
    var fetcher = fetcher(resource("tools/native.zip", bytes), bytes, calls);
    var extracted = fetcher.fetchAndExtract("javascript", "tools/native.zip");
    Files.delete(storage.resolve("resources").resolve(DigestUtils.sha256Hex(bytes)).resolve("native.zip"));

    var again = fetcher.fetchAndExtract("javascript", "tools/native.zip");

    assertThat(again).isEqualTo(extracted);
    assertThat(again.resolve("hello.txt")).hasContent("hello");
    assertThat(calls).hasValue(1);
  }

  @Test
  void keeps_an_extraction_another_process_already_published() throws IOException {
    var destination = storage.resolve("extracted");
    var extracting = storage.resolve(".extracting-local");
    Files.createDirectories(destination);
    Files.writeString(destination.resolve("hello.txt"), "hello");
    Files.createDirectories(extracting);
    Files.writeString(extracting.resolve("partial"), "partial");

    ConnectedResourceFetcher.publishExtraction(extracting, destination);

    assertThat(destination.resolve("hello.txt")).hasContent("hello");
    assertThat(extracting).doesNotExist();
  }

  @Test
  void keeps_a_completed_extraction_without_moving_it() throws IOException {
    var destination = storage.resolve("extracted");
    Files.createDirectories(destination);
    Files.writeString(destination.resolve("hello.txt"), "hello");
    Files.writeString(destination.resolve(StaticResourceStorage.EXTRACTION_COMPLETE), "");

    assertThat(ConnectedResourceFetcher.keepNewlyCompletedExtraction(destination)).isTrue();

    assertThat(destination.resolve("hello.txt")).hasContent("hello");
    assertThat(destination.resolve(StaticResourceStorage.EXTRACTION_COMPLETE)).isEmptyFile();
    try (var children = Files.list(storage)) {
      assertThat(children.map(path -> path.getFileName().toString())).containsExactly("extracted");
    }
  }

  @Test
  void moves_a_completed_extraction_back_into_place() throws IOException {
    var destination = storage.resolve("extracted");
    var aside = storage.resolve(".extracting-aside");
    Files.createDirectories(aside);
    Files.writeString(aside.resolve("hello.txt"), "hello");
    Files.writeString(aside.resolve(StaticResourceStorage.EXTRACTION_COMPLETE), "");

    ConnectedResourceFetcher.restoreCompletedExtraction(aside, destination);

    assertThat(destination.resolve("hello.txt")).hasContent("hello");
    assertThat(destination.resolve(StaticResourceStorage.EXTRACTION_COMPLETE)).isEmptyFile();
    assertThat(aside).doesNotExist();
  }

  @Test
  void discards_the_aside_copy_when_another_process_already_restored_the_extraction() throws IOException {
    var destination = storage.resolve("extracted");
    var aside = storage.resolve(".extracting-aside");
    Files.createDirectories(destination);
    Files.writeString(destination.resolve("hello.txt"), "winner");
    Files.writeString(destination.resolve(StaticResourceStorage.EXTRACTION_COMPLETE), "");
    Files.createDirectories(aside);
    Files.writeString(aside.resolve("hello.txt"), "loser");
    Files.writeString(aside.resolve(StaticResourceStorage.EXTRACTION_COMPLETE), "");

    ConnectedResourceFetcher.restoreCompletedExtraction(aside, destination);

    assertThat(destination.resolve("hello.txt")).hasContent("winner");
    assertThat(aside).doesNotExist();
  }

  @Test
  void waits_for_a_completion_marker_to_reappear() throws IOException {
    var complete = storage.resolve("extracted").resolve(StaticResourceStorage.EXTRACTION_COMPLETE);
    Files.createDirectories(complete.getParent());
    Files.writeString(complete, "");
    Files.delete(complete);
    var polls = new AtomicInteger();

    var found = ConnectedResourceFetcher.extractionRemainsComplete(complete, Duration.ofMillis(200), Duration.ofMillis(10), pollInterval -> {
      assertThat(pollInterval).isEqualTo(Duration.ofMillis(10));
      polls.incrementAndGet();
      try {
        Files.writeString(complete, "");
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });

    assertThat(found).isTrue();
    assertThat(polls).hasValue(1);
    assertThat(complete).isEmptyFile();
  }

  @Test
  void reports_an_extraction_incomplete_when_the_marker_stays_missing() {
    var complete = storage.resolve("extracted").resolve(StaticResourceStorage.EXTRACTION_COMPLETE);

    assertThat(ConnectedResourceFetcher.extractionRemainsComplete(complete)).isFalse();
  }

  @Test
  void stops_waiting_for_a_completion_marker_when_interrupted() {
    var complete = storage.resolve("extracted").resolve(StaticResourceStorage.EXTRACTION_COMPLETE);
    Thread.currentThread().interrupt();
    try {
      assertThat(ConnectedResourceFetcher.extractionRemainsComplete(complete)).isFalse();
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void reports_a_move_failure_when_the_incomplete_extraction_cannot_be_set_aside() throws IOException {
    var destination = storage.resolve("extracted");
    Files.createDirectories(destination);
    Files.writeString(destination.resolve("hello.txt"), "partial");
    Files.setPosixFilePermissions(storage, Set.of(OWNER_READ, OWNER_EXECUTE));

    try {
      assertThatThrownBy(() -> ConnectedResourceFetcher.keepNewlyCompletedExtraction(destination))
        .isInstanceOf(IOException.class);
      assertThat(destination.resolve("hello.txt")).hasContent("partial");
    } finally {
      Files.setPosixFilePermissions(storage, Set.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE));
    }
  }

  @Test
  void fails_the_restore_when_the_destination_is_an_incomplete_directory() throws IOException {
    var destination = storage.resolve("extracted");
    var aside = storage.resolve(".extracting-aside");
    Files.createDirectories(destination);
    Files.writeString(destination.resolve("partial.txt"), "partial");
    Files.createDirectories(aside);
    Files.writeString(aside.resolve("hello.txt"), "hello");
    Files.writeString(aside.resolve(StaticResourceStorage.EXTRACTION_COMPLETE), "");

    assertThatThrownBy(() -> ConnectedResourceFetcher.restoreCompletedExtraction(aside, destination))
      .isInstanceOf(IOException.class);

    assertThat(destination.resolve("partial.txt")).hasContent("partial");
    assertThat(aside.resolve("hello.txt")).hasContent("hello");
  }

  @Test
  void removes_an_incomplete_extraction_without_deleting_it_in_place() throws IOException {
    var destination = storage.resolve("extracted");
    Files.createDirectories(destination);
    Files.writeString(destination.resolve("hello.txt"), "partial");

    assertThat(ConnectedResourceFetcher.keepNewlyCompletedExtraction(destination)).isFalse();

    assertThat(destination).doesNotExist();
  }

  @Test
  void reextracts_when_the_previous_extraction_lost_its_completion_marker() throws IOException {
    var bytes = zip("hello.txt", "hello");
    var calls = new AtomicInteger();
    var fetcher = fetcher(resource("tools/native.zip", bytes), bytes, calls);
    var extracted = fetcher.fetchAndExtract("javascript", "tools/native.zip");
    Files.delete(extracted.resolve(StaticResourceStorage.EXTRACTION_COMPLETE));
    Files.delete(extracted.resolve("hello.txt"));

    var again = fetcher.fetchAndExtract("javascript", "tools/native.zip");

    assertThat(again.resolve("hello.txt")).hasContent("hello");
    assertThat(again.resolve(StaticResourceStorage.EXTRACTION_COMPLETE)).isEmptyFile();
    assertThat(calls).hasValue(1);
  }

  @Test
  void leaves_another_process_extraction_directory_in_place() throws IOException {
    var bytes = zip("hello.txt", "hello");
    var foreign = storage.resolve("resources").resolve(DigestUtils.sha256Hex(bytes)).resolve(".extracting");
    Files.createDirectories(foreign);
    Files.writeString(foreign.resolve("partial"), "partial");
    var fetcher = fetcher(resource("tools/native.zip", bytes), bytes);

    var extracted = fetcher.fetchAndExtract("javascript", "tools/native.zip");

    assertThat(extracted.resolve("hello.txt")).hasContent("hello");
    assertThat(foreign.resolve("partial")).hasContent("partial");
  }

  private ConnectedResourceFetcher fetcher(StaticResource resource, byte[] bytes) {
    return fetcher(List.of(resource), bytes, new AtomicInteger());
  }

  private ConnectedResourceFetcher fetcher(List<StaticResource> resources, byte[] bytes) {
    return fetcher(resources, bytes, new AtomicInteger());
  }

  private ConnectedResourceFetcher fetcher(StaticResource resource, byte[] bytes, AtomicInteger calls) {
    return fetcher(List.of(resource), bytes, calls);
  }

  private ConnectedResourceFetcher fetcher(List<StaticResource> resources, byte[] bytes, AtomicInteger calls) {
    var plugin = new StoredPlugin("javascript", "jar", storage.resolve("plugins").resolve("javascript.jar"), "folder", resources);
    var clients = mock(SonarQubeClientManager.class);
    when(clients.withActiveClientAndReturn(eq("conn"), any())).thenAnswer(invocation -> {
      calls.incrementAndGet();
      return Optional.of(bytes);
    });
    return new ConnectedResourceFetcher("conn", Map.of("javascript", plugin), new StaticResourceStorage(storage), clients);
  }

  private static StaticResource resource(String path, byte[] bytes) {
    return new StaticResource(path, DigestUtils.sha256Hex(bytes), bytes.length);
  }

  private static byte[] zip(String name, String content) throws IOException {
    var file = Files.createTempFile("resource", ".zip");
    try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
      zip.putNextEntry(new ZipEntry(name));
      zip.write(content.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return Files.readAllBytes(file);
  }

}
