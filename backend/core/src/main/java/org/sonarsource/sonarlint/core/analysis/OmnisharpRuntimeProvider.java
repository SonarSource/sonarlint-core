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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.file.PathUtils;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.plugin.resolvers.DownloadableArtifact;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.OmnisharpRequirementsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.serverconnection.storage.TarGzUtils;

/**
 * Provides OmniSharp runtime folder paths for analysis configuration.
 *
 * <p>Paths are resolved lazily on first use and cached for the lifetime of the bean.
 * Embedded paths from {@link OmnisharpRequirementsDto} take priority; when absent,
 * the runtimes are downloaded synchronously and extracted to the local cache.</p>
 *
 * TODO signature verification
 */
public class OmnisharpRuntimeProvider {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String CACHE_SUBDIR = "omnisharp-runtimes";

  private static final Map<DownloadableArtifact, String> ARTIFACT_PROPERTY_KEYS = Map.of(
    DownloadableArtifact.OMNISHARP_MONO, "sonar.cs.internal.omnisharpMonoLocation",
    DownloadableArtifact.OMNISHARP_NET472, "sonar.cs.internal.omnisharpWinLocation",
    DownloadableArtifact.OMNISHARP_NET6, "sonar.cs.internal.omnisharpNet6Location");

  private final boolean isDotnetEnabled;
  @Nullable
  private final OmnisharpRequirementsDto requirements;
  private final HttpClient httpClient;
  private final Path cacheDir;
  private Map<String, Path> cache;

  public OmnisharpRuntimeProvider(InitializeParams initializeParams, HttpClientProvider httpClientProvider, UserPaths userPaths) {
    var languages = initializeParams.getEnabledLanguagesInStandaloneMode();
    this.isDotnetEnabled = languages.contains(Language.CS) || languages.contains(Language.VBNET);
    var lsr = initializeParams.getLanguageSpecificRequirements();
    this.requirements = lsr != null ? lsr.getOmnisharpRequirements() : null;
    this.httpClient = httpClientProvider.getHttpClientWithoutAuth();
    this.cacheDir = userPaths.getStorageRoot().resolve(CACHE_SUBDIR);
  }

  /**
   * Returns a map of analysis property keys to OmniSharp runtime folder paths,
   * or an empty map if .NET languages are not enabled or paths could not be resolved.
   */
  public synchronized Map<String, Path> getPaths() {
    if (cache == null) {
      cache = resolve();
    }
    return cache;
  }

  private Map<String, Path> resolve() {
    if (!isDotnetEnabled) {
      return Map.of();
    }
    if (requirements == null) {
      return Map.of();
    }
    var mono = requirements.getMonoDistributionPath();
    var win = requirements.getDotNet472DistributionPath();
    var net6 = requirements.getDotNet6DistributionPath();
    if (mono != null && win != null && net6 != null) {
      return Map.of(
        "sonar.cs.internal.omnisharpMonoLocation", mono,
        "sonar.cs.internal.omnisharpWinLocation", win,
        "sonar.cs.internal.omnisharpNet6Location", net6);
    }
    return downloadOmnisharpRuntimes();
  }

  private Map<String, Path> downloadOmnisharpRuntimes() {
    var result = new HashMap<String, Path>();
    for (var entry : ARTIFACT_PROPERTY_KEYS.entrySet()) {
      try {
        result.put(entry.getValue(), downloadAndExtract(entry.getKey()));
      } catch (Exception e) {
        LOG.error("Failed to download OmniSharp runtime {}", entry.getKey().artifactKey(), e);
        return Map.of();
      }
    }
    return Map.copyOf(result);
  }

  private Path downloadAndExtract(DownloadableArtifact artifact) throws IOException {
    var artifactDir = cacheDir.resolve(artifact.artifactKey()).resolve(artifact.version());
    var archivePath = artifactDir.resolve(artifact.artifactKey() + ".tar.gz");
    var extractedDir = artifactDir.resolve("extracted");

    if (!Files.exists(archivePath)) {
      Files.createDirectories(artifactDir);
      downloadArchive(artifact, archivePath);
    }
    if (!Files.exists(extractedDir) || PathUtils.isEmptyDirectory(extractedDir)) {
      Files.createDirectories(extractedDir);
      TarGzUtils.extractTarGz(archivePath, extractedDir);
    }
    return extractedDir;
  }

  private void downloadArchive(DownloadableArtifact artifact, Path destination) throws IOException {
    var url = String.format(artifact.urlPattern(), artifact.version());
    LOG.info("Downloading OmniSharp runtime {} version {} from {}", artifact.artifactKey(), artifact.version(), url);
    try (var response = httpClient.get(url)) {
      if (!response.isSuccessful()) {
        throw new IOException("Failed to download OmniSharp runtime " + artifact.artifactKey() + ": HTTP " + response.code());
      }
      try (var inputStream = response.bodyAsStream()) {
        FileUtils.copyInputStreamToFile(inputStream, destination.toFile());
      }
    }
  }
}
