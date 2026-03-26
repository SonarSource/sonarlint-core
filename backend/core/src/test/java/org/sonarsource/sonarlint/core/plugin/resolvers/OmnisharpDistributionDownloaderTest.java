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
package org.sonarsource.sonarlint.core.plugin.resolvers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.event.OmnisharpDistributionChangedEvent;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OmnisharpDistributionDownloaderTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  private HttpClientProvider httpClientProvider;
  private ApplicationEventPublisher eventPublisher;
  private OnDemandPluginSignatureVerifier signatureVerifier;
  private List<OmnisharpDistributionChangedEvent> publishedEvents;

  @BeforeEach
  void setUp() {
    httpClientProvider = mock(HttpClientProvider.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    signatureVerifier = mock(OnDemandPluginSignatureVerifier.class);
    when(signatureVerifier.verify(any(Path.class), any(DownloadableArtifact.class))).thenReturn(true);
    publishedEvents = new CopyOnWriteArrayList<>();
    doAnswer(inv -> {
      publishedEvents.add(inv.getArgument(0, OmnisharpDistributionChangedEvent.class));
      return null;
    }).when(eventPublisher).publishEvent(any(OmnisharpDistributionChangedEvent.class));
  }

  @Test
  void should_not_trigger_downloads_when_csharp_not_enabled() {
    var downloader = buildDownloader(Set.of());
    downloader.triggerDownloads();

    verify(httpClientProvider, never()).getHttpClientWithoutAuth();
    assertThat(downloader.getMonoPath()).isNull();
    assertThat(downloader.getDotNet472Path()).isNull();
    assertThat(downloader.getDotNet6Path()).isNull();
  }

  @Test
  void should_trigger_downloads_when_csharp_enabled_in_standalone() throws IOException {
    mockSuccessfulHttpClient();
    var downloader = buildDownloader(Set.of(Language.CS));

    downloader.triggerDownloads();

    await().atMost(10, TimeUnit.SECONDS).until(() -> !publishedEvents.isEmpty());
    assertThat(downloader.getMonoPath()).isNotNull().satisfies(p -> assertThat(Files.isDirectory(p)).isTrue());
    assertThat(downloader.getDotNet6Path()).isNotNull().satisfies(p -> assertThat(Files.isDirectory(p)).isTrue());
    assertThat(downloader.getDotNet472Path()).isNotNull().satisfies(p -> assertThat(Files.isDirectory(p)).isTrue());
  }

  @Test
  void should_reuse_cached_directories_without_re_downloading() throws IOException {
    var version = DownloadableArtifact.OMNISHARP_MONO.version();
    var monoDir = tempDir.resolve("ondemand-plugins/omnisharp/mono").resolve(version);
    var net472Dir = tempDir.resolve("ondemand-plugins/omnisharp/net472").resolve(version);
    var net6Dir = tempDir.resolve("ondemand-plugins/omnisharp/net6").resolve(version);
    Files.createDirectories(monoDir);
    Files.createDirectories(net472Dir);
    Files.createDirectories(net6Dir);

    var downloader = buildDownloader(Set.of(Language.CS));
    downloader.triggerDownloads();

    verify(httpClientProvider, never()).getHttpClientWithoutAuth();
    assertThat(downloader.getMonoPath()).isEqualTo(monoDir);
    assertThat(downloader.getDotNet472Path()).isEqualTo(net472Dir);
    assertThat(downloader.getDotNet6Path()).isEqualTo(net6Dir);
    assertThat(publishedEvents).hasSize(1);
  }

  @Test
  void should_fire_event_only_after_all_three_variants_are_ready() throws IOException {
    mockSuccessfulHttpClient();
    var downloader = buildDownloader(Set.of(Language.CS));

    downloader.triggerDownloads();

    await().atMost(10, TimeUnit.SECONDS).until(() -> !publishedEvents.isEmpty());
    assertThat(publishedEvents).hasSize(1);
  }

  @Test
  void should_not_fire_event_when_a_download_fails() {
    var httpClient = mock(HttpClient.class);
    when(httpClient.get(anyString())).thenThrow(new RuntimeException("Connection refused"));
    when(httpClientProvider.getHttpClientWithoutAuth()).thenReturn(httpClient);

    var downloader = buildDownloader(Set.of(Language.CS));
    downloader.triggerDownloads();

    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
      assertThat(logTester.logs()).anyMatch(l -> l.contains("Failed to download OmniSharp")));
    assertThat(publishedEvents).isEmpty();
  }

  @Test
  void should_return_null_paths_before_downloads_complete() {
    var downloader = buildDownloader(Set.of(Language.CS));

    assertThat(downloader.getMonoPath()).isNull();
    assertThat(downloader.getDotNet472Path()).isNull();
    assertThat(downloader.getDotNet6Path()).isNull();
  }

  @Test
  void resolve_with_fallback_should_prefer_client_path_over_downloaded_path() {
    var downloaded = Path.of("/downloaded");
    var clientProvided = Path.of("/client");

    assertThat(OmnisharpDistributionDownloader.resolveWithFallback(downloaded, clientProvided)).isEqualTo(clientProvided);
  }

  @Test
  void resolve_with_fallback_should_use_downloaded_path_when_client_path_is_null() {
    var downloaded = Path.of("/downloaded");

    assertThat(OmnisharpDistributionDownloader.resolveWithFallback(downloaded, null)).isEqualTo(downloaded);
  }

  @Test
  void resolve_with_fallback_should_use_client_path_when_download_not_ready() {
    var clientProvided = Path.of("/client");

    assertThat(OmnisharpDistributionDownloader.resolveWithFallback(null, clientProvided)).isEqualTo(clientProvided);
  }

  @Test
  void resolve_with_fallback_should_return_null_when_both_paths_are_null() {
    assertThat(OmnisharpDistributionDownloader.resolveWithFallback(null, null)).isNull();
  }

  private OmnisharpDistributionDownloader buildDownloader(Set<Language> enabledLanguages) {
    var userPaths = mock(UserPaths.class);
    when(userPaths.getStorageRoot()).thenReturn(tempDir);
    var initializeParams = mock(InitializeParams.class);
    when(initializeParams.getEnabledLanguagesInStandaloneMode()).thenReturn(enabledLanguages);
    when(initializeParams.getExtraEnabledLanguagesInConnectedMode()).thenReturn(Set.of());
    return new OmnisharpDistributionDownloader(initializeParams, userPaths, httpClientProvider, eventPublisher,
      Executors.newCachedThreadPool(), signatureVerifier);
  }

  private void mockSuccessfulHttpClient() throws IOException {
    var tarGzBytes = createMinimalTarGz();
    var zipBytes = createMinimalZip();
    var httpClient = mock(HttpClient.class);
    when(httpClientProvider.getHttpClientWithoutAuth()).thenReturn(httpClient);
    when(httpClient.get(anyString())).thenAnswer(inv -> {
      var url = (String) inv.getArgument(0);
      var response = mock(HttpClient.Response.class);
      when(response.isSuccessful()).thenReturn(true);
      when(response.code()).thenReturn(200);
      if (url.endsWith(".zip")) {
        when(response.bodyAsStream()).thenReturn(new ByteArrayInputStream(zipBytes));
      } else {
        when(response.bodyAsStream()).thenReturn(new ByteArrayInputStream(tarGzBytes));
      }
      return response;
    });
  }

  private static byte[] createMinimalTarGz() throws IOException {
    var baos = new ByteArrayOutputStream();
    try (var gzip = new GzipCompressorOutputStream(baos);
      var tar = new TarArchiveOutputStream(gzip)) {
      var entry = new TarArchiveEntry("OmniSharp.exe");
      var content = "fake binary".getBytes(StandardCharsets.UTF_8);
      entry.setSize(content.length);
      tar.putArchiveEntry(entry);
      tar.write(content);
      tar.closeArchiveEntry();
      tar.finish();
    }
    return baos.toByteArray();
  }

  private static byte[] createMinimalZip() throws IOException {
    var baos = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(baos)) {
      var entry = new ZipEntry("OmniSharp.exe");
      zip.putNextEntry(entry);
      zip.write("fake binary".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return baos.toByteArray();
  }

}
