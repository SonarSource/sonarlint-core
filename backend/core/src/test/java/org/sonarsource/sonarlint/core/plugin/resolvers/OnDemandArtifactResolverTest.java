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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
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
import org.sonarsource.sonarlint.core.plugin.ArtifactSource;
import org.sonarsource.sonarlint.core.plugin.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.PluginStatus;
import org.sonarsource.sonarlint.core.plugin.ResolvedArtifact;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnDemandArtifactResolverTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  private HttpClientProvider httpClientProvider;
  private ApplicationEventPublisher eventPublisher;
  private List<PluginStatus> capturedStatuses;
  private OnDemandPluginSignatureVerifier signatureVerifier;

  @BeforeEach
  void setUp() {
    httpClientProvider = mock(HttpClientProvider.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    signatureVerifier = mock(OnDemandPluginSignatureVerifier.class);
    capturedStatuses = new CopyOnWriteArrayList<>();
    doAnswer(inv -> {
      capturedStatuses.addAll(inv.getArgument(0, PluginStatusUpdateEvent.class).newStatuses());
      return null;
    }).when(eventPublisher).publishEvent(any(PluginStatusUpdateEvent.class));
  }

  @Test
  void should_return_empty_when_language_not_registered() {
    var resolver = buildResolver();

    var result = resolver.resolve(SonarLanguage.JAVA, null);

    assertThat(result).isEmpty();
  }

  @Test
  void should_return_downloading_on_first_async_call() {
    var proceedLatch = new CountDownLatch(1);
    mockBlockingHttpClient(proceedLatch);
    var resolver = buildResolver();
    try {
      var result = resolver.resolve(SonarLanguage.C, null);

      assertThat(result).contains(downloading());
    } finally {
      proceedLatch.countDown();
      // wait for the async task to finish all file I/O before JUnit cleans up @TempDir
      await().atMost(5, TimeUnit.SECONDS).until(() -> !capturedStatuses.isEmpty());
    }
  }

  @Test
  void should_return_downloading_while_same_artifact_is_in_progress() {
    var proceedLatch = new CountDownLatch(1);
    mockBlockingHttpClient(proceedLatch);
    var resolver = buildResolver();
    try {
      resolver.resolve(SonarLanguage.C, null);  // starts download, key added to inProgress

      var result = resolver.resolve(SonarLanguage.C, null);  // while download is blocked

      assertThat(result).contains(downloading());
    } finally {
      proceedLatch.countDown();
      // wait for the async task to finish all file I/O before JUnit cleans up @TempDir
      await().atMost(5, TimeUnit.SECONDS).until(() -> !capturedStatuses.isEmpty());
    }
  }

  @Test
  void should_fire_failed_event_on_async_download_error() {
    var httpClient = mock(HttpClient.class);
    when(httpClient.get(anyString())).thenThrow(new RuntimeException("Connection refused"));
    when(httpClientProvider.getHttpClientWithoutAuth()).thenReturn(httpClient);
    var resolver = buildResolver();

    resolver.resolve(SonarLanguage.C, null);

    await().atMost(5, TimeUnit.SECONDS).until(() -> capturedStatuses.size() == 3);
    assertThat(capturedStatuses).containsExactlyInAnyOrder(
      failedStatus(SonarLanguage.C),
      failedStatus(SonarLanguage.CPP),
      failedStatus(SonarLanguage.OBJC));
  }

  @Test
  void should_fire_failed_event_when_signature_verification_fails() throws Exception {
    mockSuccessfulHttpClient();
    when(signatureVerifier.verify(any(Path.class), any(DownloadableArtifact.class))).thenReturn(false);
    var resolver = buildResolver();

    resolver.resolve(SonarLanguage.C, null);

    await().atMost(5, TimeUnit.SECONDS).until(() -> capturedStatuses.size() == 3);
    assertThat(capturedStatuses).containsExactlyInAnyOrder(
      failedStatus(SonarLanguage.C),
      failedStatus(SonarLanguage.CPP),
      failedStatus(SonarLanguage.OBJC));
  }

  @Test
  void should_fire_active_event_covering_all_languages_on_successful_async_download() throws Exception {
    mockSuccessfulHttpClient();
    when(signatureVerifier.verify(any(Path.class), any(DownloadableArtifact.class))).thenReturn(true);
    var resolver = buildResolver();

    resolver.resolve(SonarLanguage.C, null);

    await().atMost(10, TimeUnit.SECONDS).until(() -> capturedStatuses.size() == 3);
    var artifactVersion = DownloadableArtifact.CFAMILY_PLUGIN.version();
    var pluginPath = tempDir.resolve("ondemand-plugins").resolve("cpp").resolve(artifactVersion)
      .resolve("sonar-cpp-plugin-" + artifactVersion + ".jar");
    assertThat(capturedStatuses).containsExactlyInAnyOrder(
      activeStatus(SonarLanguage.C, pluginPath),
      activeStatus(SonarLanguage.CPP, pluginPath),
      activeStatus(SonarLanguage.OBJC, pluginPath));
  }

  private OnDemandArtifactResolver buildResolver() {
    var userPaths = mock(UserPaths.class);
    when(userPaths.getStorageRoot()).thenReturn(tempDir);
    return new OnDemandArtifactResolver(userPaths, httpClientProvider, eventPublisher, Executors.newCachedThreadPool(), signatureVerifier);
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
    return new ResolvedArtifact(ArtifactState.DOWNLOADING, null, null, null);
  }

  private static PluginStatus activeStatus(SonarLanguage lang, Path path) {
    return PluginStatus.forLanguage(lang, ArtifactState.ACTIVE, ArtifactSource.ON_DEMAND,
      Version.create(DownloadableArtifact.CFAMILY_PLUGIN.version()), null, path, null);
  }

  private static PluginStatus failedStatus(SonarLanguage lang) {
    return PluginStatus.forLanguage(lang, ArtifactState.FAILED, null, null, null, null, null);
  }

}
