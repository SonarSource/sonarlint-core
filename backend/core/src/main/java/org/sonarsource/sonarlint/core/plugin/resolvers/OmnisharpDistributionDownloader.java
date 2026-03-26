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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.OmnisharpDistributionChangedEvent;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.springframework.context.ApplicationEventPublisher;

import static org.sonarsource.sonarlint.core.serverconnection.storage.TarGzUtils.extractTarGz;

/**
 * Downloads, verifies, and extracts the three OmniSharp runtime distributions
 * (Mono, .NET Framework 4.7.2, .NET 6) from binaries.sonarsource.com.
 *
 * <p>Downloads are triggered at startup when C# is an enabled language. Each variant is
 * downloaded in the background via a shared {@link UniqueTaskExecutor}, verified against
 * its bundled PGP signature, then extracted into
 * {@code <storageRoot>/ondemand-plugins/omnisharp/<variant>/<version>/}.
 * When all variants are ready an {@link OmnisharpDistributionChangedEvent} is published
 * so that analysis schedulers can pick up the new paths.</p>
 *
 * <p>On subsequent startups the extracted directories are reused if they already exist and
 * the version has not changed.</p>
 */
public class OmnisharpDistributionDownloader {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String CACHE_SUBDIR = "ondemand-plugins/omnisharp";

  static final String VARIANT_MONO = "mono";
  static final String VARIANT_NET472 = "net472";
  static final String VARIANT_NET6 = "net6";

  private static final List<DownloadableArtifact> OMNISHARP_ARTIFACTS = List.of(
    DownloadableArtifact.OMNISHARP_MONO,
    DownloadableArtifact.OMNISHARP_NET472,
    DownloadableArtifact.OMNISHARP_NET6);

  private static final Map<DownloadableArtifact, String> ARTIFACT_TO_VARIANT = Map.of(
    DownloadableArtifact.OMNISHARP_MONO, VARIANT_MONO,
    DownloadableArtifact.OMNISHARP_NET472, VARIANT_NET472,
    DownloadableArtifact.OMNISHARP_NET6, VARIANT_NET6);

  private static final String TAR_GZ_EXTENSION = ".tar.gz";
  private static final String ZIP_EXTENSION = ".zip";

  private final Path cacheBaseDirectory;
  private final HttpClientProvider httpClientProvider;
  private final OnDemandPluginSignatureVerifier signatureVerifier;
  private final ApplicationEventPublisher eventPublisher;
  private final UniqueTaskExecutor uniqueTaskExecutor;
  private final boolean csharpEnabled;

  private final Map<String, Path> resolvedPathsByVariant = new ConcurrentHashMap<>();
  private final AtomicBoolean eventFired = new AtomicBoolean(false);

  public OmnisharpDistributionDownloader(InitializeParams initializeParams, UserPaths userPaths, HttpClientProvider httpClientProvider,
    ApplicationEventPublisher eventPublisher, ExecutorService downloadExecutor, OnDemandPluginSignatureVerifier signatureVerifier) {
    this.cacheBaseDirectory = userPaths.getStorageRoot().resolve(CACHE_SUBDIR);
    this.httpClientProvider = httpClientProvider;
    this.signatureVerifier = signatureVerifier;
    this.eventPublisher = eventPublisher;
    this.uniqueTaskExecutor = new UniqueTaskExecutor(downloadExecutor);
    this.csharpEnabled = initializeParams.getEnabledLanguagesInStandaloneMode().contains(Language.CS)
      || initializeParams.getExtraEnabledLanguagesInConnectedMode().contains(Language.CS);
  }

  @PostConstruct
  void triggerDownloads() {
    if (!csharpEnabled) {
      return;
    }
    var allCached = true;
    for (var artifact : OMNISHARP_ARTIFACTS) {
      var variant = ARTIFACT_TO_VARIANT.get(artifact);
      var extractDir = buildExtractDir(artifact);
      if (Files.isDirectory(extractDir)) {
        LOG.debug("OmniSharp {} distribution already cached at {}", variant, extractDir);
        resolvedPathsByVariant.put(variant, extractDir);
      } else {
        allCached = false;
        scheduleDownload(artifact, variant);
      }
    }
    if (allCached) {
      notifyIfAllReady();
    }
  }

  private void scheduleDownload(DownloadableArtifact artifact, String variant) {
    uniqueTaskExecutor.scheduleIfAbsent("omnisharp-" + variant, () -> downloadAndFireEvent(artifact, variant));
  }

  private void downloadAndFireEvent(DownloadableArtifact artifact, String variant) {
    try {
      var extractDir = downloadAndExtract(artifact, variant);
      resolvedPathsByVariant.put(variant, extractDir);
      LOG.info("OmniSharp {} distribution ready at {}", variant, extractDir);
      notifyIfAllReady();
    } catch (Exception e) {
      LOG.error("Failed to download OmniSharp {} distribution", variant, e);
    }
  }

