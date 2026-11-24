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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.serverapi.plugins.StaticResource;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.serverconnection.storage.StaticResourceStorage;

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
  void shares_one_download_per_resource_inside_the_process() throws Exception {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var calls = new AtomicInteger();
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var plugin = new StoredPlugin("javascript", "jar", storage.resolve("plugins").resolve("javascript.jar"), "folder",
      List.of(resource("linux-x64/native-tool", bytes)));
    var clients = mock(SonarQubeClientManager.class);
    when(clients.withActiveClientAndReturn(eq("conn"), any())).thenAnswer(invocation -> {
      calls.incrementAndGet();
      started.countDown();
      release.await();
      return Optional.of(bytes);
    });
    var fetcher = new ConnectedResourceFetcher("conn", Map.of("javascript", plugin), new StaticResourceStorage(storage), clients);

    var first = new Thread(() -> fetcher.fetch("javascript", "linux-x64/native-tool"));
    first.start();
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    var second = new Thread(() -> fetcher.fetch("javascript", "linux-x64/native-tool"));
    second.start();
    var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (second.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
      second.join(10);
    }

    assertThat(second.getState()).isEqualTo(Thread.State.BLOCKED);
    assertThat(calls).hasValue(1);
    release.countDown();
    first.join();
    second.join();
    assertThat(calls).hasValue(1);
  }

  @Test
  void rejects_a_resource_whose_hash_does_not_match_the_inventory() {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var fetcher = fetcher(new StaticResource("linux-x64/native-tool", "deadbeef", bytes.length), bytes);

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("expected deadbeef");
    assertThat(storage.resolve("resources")).doesNotExist();
  }

  @Test
  void rejects_a_plugin_without_that_resource() {
    var fetcher = fetcher(List.of(), new byte[0]);

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Plugin 'javascript' has no static resource 'linux-x64/native-tool'");
  }

  @Test
  void rejects_an_unknown_plugin() {
    var fetcher = new ConnectedResourceFetcher("conn", Map.of(), mock(SonarQubeClientManager.class), new StaticResourceStorage(storage));

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Plugin 'javascript' is not stored for connection 'conn'");
  }

  @Test
  void extracts_a_zip_resource() throws IOException {
    var bytes = zip("hello.txt", "hello");
    var calls = new AtomicInteger();
    var fetcher = fetcher(resource("tools/native.zip", bytes), bytes, calls);

    var extracted = fetcher.fetchAndExtract("javascript", "tools/native.zip");
    var again = fetcher.fetchAndExtract("javascript", "tools/native.zip");

    assertThat(extracted.resolve("hello.txt")).hasContent("hello");
    assertThat(again).isEqualTo(extracted);
    assertThat(calls).hasValue(1);
  }

  @Test
  void keeps_an_extraction_another_process_already_published() throws IOException {
    var destination = storage.resolve("extracted");
    var staging = storage.resolve(".extracting-local");
    Files.createDirectories(destination);
    Files.writeString(destination.resolve("hello.txt"), "hello");
    Files.createDirectories(staging);
    Files.writeString(staging.resolve("partial"), "partial");

    ConnectedResourceFetcher.publishExtraction(staging, destination);

    assertThat(destination.resolve("hello.txt")).hasContent("hello");
    assertThat(staging).doesNotExist();
  }

  @Test
  void deletes_staging_directories_left_by_an_interrupted_extraction() throws IOException {
    var bytes = zip("hello.txt", "hello");
    var hashDirectory = storage.resolve("resources").resolve(DigestUtils.sha256Hex(bytes));
    var stale = hashDirectory.resolve(".extracting-stale");
    var current = hashDirectory.resolve(".extracting-current");
    Files.createDirectories(stale);
    Files.writeString(stale.resolve("partial"), "partial");
    Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minus(Duration.ofHours(2))));
    Files.createDirectories(current);
    Files.writeString(current.resolve("partial"), "partial");
    var fetcher = fetcher(resource("tools/native.zip", bytes), (plugin, path) -> bytes);

    var extracted = fetcher.fetchAndExtract("javascript", "tools/native.zip");

    assertThat(extracted.resolve("hello.txt")).hasContent("hello");
    assertThat(stale).doesNotExist();
    assertThat(current.resolve("partial")).hasContent("partial");
  }

  @Test
  void leaves_another_process_extraction_directory_in_place() throws IOException {
    var bytes = zip("hello.txt", "hello");
    var foreign = storage.resolve("resources").resolve(DigestUtils.sha256Hex(bytes)).resolve(".extracting");
    Files.createDirectories(foreign);
    Files.writeString(foreign.resolve("partial"), "partial");
    var fetcher = fetcher(resource("tools/native.zip", bytes), (plugin, path) -> bytes);

    var extracted = fetcher.fetchAndExtract("javascript", "tools/native.zip");

    assertThat(extracted.resolve("hello.txt")).hasContent("hello");
    assertThat(foreign.resolve("partial")).hasContent("partial");
  }

  @Test
  void extracts_a_tar_gz_resource() throws IOException {
    var bytes = tarGz("hello.txt", "hello");
    var fetcher = fetcher(resource("tools/native.tar.gz", bytes), bytes);

    var extracted = fetcher.fetchAndExtract("javascript", "tools/native.tar.gz");

    assertThat(extracted.resolve("hello.txt")).hasContent("hello");
  }

  @Test
  void rejects_extracting_a_resource_that_is_not_an_archive() {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var fetcher = fetcher(resource("linux-x64/native-tool", bytes), bytes);

    assertThatThrownBy(() -> fetcher.fetchAndExtract("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("only zip and tar.gz");
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

  private static byte[] tarGz(String name, String content) throws IOException {
    var file = Files.createTempFile("resource", ".tar.gz");
    var data = content.getBytes(StandardCharsets.UTF_8);
    try (var tar = new TarArchiveOutputStream(new GzipCompressorOutputStream(Files.newOutputStream(file)))) {
      var entry = new TarArchiveEntry(name);
      entry.setSize(data.length);
      tar.putArchiveEntry(entry);
      tar.write(data);
      tar.closeArchiveEntry();
    }
    return Files.readAllBytes(file);
  }

}
