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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
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
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactLocation;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.plugin.source.AvailableArtifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BinariesArtifactSourceTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  private HttpClientProvider httpClientProvider;
  private BinariesSignatureVerifier signatureVerifier;
  private BinariesLocalCacheManager cacheManager;

  @BeforeEach
  void setUp() {
    httpClientProvider = mock(HttpClientProvider.class);
    signatureVerifier = mock(BinariesSignatureVerifier.class);
    cacheManager = mock(BinariesLocalCacheManager.class);
  }

  @Test
  void should_return_empty_when_no_artifact_handles_the_enabled_language() {
    var source = buildSource();

    assertThat(source.listAvailableArtifacts(EnumSet.of(SonarLanguage.JAVA))).isEmpty();
  }

  @Test
  void should_describe_missing_artifact_as_remote_with_url_as_deduplication_key() {
    var source = buildSource();

    var artifact = findArtifact(source, "cpp");
    var download = ((ArtifactLocation.Remote) artifact.location()).download();

    assertThat(download.deduplicationKey())
      .isEqualTo(String.format(BinariesArtifact.CFAMILY_PLUGIN.urlPattern(), BinariesArtifact.CFAMILY_PLUGIN.version()));
  }

  @Test
  void should_download_remote_artifact_synchronously() throws Exception {
    mockSuccessfulHttpClient();
    when(signatureVerifier.verify(any(Path.class), any(BinariesArtifact.class))).thenReturn(true);
    var source = buildSource();

    var local = remoteDownload(findArtifact(source, "cpp")).download();

    var artifactVersion = BinariesArtifact.CFAMILY_PLUGIN.version();
    var expectedPath = tempDir.resolve("ondemand-plugins").resolve("cpp").resolve(artifactVersion)
      .resolve("sonar-cpp-plugin-" + artifactVersion + ".jar");
    assertThat(local).isEqualTo(new ArtifactLocation.Local(expectedPath, ArtifactOrigin.ON_DEMAND, Version.create(artifactVersion)));
    assertThat(expectedPath).exists();
    verify(cacheManager).cleanupOldVersions(tempDir.resolve("ondemand-plugins").resolve("cpp"), artifactVersion);
  }

  @Test
  void should_propagate_download_errors() {
    var httpClient = mock(HttpClient.class);
    when(httpClient.get(anyString())).thenThrow(new RuntimeException("Connection refused"));
    when(httpClientProvider.getHttpClientWithoutAuth()).thenReturn(httpClient);
    var source = buildSource();

    assertThatThrownBy(() -> remoteDownload(findArtifact(source, "cpp")).download())
      .isInstanceOf(RuntimeException.class)
      .hasMessage("Connection refused");
  }

  @Test
  void should_fail_download_when_signature_verification_fails() throws Exception {
    mockSuccessfulHttpClient();
    when(signatureVerifier.verify(any(Path.class), any(BinariesArtifact.class))).thenReturn(false);
    var source = buildSource();

    assertThatThrownBy(() -> remoteDownload(findArtifact(source, "cpp")).download())
      .isInstanceOf(IOException.class)
      .hasMessage("Signature verification failed for cpp");
  }

  @Test
  void should_describe_non_empty_cached_archive_as_local() throws Exception {
    var source = buildSource();
    var artifactVersion = BinariesArtifact.OMNISHARP_MONO.version();
    var omnisharpDir = tempDir.resolve("ondemand-plugins").resolve("omnisharp-mono").resolve(artifactVersion);
    Files.createDirectories(omnisharpDir);
    Files.createFile(omnisharpDir.resolve("OmniSharp.exe"));

    var artifact = findArtifact(source, "omnisharp-mono");

    assertThat(artifact.location())
      .isEqualTo(new ArtifactLocation.Local(omnisharpDir, ArtifactOrigin.ON_DEMAND, Version.create(artifactVersion)));
    assertThat(source.getOmnisharpExtraProperties())
      .containsEntry("sonar.cs.internal.omnisharpMonoLocation", omnisharpDir.toString());
  }

  @Test
  void should_describe_empty_cached_archive_as_remote() throws Exception {
    var source = buildSource();
    var artifactVersion = BinariesArtifact.OMNISHARP_MONO.version();
    Files.createDirectories(tempDir.resolve("ondemand-plugins").resolve("omnisharp-mono").resolve(artifactVersion));

    assertThat(findArtifact(source, "omnisharp-mono").location()).isInstanceOf(ArtifactLocation.Remote.class);
  }

  @Test
  void should_return_entries_for_all_supported_artifacts() {
    var source = buildSource();

    var listed = source.listAvailableArtifacts(EnumSet.allOf(SonarLanguage.class));

    assertThat(listed).hasSize(5);
    assertThat(listed).extracting(AvailableArtifact::key)
      .containsOnly("cpp", "csharp", "omnisharp-mono", "omnisharp-net472", "omnisharp-net6");
  }

  private BinariesArtifactSource buildSource() {
    var userPaths = mock(UserPaths.class);
    when(userPaths.getStorageRoot()).thenReturn(tempDir);
    return new BinariesArtifactSource(userPaths, httpClientProvider, signatureVerifier, cacheManager);
  }

  private AvailableArtifact findArtifact(BinariesArtifactSource source, String key) {
    return source.listAvailableArtifacts(EnumSet.allOf(SonarLanguage.class)).stream()
      .filter(artifact -> artifact.key().equals(key))
      .findFirst()
      .orElseThrow();
  }

  private static org.sonarsource.sonarlint.core.plugin.source.ArtifactDownload remoteDownload(AvailableArtifact artifact) {
    return ((ArtifactLocation.Remote) artifact.location()).download();
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

  private static byte[] createMinimalPluginJarBytes(String pluginKey, String pluginVersion) throws IOException {
    var tempJar = Files.createTempFile("test-plugin", ".jar");
    try {
      var manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
      manifest.getMainAttributes().putValue("Plugin-Key", pluginKey);
      manifest.getMainAttributes().putValue("Plugin-Version", pluginVersion);
      try (var ignored = new JarOutputStream(Files.newOutputStream(tempJar), manifest)) {
        // Minimal JAR with only the manifest
      }
      return Files.readAllBytes(tempJar);
    } finally {
      Files.deleteIfExists(tempJar);
    }
  }
}