  private void notifyIfAllReady() {
    if (resolvedPathsByVariant.containsKey(VARIANT_MONO)
      && resolvedPathsByVariant.containsKey(VARIANT_NET472)
      && resolvedPathsByVariant.containsKey(VARIANT_NET6)
      && eventFired.compareAndSet(false, true)) {
      eventPublisher.publishEvent(new OmnisharpDistributionChangedEvent());
    }
  }

  private Path downloadAndExtract(DownloadableArtifact artifact, String variant) throws IOException {
    var extractDir = buildExtractDir(artifact);
    var archiveFileName = artifact.artifactKey() + "-" + artifact.version() + getArchiveExtension(artifact);
    var tempArchive = cacheBaseDirectory.resolve(archiveFileName + ".tmp");
    var tempExtractDir = cacheBaseDirectory.resolve(ARTIFACT_TO_VARIANT.get(artifact)).resolve(artifact.version() + ".extracting");
    Files.createDirectories(cacheBaseDirectory);
    try {
      downloadArtifact(artifact, tempArchive);
      if (!signatureVerifier.verify(tempArchive, artifact)) {
        throw new IOException("Signature verification failed for OmniSharp " + variant);
      }
      Files.createDirectories(tempExtractDir);
      extract(artifact, tempArchive, tempExtractDir);
      Files.move(tempExtractDir, extractDir, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
      LOG.info("Successfully extracted OmniSharp {} version {} to {}", variant, artifact.version(), extractDir);
      cleanupOldVersions(artifact);
    } finally {
      FileUtils.deleteQuietly(tempExtractDir.toFile());
      Files.deleteIfExists(tempArchive);
    }
    return extractDir;
  }

  private void downloadArtifact(DownloadableArtifact artifact, Path destination) throws IOException {
    var url = String.format(artifact.urlPattern(), artifact.version());
    var httpClient = httpClientProvider.getHttpClientWithoutAuth();
    LOG.info("Downloading OmniSharp {} version {} from {}", artifact.artifactKey(), artifact.version(), url);
    try (var response = httpClient.get(url)) {
      if (!response.isSuccessful()) {
        throw new IOException("Failed to download OmniSharp distribution: HTTP " + response.code());
      }
      try (var inputStream = response.bodyAsStream()) {
        FileUtils.copyInputStreamToFile(inputStream, destination.toFile());
      }
    }
  }

  private static void extract(DownloadableArtifact artifact, Path archive, Path targetDir) throws IOException {
    var url = artifact.urlPattern();
    if (url.endsWith(TAR_GZ_EXTENSION)) {
      extractTarGz(archive, targetDir);
    } else {
      throw new IOException("Unsupported archive format for " + artifact.artifactKey());
    }
  }

  private void cleanupOldVersions(DownloadableArtifact artifact) {
    var currentVersion = artifact.version();
    var variantDir = cacheBaseDirectory.resolve(ARTIFACT_TO_VARIANT.get(artifact));
    if (!Files.isDirectory(variantDir)) {
      return;
    }
    try (var versions = Files.list(variantDir)) {
      versions
        .filter(p -> Files.isDirectory(p) && !p.getFileName().toString().equals(currentVersion))
        .forEach(old -> {
          try {
            FileUtils.deleteDirectory(old.toFile());
            LOG.debug("Deleted old OmniSharp distribution at {}", old);
          } catch (IOException e) {
            LOG.warn("Could not delete old OmniSharp distribution at {}", old, e);
          }
        });
    } catch (IOException e) {
      LOG.warn("Could not list OmniSharp versions for cleanup", e);
    }
  }

  private Path buildExtractDir(DownloadableArtifact artifact) {
    return cacheBaseDirectory
      .resolve(ARTIFACT_TO_VARIANT.get(artifact))
      .resolve(artifact.version());
  }

  private static String getArchiveExtension(DownloadableArtifact artifact) {
    var url = artifact.urlPattern();
    if (url.endsWith(TAR_GZ_EXTENSION)) {
      return TAR_GZ_EXTENSION;
    } else if (url.endsWith(ZIP_EXTENSION)) {
      return ZIP_EXTENSION;
    }
    return "";
  }

  @CheckForNull
  public Path getMonoPath() {
    return resolvedPathsByVariant.get(VARIANT_MONO);
  }

  @CheckForNull
  public Path getDotNet472Path() {
    return resolvedPathsByVariant.get(VARIANT_NET472);
  }

  @CheckForNull
  public Path getDotNet6Path() {
    return resolvedPathsByVariant.get(VARIANT_NET6);
  }

  /**
   * Returns the client-provided path when available; otherwise falls back to the downloaded path.
   * The client-provided path takes priority because it reflects an explicit IDE-side installation.
   */
  @CheckForNull
  public static Path resolveWithFallback(@Nullable Path downloadedPath, @Nullable Path clientProvidedPath) {
    return clientProvidedPath != null ? clientProvidedPath : downloadedPath;
  }

}
