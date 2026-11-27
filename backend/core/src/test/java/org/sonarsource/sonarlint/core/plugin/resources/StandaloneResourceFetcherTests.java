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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifact;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifactSource;
import org.sonarsource.sonarlint.core.serverconnection.storage.StaticResourceStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StandaloneResourceFetcherTests {

  @TempDir
  Path storage;

  @Test
  void reuses_a_cached_resource_whose_hash_matches_the_manifest() throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var hash = DigestUtils.sha256Hex(bytes);
    var calls = new AtomicInteger();
    var urls = new ArrayList<String>();
    writeManifest("javascript", "linux-x64/native-tool", hash.toUpperCase(Locale.ROOT), bytes.length);
    var fetcher = fetcher(url -> {
      calls.incrementAndGet();
      urls.add(url);
      return bytes;
    });

    var first = fetcher.fetch("javascript", "linux-x64/native-tool");
    var second = fetcher.fetch("javascript", "linux-x64/native-tool");

    assertThat(Files.readAllBytes(first)).isEqualTo(bytes);
    assertThat(second).isEqualTo(first);
    assertThat(first).isEqualTo(cachedFile("javascript", hash, "native-tool"));
    try (var children = Files.list(first.getParent())) {
      assertThat(children).containsExactly(first);
    }
    assertThat(calls).hasValue(1);
    assertThat(urls).containsExactly(BinariesArtifact.JAVASCRIPT.resourceUrl("linux-x64/native-tool"));
  }

  @Test
  void replaces_a_cached_file_whose_hash_does_not_match() throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var hash = DigestUtils.sha256Hex(bytes);
    var cached = cachedFile("javascript", hash, "native-tool");
    Files.createDirectories(cached.getParent());
    Files.writeString(cached, "stale");
    writeManifest("javascript", "linux-x64/native-tool", hash, bytes.length);
    var calls = new AtomicInteger();
    var fetcher = fetcher(url -> {
      calls.incrementAndGet();
      return bytes;
    });

    var fetched = fetcher.fetch("javascript", "linux-x64/native-tool");

    assertThat(fetched).isEqualTo(cached).hasContent("native");
    assertThat(calls).hasValue(1);
  }

  @Test
  void rejects_a_resource_whose_hash_does_not_match_the_manifest() throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    writeManifest("javascript", "linux-x64/native-tool", "deadbeef", bytes.length);
    var fetcher = fetcher(url -> bytes);

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("expected deadbeef");
    assertThat(cachedFile("javascript", "deadbeef", "native-tool")).doesNotExist();
  }

  @Test
  void rejects_a_plugin_without_that_resource() throws IOException {
    writeManifest("javascript", "other", "abc", 1);
    var fetcher = fetcher(url -> new byte[0]);

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Plugin 'javascript' has no static resource 'linux-x64/native-tool'");
  }

  @Test
  void rejects_a_plugin_that_is_not_an_exploded_zip() {
    var fetcher = fetcher(url -> new byte[0]);

    assertThatThrownBy(() -> fetcher.fetch("cpp", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Plugin 'cpp' is not a standalone ZIP plugin");
  }

  @Test
  void rejects_a_plugin_with_no_manifest() {
    var fetcher = fetcher(url -> new byte[0]);

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Plugin 'javascript' has no manifest");
  }

  @Test
  void rejects_a_manifest_that_is_not_json() throws IOException {
    var manifest = BinariesArtifact.JAVASCRIPT.localManifest(storage.resolve(BinariesArtifactSource.CACHE_SUBDIR));
    Files.createDirectories(manifest.getParent());
    Files.writeString(manifest, "not-json");
    var fetcher = fetcher(url -> new byte[0]);

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Cannot read manifest for plugin 'javascript'");
  }

  @Test
  void rejects_a_path_that_escapes_the_resources_directory() throws IOException {
    var calls = new AtomicInteger();
    writeManifest("javascript", "../secret", "abc", 1);
    var fetcher = fetcher(url -> {
      calls.incrementAndGet();
      return new byte[0];
    });

    assertThatThrownBy(() -> fetcher.fetch("javascript", "../secret"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid static resource path: ../secret");
    assertThat(calls).hasValue(0);
  }

  @Test
  void rejects_a_resource_with_a_blank_hash() throws IOException {
    writeManifest("javascript", "linux-x64/native-tool", "", 1);
    var fetcher = fetcher(url -> new byte[0]);

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Plugin 'javascript' has no hash for static resource 'linux-x64/native-tool'");
  }

  @Test
  void rejects_a_manifest_without_static_resources() throws IOException {
    var manifest = BinariesArtifact.JAVASCRIPT.localManifest(storage.resolve(BinariesArtifactSource.CACHE_SUBDIR));
    Files.createDirectories(manifest.getParent());
    Files.writeString(manifest, "{}");
    var fetcher = fetcher(url -> new byte[0]);

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Plugin 'javascript' has no static resource 'linux-x64/native-tool'");
  }

  @Test
  void rejects_an_absolute_resource_path() {
    var fetcher = fetcher(url -> new byte[0]);

    assertThatThrownBy(() -> fetcher.fetch("javascript", "/linux-x64/native-tool"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid static resource path: /linux-x64/native-tool");
  }

  @Test
  void downloads_a_resource_through_the_http_client() throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    writeManifest("javascript", "linux-x64/native-tool", DigestUtils.sha256Hex(bytes), bytes.length);
    var response = mock(HttpClient.Response.class);
    when(response.isSuccessful()).thenReturn(true);
    when(response.bodyAsStream()).thenReturn(new ByteArrayInputStream(bytes));
    var client = mock(HttpClient.class);
    when(client.get(anyString())).thenReturn(response);
    var provider = mock(HttpClientProvider.class);
    when(provider.getHttpClientWithoutAuth()).thenReturn(client);
    var fetcher = new StandaloneResourceFetcher(storage, provider);

    assertThat(fetcher.fetch("javascript", "linux-x64/native-tool")).hasContent("native");
  }

  @Test
  void reports_an_io_failure_while_downloading() throws IOException {
    writeManifest("javascript", "linux-x64/native-tool", "abc", 1);
    var response = mock(HttpClient.Response.class);
    when(response.isSuccessful()).thenReturn(true);
    when(response.bodyAsStream()).thenReturn(new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("broken");
      }
    });
    var client = mock(HttpClient.class);
    when(client.get(anyString())).thenReturn(response);
    var provider = mock(HttpClientProvider.class);
    when(provider.getHttpClientWithoutAuth()).thenReturn(client);
    var fetcher = new StandaloneResourceFetcher(storage, provider);

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(UncheckedIOException.class)
      .hasMessageContaining("Failed to download static resource");
  }

  @Test
  void reports_an_http_failure_from_binaries() throws IOException {
    writeManifest("javascript", "linux-x64/native-tool", "abc", 1);
    var response = mock(HttpClient.Response.class);
    when(response.code()).thenReturn(404);
    var client = mock(HttpClient.class);
    when(client.get(BinariesArtifact.JAVASCRIPT.resourceUrl("linux-x64/native-tool"))).thenReturn(response);
    var provider = mock(HttpClientProvider.class);
    when(provider.getHttpClientWithoutAuth()).thenReturn(client);
    var fetcher = new StandaloneResourceFetcher(storage, provider);

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Failed to download static resource: HTTP 404");
    assertThat(cachedFile("javascript", "abc", "native-tool")).doesNotExist();
  }

  @Test
  void reuses_an_extracted_archive_without_downloading_again() throws IOException {
    var bytes = zip("hello.txt", "hello");
    var hash = DigestUtils.sha256Hex(bytes);
    var calls = new AtomicInteger();
    writeManifest("javascript", "tools/native.zip", hash, bytes.length);
    var fetcher = fetcher(url -> {
      calls.incrementAndGet();
      return bytes;
    });

    var extracted = fetcher.fetchAndExtract("javascript", "tools/native.zip");
    var again = fetcher.fetchAndExtract("javascript", "tools/native.zip");

    assertThat(extracted.resolve("hello.txt")).hasContent("hello");
    assertThat(extracted.resolve(StaticResourceStorage.EXTRACTION_COMPLETE)).isEmptyFile();
    assertThat(again).isEqualTo(extracted);
    assertThat(extracted).isEqualTo(cachedFile("javascript", hash, "native.zip").getParent().resolve(StandaloneResourceStorage.EXTRACTED_DIRECTORY));
    assertThat(Files.readAllBytes(cachedFile("javascript", hash, "native.zip"))).isEqualTo(bytes);
    assertThat(calls).hasValue(1);
  }

  @Test
  void extracts_a_tar_gz_resource() throws IOException {
    var bytes = tarGz("hello.txt", "hello");
    var hash = DigestUtils.sha256Hex(bytes);
    writeManifest("python", "tools/native.tar.gz", hash, bytes.length);
    var fetcher = fetcher(url -> bytes);

    var extracted = fetcher.fetchAndExtract("python", "tools/native.tar.gz");

    assertThat(extracted.resolve("hello.txt")).hasContent("hello");
    assertThat(extracted).isEqualTo(cachedFile("python", hash, "native.tar.gz").getParent().resolve(StandaloneResourceStorage.EXTRACTED_DIRECTORY));
  }

  @Test
  void reports_a_corrupt_archive() throws IOException {
    var bytes = "not-a-zip".getBytes(StandardCharsets.UTF_8);
    writeManifest("javascript", "tools/native.zip", DigestUtils.sha256Hex(bytes), bytes.length);
    var fetcher = fetcher(url -> bytes);

    assertThatThrownBy(() -> fetcher.fetchAndExtract("javascript", "tools/native.zip"))
      .isInstanceOf(UncheckedIOException.class)
      .hasMessageContaining("Cannot extract 'tools/native.zip'");
  }

  @Test
  void rejects_extracting_a_resource_that_is_not_an_archive() throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    writeManifest("javascript", "linux-x64/native-tool", DigestUtils.sha256Hex(bytes), bytes.length);
    var fetcher = fetcher(url -> bytes);

    assertThatThrownBy(() -> fetcher.fetchAndExtract("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("only zip and tar.gz");
  }

  private Path cachedFile(String pluginKey, String hash, String filename) {
    var artifact = BinariesArtifact.findByKey(pluginKey).orElseThrow();
    var versionDirectory = artifact.localManifest(storage.resolve(BinariesArtifactSource.CACHE_SUBDIR)).getParent();
    return new StandaloneResourceStorage(versionDirectory).file(hash, filename);
  }

  private StandaloneResourceFetcher fetcher(StandaloneResourceFetcher.Downloader downloader) {
    return new StandaloneResourceFetcher(storage, downloader);
  }

  private void writeManifest(String pluginKey, String path, String hash, long size) throws IOException {
    var artifact = BinariesArtifact.findByKey(pluginKey).orElseThrow();
    var manifest = artifact.localManifest(storage.resolve(BinariesArtifactSource.CACHE_SUBDIR));
    Files.createDirectories(manifest.getParent());
    Files.writeString(manifest, """
      {"staticResources":[{"path":"%s","hash":"%s","size":%d}]}
      """.formatted(path, hash, size));
  }

  private static byte[] zip(String name, String content) throws IOException {
    var file = Files.createTempFile("resource", ".zip");
    try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
      zip.putNextEntry(new ZipEntry(name));
      zip.write(content.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    var bytes = Files.readAllBytes(file);
    Files.delete(file);
    return bytes;
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
    var bytes = Files.readAllBytes(file);
    Files.delete(file);
    return bytes;
  }

}
