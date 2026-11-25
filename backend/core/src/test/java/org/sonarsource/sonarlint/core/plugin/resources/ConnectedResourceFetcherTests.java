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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.staticfiles.StaticFiles;
import org.sonarsource.sonarlint.core.serverapi.plugins.StaticResource;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectedResourceFetcherTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path storage;

  @Test
  void downloads_the_resource_on_every_call() throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var calls = new AtomicInteger();
    var fetcher = fetcher(resource("linux-x64/native-tool", bytes), (plugin, path) -> {
      calls.incrementAndGet();
      return bytes;
    });

    var first = fetcher.fetch("javascript", "linux-x64/native-tool");
    var second = fetcher.fetch("javascript", "linux-x64/native-tool");

    assertThat(Files.readAllBytes(first)).isEqualTo(bytes);
    assertThat(Files.readAllBytes(second)).isEqualTo(bytes);
    assertThat(first).isNotEqualTo(second);
    assertThat(calls).hasValue(2);
  }

  @Test
  void rejects_a_resource_whose_hash_does_not_match_the_inventory() {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var fetcher = fetcher(new StaticResource("linux-x64/native-tool", "not-the-hash", bytes.length), (plugin, path) -> bytes);

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("expected not-the-hash");
  }

  @Test
  void rejects_a_plugin_without_that_resource() {
    var fetcher = fetcher(List.of(), (plugin, path) -> new byte[0]);

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Plugin 'javascript' has no static resource 'linux-x64/native-tool'");
  }

  @Test
  void rejects_an_unknown_plugin() {
    var fetcher = new ConnectedResourceFetcher("conn", Map::of, (plugin, path) -> new byte[0], storage);

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Plugin 'javascript' is not stored for connection 'conn'");
  }

  @Test
  void extracts_a_zip_resource() throws IOException {
    var bytes = zip("hello.txt", "hello");
    var fetcher = fetcher(resource("tools/native.zip", bytes), (plugin, path) -> bytes);

    var extracted = fetcher.fetchAndExtract("javascript", "tools/native.zip");

    assertThat(extracted.resolve("hello.txt")).hasContent("hello");
  }

  @Test
  void extracts_a_tar_gz_resource() throws IOException {
    var bytes = tarGz("hello.txt", "hello");
    var fetcher = fetcher(resource("tools/native.tar.gz", bytes), (plugin, path) -> bytes);

    var extracted = fetcher.fetchAndExtract("javascript", "tools/native.tar.gz");

    assertThat(extracted.resolve("hello.txt")).hasContent("hello");
  }

  @Test
  void rejects_extracting_a_resource_that_is_not_an_archive() {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var fetcher = fetcher(resource("linux-x64/native-tool", bytes), (plugin, path) -> bytes);

    assertThatThrownBy(() -> fetcher.fetchAndExtract("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("only zip and tar.gz");
  }

  @Test
  void downloads_a_server_resource_into_the_connection_directory() throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var fetcher = connected(plugin(resource("linux-x64/native-tool", bytes)), api -> {
      when(api.isSonarCloud()).thenReturn(false);
      var staticFiles = mock(StaticFiles.class);
      when(api.staticFiles()).thenReturn(staticFiles);
      answerWith(staticFiles, bytes);
    });

    var file = fetcher.fetch("javascript", "linux-x64/native-tool");

    assertThat(Files.readAllBytes(file)).isEqualTo(bytes);
    assertThat(file).startsWith(storage.resolve("tmp"));
  }

  @Test
  void downloads_a_sonarcloud_resource() throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var fetcher = connected(plugin(resource("linux-x64/native-tool", bytes)), api -> {
      when(api.isSonarCloud()).thenReturn(true);
      var helper = mock(ServerApiHelper.class);
      var response = mock(HttpClient.Response.class);
      when(api.getHelper()).thenReturn(helper);
      when(helper.getBaseUrl()).thenReturn("https://sonarcloud.io");
      when(helper.getAnonymousUrl(any(), any())).thenReturn(response);
      when(response.bodyAsStream()).thenReturn(new ByteArrayInputStream(bytes));
    });

    assertThat(Files.readAllBytes(fetcher.fetch("javascript", "linux-x64/native-tool"))).isEqualTo(bytes);
  }

  @Test
  void rejects_a_sonarcloud_plugin_without_a_resource_folder() {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var plugin = new StoredPlugin("javascript", "jar", storage.resolve("javascript.jar"), " ", List.of(resource("linux-x64/native-tool", bytes)));
    var fetcher = connected(plugin, api -> {
      when(api.isSonarCloud()).thenReturn(true);
    });

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("no static resource folder");
  }

  @Test
  void rejects_a_connection_that_cannot_download() {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var clients = mock(SonarQubeClientManager.class);
    when(clients.withActiveClientAndReturn(eq("conn"), any())).thenReturn(Optional.empty());
    var fetcher = connected(plugin(resource("linux-x64/native-tool", bytes)), clients);

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("cannot download");
  }

  @Test
  void wraps_a_download_that_cannot_be_read() {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var fetcher = connected(plugin(resource("linux-x64/native-tool", bytes)), api -> {
      when(api.isSonarCloud()).thenReturn(false);
      var staticFiles = mock(StaticFiles.class);
      when(api.staticFiles()).thenReturn(staticFiles);
      answerWith(staticFiles, new InputStream() {
        @Override
        public int read() throws IOException {
          throw new IOException("broken");
        }
      });
    });

    assertThatThrownBy(() -> fetcher.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(UncheckedIOException.class)
      .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void wraps_an_archive_that_cannot_be_extracted() throws IOException {
    var bytes = zip("../outside.txt", "nope");
    var fetcher = fetcher(resource("tools/native.zip", bytes), (plugin, path) -> bytes);

    assertThatThrownBy(() -> fetcher.fetchAndExtract("javascript", "tools/native.zip"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("outside the target directory");
  }

  private ConnectedResourceFetcher connected(StoredPlugin plugin, Consumer<ServerApi> prepareApi) {
    var clients = mock(SonarQubeClientManager.class);
    when(clients.withActiveClientAndReturn(eq("conn"), any())).thenAnswer(invocation -> {
      Function<ServerApi, byte[]> call = invocation.getArgument(1);
      var api = mock(ServerApi.class);
      prepareApi.accept(api);
      return Optional.of(call.apply(api));
    });
    return connected(plugin, clients);
  }

  private ConnectedResourceFetcher connected(StoredPlugin plugin, SonarQubeClientManager clients) {
    var storageService = mock(StorageService.class);
    var connection = mock(ConnectionStorage.class);
    var plugins = mock(PluginsStorage.class);
    when(storageService.connection("conn")).thenReturn(connection);
    when(connection.storageRoot()).thenReturn(storage);
    when(connection.plugins()).thenReturn(plugins);
    when(plugins.getStoredPluginsByKey()).thenReturn(Map.of(plugin.getKey(), plugin));
    return new ConnectedResourceFetcher("conn", storageService, clients);
  }

  private StoredPlugin plugin(StaticResource resource) {
    return new StoredPlugin("javascript", "jar", storage.resolve("javascript.jar"), "folder", List.of(resource));
  }

  private static void answerWith(StaticFiles staticFiles, byte[] bytes) {
    answerWith(staticFiles, new ByteArrayInputStream(bytes));
  }

  private static void answerWith(StaticFiles staticFiles, InputStream stream) {
    doAnswer(invocation -> {
      Consumer<InputStream> consumer = invocation.getArgument(1);
      consumer.accept(stream);
      return null;
    }).when(staticFiles).get(any(), any(), any());
  }

  private ConnectedResourceFetcher fetcher(StaticResource resource, ConnectedResourceFetcher.Downloader downloader) {
    return fetcher(List.of(resource), downloader);
  }

  private ConnectedResourceFetcher fetcher(List<StaticResource> resources, ConnectedResourceFetcher.Downloader downloader) {
    var plugin = new StoredPlugin("javascript", "jar", storage.resolve("javascript.jar"), "folder", resources);
    return new ConnectedResourceFetcher("conn", () -> Map.of("javascript", plugin), downloader, storage);
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
