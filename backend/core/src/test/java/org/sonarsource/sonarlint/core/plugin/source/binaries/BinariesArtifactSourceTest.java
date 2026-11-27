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
package org.sonarsource.sonarlint.core.plugin.source.binaries;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.event.PluginStatusUpdateEvent;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.plugin.PluginStatus;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.source.AvailableArtifact;
import org.sonarsource.sonarlint.core.plugin.source.PendingDownload;
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BinariesArtifactSourceTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  private HttpClientProvider httpClientProvider;
  private ApplicationEventPublisher eventPublisher;
  private List<PluginStatus> capturedStatuses;
  private BinariesSignatureVerifier signatureVerifier;
  private BinariesLocalCacheManager cacheManager;

  @BeforeEach
  void setUp() {
    httpClientProvider = mock(HttpClientProvider.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    signatureVerifier = mock(BinariesSignatureVerifier.class);
    cacheManager = mock(BinariesLocalCacheManager.class);
    capturedStatuses = new CopyOnWriteArrayList<>();
    doAnswer(inv -> {
      capturedStatuses.addAll(inv.getArgument(0, PluginStatusUpdateEvent.class).newStatuses());
      return null;
    }).when(eventPublisher).publishEvent(any(PluginStatusUpdateEvent.class));
  }

  @Test
  void load_should_return_empty_when_plugin_key_not_handled() {
    var source = buildSource();

    var result = source.load(Set.of("java"));

    assertThat(result.resolvedArtifactsByKey()).doesNotContainKey("java");
  }

  @Test
  void load_should_return_downloading_on_first_async_call_for_cfamily() {
    var executor = mock(ExecutorService.class);
    var source = buildSource(executor);

    var result = source.load(Set.of("cpp"));

    verifyNoInteractions(executor, httpClientProvider);
    assertThat(result.pendingDownloads()).hasSize(1);
    var pending = result.pendingDownloads().getFirst();
    assertThat(pending.artifactKey()).isEqualTo("cpp");
    var future = pending.starter().get();
    verify(executor).execute(any(Runnable.class));
    assertThat(future).isNotDone();
    assertThat(result.resolvedArtifactsByKey().get("cpp"))
      .usingRecursiveComparison()
      .isEqualTo(downloading());
  }

  @Test
  void load_should_return_downloading_while_same_artifact_is_in_progress() {
    var proceedLatch = new CountDownLatch(1);
    mockBlockingHttpClient(proceedLatch);
    var source = buildSource();
    try {
      var running = source.load(Set.of("cpp")).pendingDownloads().getFirst().starter().get();

      var result = source.load(Set.of("cpp"));
      assertThat(result.pendingDownloads().getFirst().starter().get()).isSameAs(running);

      assertThat(result.resolvedArtifactsByKey().get("cpp"))
        .usingRecursiveComparison()
        .isEqualTo(downloading());
    } finally {
      proceedLatch.countDown();
      await().atMost(5, TimeUnit.SECONDS).until(() -> !capturedStatuses.isEmpty());
    }
  }

  @Test
  void load_should_fire_failed_event_on_async_download_error() {
    var httpClient = mock(HttpClient.class);
    when(httpClient.get(anyString())).thenThrow(new RuntimeException("Connection refused"));
    when(httpClientProvider.getHttpClientWithoutAuth()).thenReturn(httpClient);
    var source = buildSource();

    var result = source.load(Set.of("cpp"));
    var downloadFuture = result.pendingDownloads().getFirst().starter().get();

    await().atMost(5, TimeUnit.SECONDS).until(() -> capturedStatuses.size() == 3);
    assertThat(capturedStatuses).containsExactlyInAnyOrder(
      failedStatus(SonarLanguage.C),
      failedStatus(SonarLanguage.CPP),
      failedStatus(SonarLanguage.OBJC));
    await().atMost(5, TimeUnit.SECONDS).until(downloadFuture::isCompletedExceptionally);
  }

  @Test
  void load_should_fire_failed_event_when_signature_verification_fails() throws Exception {
    mockSuccessfulHttpClient();
    when(signatureVerifier.verify(any(Path.class), any(BinariesArtifact.class))).thenReturn(false);
    var source = buildSource();

    var result = source.load(Set.of("cpp"));
    var downloadFuture = result.pendingDownloads().getFirst().starter().get();

    await().atMost(5, TimeUnit.SECONDS).until(() -> capturedStatuses.size() == 3);
    assertThat(capturedStatuses).containsExactlyInAnyOrder(
      failedStatus(SonarLanguage.C),
      failedStatus(SonarLanguage.CPP),
      failedStatus(SonarLanguage.OBJC));
    await().atMost(5, TimeUnit.SECONDS).until(downloadFuture::isCompletedExceptionally);
  }

  /**
   * Regression test for the infinite plugin reload loop triggered by a failed on-demand download.
   *
   * The bug: downloadAndFireEvent() swallowed exceptions, completing the download future normally.
   * whenAllArtifactsDownloaded therefore always published PluginsSynchronizedEvent, which
   * triggered ResetPluginsCommand -> cache clear -> new download -> infinite loop.
   *
   * The fix: rethrow the exception so the future completes exceptionally, preventing
   * PluginsSynchronizedEvent from being published and stopping the loop.
   */
  @Test
  void failed_download_future_completes_exceptionally_preventing_plugin_reload_loop() {
    var httpClient = mock(HttpClient.class);
    when(httpClient.get(anyString())).thenThrow(new RuntimeException("Connection refused"));
    when(httpClientProvider.getHttpClientWithoutAuth()).thenReturn(httpClient);
    var source = buildSource();

    var loadResult = source.load(Set.of("cpp"));
    var downloadFuture = loadResult.pendingDownloads().getFirst().starter().get();

    await().atMost(5, TimeUnit.SECONDS).until(downloadFuture::isDone);
    assertThat(downloadFuture).isCompletedExceptionally();
  }

  @Test
  void load_should_fire_active_event_covering_all_languages_on_successful_async_download() throws Exception {
    mockSuccessfulHttpClient();
    when(signatureVerifier.verify(any(Path.class), any(BinariesArtifact.class))).thenReturn(true);
    var source = buildSource();

    source.load(Set.of("cpp")).pendingDownloads().forEach(BinariesArtifactSourceTest::startDownload);

    await().atMost(10, TimeUnit.SECONDS).until(() -> capturedStatuses.size() == 3);
    var artifactVersion = BinariesArtifact.CFAMILY_PLUGIN.version();
    var pluginPath = tempDir.resolve("ondemand-plugins").resolve("cpp").resolve(artifactVersion)
      .resolve("sonar-cpp-plugin-" + artifactVersion + ".jar");
    assertThat(capturedStatuses).containsExactlyInAnyOrder(
      activeStatus(SonarLanguage.C, pluginPath),
      activeStatus(SonarLanguage.CPP, pluginPath),
      activeStatus(SonarLanguage.OBJC, pluginPath));
    // cleanupOldVersions must receive the artifact-key directory (.../ondemand-plugins/cpp/),
    // not the version directory or the JAR's parent
    verify(cacheManager).cleanupOldVersions(
      tempDir.resolve("ondemand-plugins").resolve("cpp"),
      artifactVersion);
  }

  @Test
  void load_should_return_active_on_warm_startup_when_omnisharp_directory_exists_and_is_non_empty() throws Exception {
    var source = buildSource();
    var artifactVersion = BinariesArtifact.OMNISHARP_MONO.version();
    var omnisharpDir = tempDir.resolve("ondemand-plugins").resolve("omnisharp-mono").resolve(artifactVersion);
    Files.createDirectories(omnisharpDir);
    Files.createFile(omnisharpDir.resolve("OmniSharp.exe"));

    var result = source.load(Set.of("omnisharp-mono"));

    assertThat(result.resolvedArtifactsByKey().get("omnisharp-mono"))
      .usingRecursiveComparison()
      .isEqualTo(new ResolvedArtifact(ArtifactState.ACTIVE, omnisharpDir, ArtifactOrigin.ON_DEMAND, Version.create(artifactVersion)));
  }

  @Test
  void load_should_re_download_when_omnisharp_directory_is_empty() throws Exception {
    var proceedLatch = new CountDownLatch(1);
    mockBlockingHttpClient(proceedLatch);
    var source = buildSource();
    var artifactVersion = BinariesArtifact.OMNISHARP_MONO.version();
    var omnisharpDir = tempDir.resolve("ondemand-plugins").resolve("omnisharp-mono").resolve(artifactVersion);
    Files.createDirectories(omnisharpDir);

    try {
      var result = source.load(Set.of("omnisharp-mono"));
      result.pendingDownloads().forEach(BinariesArtifactSourceTest::startDownload);

      assertThat(result.resolvedArtifactsByKey().get("omnisharp-mono"))
        .usingRecursiveComparison()
        .isEqualTo(downloading());
    } finally {
      proceedLatch.countDown();
      await().atMost(5, TimeUnit.SECONDS).until(() -> !capturedStatuses.isEmpty());
    }
  }

  @Test
  void list_AvailablePlugins_should_return_entries_for_all_plugins() throws Exception {
    mockSuccessfulHttpClient();
    when(signatureVerifier.verify(any(Path.class), any(BinariesArtifact.class))).thenReturn(true);
    var source = buildSource();

    var listed = source.listAvailableArtifacts(EnumSet.allOf(SonarLanguage.class));
    // Only one unique plugin key for C-family: "cpp"
    assertThat(listed).hasSize(7);
    assertThat(listed)
      .extracting(AvailableArtifact::key)
      .containsOnly("cpp", "csharp", "omnisharp-mono", "omnisharp-net472", "omnisharp-net6", "javascript", "python");
  }

  @Test
  void load_should_reuse_a_verified_exploded_plugin_and_its_manifest() throws Exception {
    when(signatureVerifier.verify(any(Path.class), any(BinariesArtifact.class))).thenReturn(true);
    var source = buildSource();
    var version = BinariesArtifact.JAVASCRIPT.version();
    var jarPath = tempDir.resolve("ondemand-plugins").resolve("javascript").resolve(version).resolve("plugin")
      .resolve(BinariesArtifact.JAVASCRIPT.jarFileName());
    Files.createDirectories(jarPath.getParent());
    Files.writeString(jarPath, "cached-jar");
    Files.writeString(jarPath.getParent().getParent().resolve(BinariesArtifact.MANIFEST_FILE_NAME), "{}");

    var result = source.load(Set.of("javascript"));

    assertThat(result.pendingDownloads()).isEmpty();
    assertThat(result.resolvedArtifactsByKey().get("javascript"))
      .usingRecursiveComparison()
      .isEqualTo(new ResolvedArtifact(ArtifactState.ACTIVE, jarPath, ArtifactOrigin.ON_DEMAND, Version.create(version)));
    verifyNoInteractions(httpClientProvider);
  }

  @Test
  void load_should_download_again_when_the_exploded_manifest_is_missing() throws Exception {
    var requestedUrls = new CopyOnWriteArrayList<String>();
    mockExplodedDownloads(requestedUrls, true, true);
    when(signatureVerifier.verify(any(Path.class), any(BinariesArtifact.class))).thenReturn(true);
    var source = buildSource(Executors.newCachedThreadPool(), new BinariesLocalCacheManager());
    var version = BinariesArtifact.PYTHON.version();
    var jarPath = tempDir.resolve("ondemand-plugins").resolve("python").resolve(version).resolve("plugin")
      .resolve(BinariesArtifact.PYTHON.jarFileName());
    Files.createDirectories(jarPath.getParent());
    Files.writeString(jarPath, "stale-jar");

    var result = source.load(Set.of("python"));
    assertThat(result.pendingDownloads()).hasSize(1);
    result.pendingDownloads().forEach(BinariesArtifactSourceTest::startDownload);

    var manifestPath = jarPath.getParent().getParent().resolve(BinariesArtifact.MANIFEST_FILE_NAME);
    await().atMost(10, TimeUnit.SECONDS).until(() -> Files.isRegularFile(manifestPath));
    assertThat(jarPath).content().isEqualTo("jar-bytes");
    assertThat(requestedUrls).contains(BinariesArtifact.PYTHON.manifestUrl());
  }

  @Test
  void load_should_store_exploded_plugin_and_apply_retention_cleanup() throws Exception {
    var requestedUrls = new CopyOnWriteArrayList<String>();
    mockExplodedDownloads(requestedUrls, true, true);
    when(signatureVerifier.verify(any(Path.class), any(BinariesArtifact.class))).thenReturn(true);
    var source = buildSource(Executors.newCachedThreadPool(), new BinariesLocalCacheManager());
    var version = BinariesArtifact.JAVASCRIPT.version();
    var pluginDirectory = tempDir.resolve("ondemand-plugins").resolve("javascript");
    var previous = pluginDirectory.resolve("0.9");
    var expired = pluginDirectory.resolve("0.8");
    Files.createDirectories(previous);
    Files.createDirectories(expired);
    Files.writeString(previous.resolve("previous.txt"), "within-retention");
    Files.setLastModifiedTime(expired, FileTime.from(Instant.now().minus(61, ChronoUnit.DAYS)));

    source.load(Set.of("javascript")).pendingDownloads().forEach(BinariesArtifactSourceTest::startDownload);

    var jarPath = pluginDirectory.resolve(version).resolve("plugin").resolve("sonar-javascript-plugin-" + version + ".jar");
    var manifestPath = pluginDirectory.resolve(version).resolve(BinariesArtifact.MANIFEST_FILE_NAME);
    await().atMost(10, TimeUnit.SECONDS).until(() -> capturedStatuses.size() == BinariesArtifact.JAVASCRIPT.getLanguages().size());
    assertThat(requestedUrls).containsExactly(BinariesArtifact.JAVASCRIPT.downloadUrl(), BinariesArtifact.JAVASCRIPT.manifestUrl());
    assertThat(jarPath).isRegularFile();
    assertThat(manifestPath).content().isEqualTo("{\"resources\":[]}");
    assertThat(previous).isDirectory();
    assertThat(expired).doesNotExist();
    assertThat(capturedStatuses).allSatisfy(status -> {
      assertThat(status.state()).isEqualTo(ArtifactState.ACTIVE);
      assertThat(status.path()).isEqualTo(jarPath);
    });
  }

  @Test
  void load_should_keep_previous_version_when_exploded_download_fails() throws Exception {
    mockExplodedDownloads(new CopyOnWriteArrayList<>(), true, false);
    var source = buildSource(Executors.newCachedThreadPool(), new BinariesLocalCacheManager());
    var previous = tempDir.resolve("ondemand-plugins").resolve("javascript").resolve("0.9");
    Files.createDirectories(previous);
    Files.writeString(previous.resolve("previous.txt"), "still-here");
    Files.setLastModifiedTime(previous, FileTime.from(Instant.now().minus(61, ChronoUnit.DAYS)));

    var downloadFuture = source.load(Set.of("javascript")).pendingDownloads().getFirst().starter().get();

    await().atMost(5, TimeUnit.SECONDS).until(downloadFuture::isCompletedExceptionally);
    assertThat(previous).isDirectory();
    assertThat(tempDir.resolve("ondemand-plugins").resolve("javascript").resolve(BinariesArtifact.JAVASCRIPT.version())
      .resolve("plugin")).isEmptyDirectory();
  }

  @Test
  void load_should_keep_previous_version_when_exploded_signature_check_fails() throws Exception {
    mockExplodedDownloads(new CopyOnWriteArrayList<>(), true, true);
    when(signatureVerifier.verify(any(Path.class), any(BinariesArtifact.class))).thenReturn(false);
    var source = buildSource(Executors.newCachedThreadPool(), new BinariesLocalCacheManager());
    var previous = tempDir.resolve("ondemand-plugins").resolve("python").resolve("1.0");
    Files.createDirectories(previous);
    Files.setLastModifiedTime(previous, FileTime.from(Instant.now().minus(61, ChronoUnit.DAYS)));

    var downloadFuture = source.load(Set.of("python")).pendingDownloads().getFirst().starter().get();

    await().atMost(5, TimeUnit.SECONDS).until(downloadFuture::isCompletedExceptionally);
    assertThat(previous).isDirectory();
    var versionDir = tempDir.resolve("ondemand-plugins").resolve("python").resolve(BinariesArtifact.PYTHON.version());
    assertThat(versionDir.resolve("plugin")).isEmptyDirectory();
    assertThat(versionDir.resolve(BinariesArtifact.MANIFEST_FILE_NAME)).doesNotExist();
  }

  private static void startDownload(PendingDownload download) {
    download.starter().get();
  }

  private BinariesArtifactSource buildSource() {
    return buildSource(Executors.newCachedThreadPool());
  }

  private BinariesArtifactSource buildSource(ExecutorService executor) {
    return buildSource(executor, cacheManager);
  }

  private BinariesArtifactSource buildSource(ExecutorService executor, BinariesLocalCacheManager binariesLocalCacheManager) {
    var userPaths = mock(UserPaths.class);
    when(userPaths.getStorageRoot()).thenReturn(tempDir);
    return new BinariesArtifactSource(userPaths, httpClientProvider, eventPublisher, executor, signatureVerifier, binariesLocalCacheManager);
  }

  private void mockExplodedDownloads(List<String> requestedUrls, boolean jarSucceeds, boolean manifestSucceeds) {
    var httpClient = mock(HttpClient.class);
    when(httpClient.get(anyString())).thenAnswer(invocation -> {
      var url = invocation.getArgument(0, String.class);
      requestedUrls.add(url);
      var manifest = url.endsWith("/" + BinariesArtifact.MANIFEST_FILE_NAME);
      var response = mock(HttpClient.Response.class);
      var success = manifest ? manifestSucceeds : jarSucceeds;
      when(response.isSuccessful()).thenReturn(success);
      when(response.code()).thenReturn(success ? 200 : 404);
      var body = manifest ? "{\"resources\":[]}" : "jar-bytes";
      when(response.bodyAsStream()).thenAnswer(ignored -> new ByteArrayInputStream(body.getBytes()));
      return response;
    });
    when(httpClientProvider.getHttpClientWithoutAuth()).thenReturn(httpClient);
  }

  private void mockSuccessfulHttpClient() throws Exception {
    var jarBytes = createMinimalPluginJarBytes("cpp", "1.0.0");
    var httpClient = mock(HttpClient.class);
    var response = mock(HttpClient.Response.class);
    when(response.code()).thenReturn(200);
    when(response.isSuccessful()).thenReturn(true);
    when(response.bodyAsStream()).thenReturn(new ByteArrayInputStream(jarBytes));
    when(httpClient.get(anyString())).thenReturn(response);
    when(httpClientProvider.getHttpClientWithoutAuth()).thenReturn(httpClient);
  }

  private void mockBlockingHttpClient(CountDownLatch proceedLatch) {
    var httpClient = mock(HttpClient.class);
    var response = mock(HttpClient.Response.class);
    when(response.isSuccessful()).thenReturn(true);
    when(response.code()).thenReturn(200);
    when(response.bodyAsStream()).thenAnswer(inv -> awaitAndReturnEmpty(proceedLatch));
    when(httpClient.get(anyString())).thenReturn(response);
    when(httpClientProvider.getHttpClientWithoutAuth()).thenReturn(httpClient);
  }

  private static InputStream awaitAndReturnEmpty(CountDownLatch latch) throws InterruptedException {
    latch.await();
    return InputStream.nullInputStream();
  }

  private static byte[] createMinimalPluginJarBytes(String pluginKey, String pluginVersion) throws IOException {
    var tempJar = Files.createTempFile("test-plugin", ".jar");
    try {
      var manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
      manifest.getMainAttributes().putValue("Plugin-Key", pluginKey);
      manifest.getMainAttributes().putValue("Plugin-Version", pluginVersion);
      try (var jos = new JarOutputStream(Files.newOutputStream(tempJar), manifest)) {
        // minimal JAR with only the manifest
      }
      return Files.readAllBytes(tempJar);
    } finally {
      Files.deleteIfExists(tempJar);
    }
  }

  private static ResolvedArtifact downloading() {
    return new ResolvedArtifact(ArtifactState.DOWNLOADING, null, ArtifactOrigin.ON_DEMAND, null);
  }

  private static PluginStatus activeStatus(SonarLanguage lang, Path path) {
    return PluginStatus.forLanguage(lang, ArtifactState.ACTIVE, ArtifactOrigin.ON_DEMAND,
      Version.create(BinariesArtifact.CFAMILY_PLUGIN.version()), null, path, null);
  }

  private static PluginStatus failedStatus(SonarLanguage lang) {
    return PluginStatus.forLanguage(lang, ArtifactState.FAILED, null, null, null, null, null);
  }
}
