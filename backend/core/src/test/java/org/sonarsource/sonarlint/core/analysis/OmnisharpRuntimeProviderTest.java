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
package org.sonarsource.sonarlint.core.analysis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.plugin.resolvers.DownloadableArtifact;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.OmnisharpRequirementsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OmnisharpRuntimeProviderTest {

  private static final List<DownloadableArtifact> OMNISHARP_ARTIFACTS = List.of(
    DownloadableArtifact.OMNISHARP_MONO, DownloadableArtifact.OMNISHARP_NET472, DownloadableArtifact.OMNISHARP_NET6);

  private static final Path MONO_PATH = Paths.get("/omnisharp/mono");
  private static final Path WIN_PATH = Paths.get("/omnisharp/win");
  private static final Path NET6_PATH = Paths.get("/omnisharp/net6");

  @TempDir
  Path defaultStorageRoot;

  private InitializeParams initializeParams;
  private HttpClientProvider httpClientProvider;
  private UserPaths userPaths;

  @BeforeEach
  void setUp() {
    initializeParams = mock(InitializeParams.class);
    httpClientProvider = mock(HttpClientProvider.class);
    userPaths = mock(UserPaths.class);
    when(userPaths.getStorageRoot()).thenReturn(defaultStorageRoot);
  }

  @Test
  void should_return_empty_when_neither_cs_nor_vbnet_enabled() {
    when(initializeParams.getEnabledLanguagesInStandaloneMode()).thenReturn(Set.of(Language.JAVA));

    var result = new OmnisharpRuntimeProvider(initializeParams, httpClientProvider, userPaths).getPaths();

    assertThat(result).isEmpty();
  }

  @Test
  void should_return_embedded_paths_when_requirements_present() {
    mockEnabledLanguages(Language.CS);
    var requirements = mockRequirements(MONO_PATH, WIN_PATH, NET6_PATH);
    mockLanguageSpecificRequirements(requirements);

    var result = new OmnisharpRuntimeProvider(initializeParams, httpClientProvider, userPaths).getPaths();

    var expected = Map.of(
      "sonar.cs.internal.omnisharpMonoLocation", MONO_PATH,
      "sonar.cs.internal.omnisharpWinLocation", WIN_PATH,
      "sonar.cs.internal.omnisharpNet6Location", NET6_PATH);
    assertThat(result).containsExactlyInAnyOrderEntriesOf(expected);
  }

  @Test
  void should_return_empty_when_omnisharp_requirements_dto_is_null() {
    mockEnabledLanguages(Language.CS);
    var lsr = mock(LanguageSpecificRequirements.class);
    when(lsr.getOmnisharpRequirements()).thenReturn(null);
    when(initializeParams.getLanguageSpecificRequirements()).thenReturn(lsr);
    var httpClient = mock(HttpClient.class);
    when(httpClientProvider.getHttpClientWithoutAuth()).thenReturn(httpClient);

    var result = new OmnisharpRuntimeProvider(initializeParams, httpClientProvider, userPaths).getPaths();

    verify(httpClient, never()).get(anyString());
    assertThat(result).isEmpty();
  }

  @Test
  void should_download_and_extract_runtimes_when_requirements_dto_has_null_paths() {
    mockEnabledLanguages(Language.CS);
    var lsr = mock(LanguageSpecificRequirements.class);
    when(lsr.getOmnisharpRequirements()).thenReturn(mock(OmnisharpRequirementsDto.class));
    when(initializeParams.getLanguageSpecificRequirements()).thenReturn(lsr);

    var httpClient = mock(HttpClient.class);
    when(httpClientProvider.getHttpClientWithoutAuth()).thenReturn(httpClient);
    when(httpClient.get(anyString())).thenAnswer(inv -> {
      var response = mock(HttpClient.Response.class);
      when(response.isSuccessful()).thenReturn(true);
      when(response.bodyAsStream()).thenReturn(createMinimalTarGz());
      return response;
    });

    var result = new OmnisharpRuntimeProvider(initializeParams, httpClientProvider, userPaths).getPaths();

    verify(httpClient).get("https://binaries.sonarsource.com/OmniSharp-Roslyn/1.39.15/omnisharp-mono.tar.gz");
    verify(httpClient).get("https://binaries.sonarsource.com/OmniSharp-Roslyn/1.39.15/omnisharp-net472.tar.gz");
    verify(httpClient).get("https://binaries.sonarsource.com/OmniSharp-Roslyn/1.39.15/omnisharp-net6.0.tar.gz");
    assertThat(result).hasSize(3);
    assertThat(result.values()).allMatch(Files::isDirectory);
  }

  @Test
  void should_skip_download_and_extraction_when_extracted_dir_has_files() throws IOException {
    mockEnabledLanguages(Language.CS);
    mockLanguageSpecificRequirements(mock(OmnisharpRequirementsDto.class));
    prepareCache(defaultStorageRoot,true);
    var httpClient = mock(HttpClient.class);
    when(httpClientProvider.getHttpClientWithoutAuth()).thenReturn(httpClient);

    var result = new OmnisharpRuntimeProvider(initializeParams, httpClientProvider, userPaths).getPaths();

    verify(httpClient, never()).get(anyString());
    assertThat(result).hasSize(3);
  }

  @Test
  void should_skip_download_but_re_extract_when_extracted_dir_is_empty() throws IOException {
    mockEnabledLanguages(Language.CS);
    mockLanguageSpecificRequirements(mock(OmnisharpRequirementsDto.class));
    prepareCache(defaultStorageRoot,false);
    var httpClient = mock(HttpClient.class);
    when(httpClientProvider.getHttpClientWithoutAuth()).thenReturn(httpClient);

    var result = new OmnisharpRuntimeProvider(initializeParams, httpClientProvider, userPaths).getPaths();

    verify(httpClient, never()).get(anyString());
    assertThat(result).hasSize(3);
    assertThat(result.values()).allSatisfy(p -> assertThat(p).isNotEmptyDirectory());
  }

  @Test
  void should_cache_result_across_multiple_calls() {
    mockEnabledLanguages(Language.CS);
    var requirements = mockRequirements(MONO_PATH, WIN_PATH, NET6_PATH);
    mockLanguageSpecificRequirements(requirements);

    var underTest = new OmnisharpRuntimeProvider(initializeParams, httpClientProvider, userPaths);
    underTest.getPaths();
    underTest.getPaths();

    verify(requirements, times(1)).getMonoDistributionPath();
  }

  private void mockEnabledLanguages(Language... languages) {
    when(initializeParams.getEnabledLanguagesInStandaloneMode()).thenReturn(Set.of(languages));
  }

  private static OmnisharpRequirementsDto mockRequirements(Path mono, Path win, Path net6) {
    var requirements = mock(OmnisharpRequirementsDto.class);
    when(requirements.getMonoDistributionPath()).thenReturn(mono);
    when(requirements.getDotNet472DistributionPath()).thenReturn(win);
    when(requirements.getDotNet6DistributionPath()).thenReturn(net6);
    return requirements;
  }

  private void mockLanguageSpecificRequirements(OmnisharpRequirementsDto requirements) {
    var lsr = mock(LanguageSpecificRequirements.class);
    when(lsr.getOmnisharpRequirements()).thenReturn(requirements);
    when(initializeParams.getLanguageSpecificRequirements()).thenReturn(lsr);
  }

  private static void prepareCache(Path storageRoot, boolean populateExtractedDir) throws IOException {
    for (var artifact : OMNISHARP_ARTIFACTS) {
      var artifactDir = storageRoot.resolve("omnisharp-runtimes").resolve(artifact.artifactKey()).resolve(artifact.version());
      Files.createDirectories(artifactDir);
      Files.copy(createMinimalTarGz(), artifactDir.resolve(artifact.artifactKey() + ".tar.gz"));
      var extractedDir = artifactDir.resolve("extracted");
      Files.createDirectories(extractedDir);
      if (populateExtractedDir) {
        Files.createFile(extractedDir.resolve("placeholder.txt"));
      }
    }
  }

  private static InputStream createMinimalTarGz() throws IOException {
    var result = new ByteArrayOutputStream();
    try (var outputStream = new GzipCompressorOutputStream(result);
      var tarOutputStream = new TarArchiveOutputStream(outputStream)) {
      var entry = new TarArchiveEntry("placeholder.txt");
      entry.setSize(0);
      tarOutputStream.putArchiveEntry(entry);
      tarOutputStream.closeArchiveEntry();
    }
    return new ByteArrayInputStream(result.toByteArray());
  }
}
