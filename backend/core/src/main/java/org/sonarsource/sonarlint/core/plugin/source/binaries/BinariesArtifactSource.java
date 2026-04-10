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

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.event.PluginStatusUpdateEvent;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.plugin.PluginStatus;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactSource;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.source.AvailableArtifact;
import org.sonarsource.sonarlint.core.plugin.source.DownloadableArtifact;
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;
import org.sonarsource.sonarlint.core.plugin.source.UniqueTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Artifact source backed by publicly downloadable artifacts from binaries.sonarsource.com.
 * Handles both plugins (CFamily, C# OSS) and plugin dependencies (OmniSharp distributions).
 *
 * <p>{@link #listAvailableArtifacts(Set)} is a pure query: it returns all known artifacts, with a
 * non-null {@code jarPath} only for those already cached and verified on disk. {@link #load(String)}
 * schedules a background download when the artifact is not yet cached, returning
 * {@link ArtifactState#DOWNLOADING} immediately. A {@link PluginStatusUpdateEvent} is published
 * when the download completes.</p>
 */
public class BinariesArtifactSource implements ArtifactSource {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String CACHE_SUBDIR = "ondemand-plugins";

  private final Path cacheBaseDirectory;
  private final HttpClientProvider httpClientProvider;
  private final OnDemandPluginSignatureVerifier signatureVerifier;
  private final OnDemandPluginCacheManager cacheManager;
  // Maps SonarPlugin key → DownloadableArtifact (used for list() and load())
  private static final Map<SonarPlugin, DownloadableArtifact> ARTIFACTS_BY_PLUGIN_KEY = Map.of(
    SonarPlugin.C_FAMILY, DownloadableArtifact.CFAMILY_PLUGIN,
    SonarPlugin.CS_OSS, DownloadableArtifact.CSHARP_OSS,
    SonarPlugin.VBNET_OSS, DownloadableArtifact.CSHARP_OSS);
  // Used only for event publishing (language-level status events)
  private final Map<SonarLanguage, DownloadableArtifact> artifactsByLanguage = Map.of(
    SonarLanguage.C, DownloadableArtifact.CFAMILY_PLUGIN,
    SonarLanguage.CPP, DownloadableArtifact.CFAMILY_PLUGIN,
    SonarLanguage.OBJC, DownloadableArtifact.CFAMILY_PLUGIN,
    SonarLanguage.CS, DownloadableArtifact.CSHARP_OSS,
    SonarLanguage.VBNET, DownloadableArtifact.CSHARP_OSS);
  private final ApplicationEventPublisher eventPublisher;
  private final UniqueTaskExecutor uniqueTaskExecutor;

  private final Map<String, Path> cachedArtifactPaths = new ConcurrentHashMap<>();

  BinariesArtifactSource(UserPaths userPaths, HttpClientProvider httpClientProvider,
    ApplicationEventPublisher eventPublisher, @Qualifier("pluginDownloadExecutor") ExecutorService downloadExecutor,
    OnDemandPluginSignatureVerifier signatureVerifier) {
    this.cacheBaseDirectory = userPaths.getStorageRoot().resolve(CACHE_SUBDIR);
    this.httpClientProvider = httpClientProvider;
    this.signatureVerifier = signatureVerifier;
    this.cacheManager = new OnDemandPluginCacheManager();
    this.eventPublisher = eventPublisher;
    this.uniqueTaskExecutor = new UniqueTaskExecutor(downloadExecutor);
  }

  /**
   * Returns all artifacts known to this source whose languages intersect {@code enabledLanguages}.
   * No downloads triggered.
   */
  @Override
  public List<AvailableArtifact> listAvailableArtifacts(Set<SonarLanguage> enabledLanguages) {
    return ARTIFACTS_BY_PLUGIN_KEY.entrySet().stream()
      .filter(entry -> entry.getKey().getLanguages().stream().anyMatch(enabledLanguages::contains))
      .map(entry -> new AvailableArtifact(entry.getKey().getKey(), Version.create(entry.getValue().version())))
      .toList();
  }

  @Override
  public Optional<ResolvedArtifact> load(String artifactKey) {
    return SonarPlugin.findByKey(artifactKey)
      .map(ARTIFACTS_BY_PLUGIN_KEY::get)
      .map(artifact -> findCachedArtifact(artifact)
        .map(r -> toActiveArtifact(artifact, r.path()))
        .orElseGet(() -> scheduleDownload(artifact)));
  }

  private ResolvedArtifact scheduleDownload(DownloadableArtifact artifact) {
    var downloadFuture = uniqueTaskExecutor.scheduleIfAbsent(artifact.artifactKey(), () -> downloadAndFireEvent(artifact));
    return new ResolvedArtifact(ArtifactState.DOWNLOADING, null, null, null, downloadFuture);
  }

  private Optional<ResolvedArtifact> findCachedArtifact(DownloadableArtifact artifact) {
    var artifactKey = artifact.artifactKey();
    var cached = cachedArtifactPaths.get(artifactKey);
    if (cached != null && Files.exists(cached)) {
      return Optional.of(toActiveArtifact(artifact, cached));
    }
    var pluginPath = buildPluginPath(artifact);
    if (Files.exists(pluginPath)) {
      if (signatureVerifier.verify(pluginPath, artifact)) {
        cachedArtifactPaths.put(artifactKey, pluginPath);
        return Optional.of(toActiveArtifact(artifact, pluginPath));
      }
      LOG.warn("Signature verification failed for cached plugin {}, will re-download", artifactKey);
      deleteQuietly(pluginPath);
    }
    return Optional.empty();
  }

  private static ResolvedArtifact toActiveArtifact(DownloadableArtifact artifact, Path pluginPath) {
    return new ResolvedArtifact(ArtifactState.ACTIVE, pluginPath, ArtifactOrigin.ON_DEMAND, Version.create(artifact.version()), null);
  }

  private void downloadAndCache(DownloadableArtifact artifact) throws IOException {
    var pluginPath = buildPluginPath(artifact);
    downloadAndVerify(artifact, pluginPath);
    cacheManager.cleanupOldVersions(pluginPath.getParent().getParent(), artifact.version());
    cachedArtifactPaths.put(artifact.artifactKey(), pluginPath);
  }

  private void downloadAndFireEvent(DownloadableArtifact artifact) {
    try {
      downloadAndCache(artifact);
      var affectedStatuses = findAffectedLanguageStatuses(artifact, cachedArtifactPaths.get(artifact.artifactKey()));
      eventPublisher.publishEvent(new PluginStatusUpdateEvent(null, affectedStatuses));
    } catch (Exception e) {
      LOG.error("Failed to download artifact with key {}", artifact.artifactKey(), e);
      var failedStatuses = findAffectedFailedStatuses(artifact);
      eventPublisher.publishEvent(new PluginStatusUpdateEvent(null, failedStatuses));
    }
  }

  private List<PluginStatus> findAffectedLanguageStatuses(DownloadableArtifact artifact, Path pluginPath) {
    var version = Version.create(artifact.version());
    return findAffected(artifact)
      .map(e -> PluginStatus.forLanguage(e.getKey(), ArtifactState.ACTIVE, ArtifactOrigin.ON_DEMAND, version, null, pluginPath, null))
      .toList();
  }

  private List<PluginStatus> findAffectedFailedStatuses(DownloadableArtifact artifact) {
    return findAffected(artifact)
      .map(e -> PluginStatus.failed(e.getKey()))
      .toList();
  }

  private Stream<Map.Entry<SonarLanguage, DownloadableArtifact>> findAffected(DownloadableArtifact artifact) {
    return artifactsByLanguage.entrySet().stream()
      .filter(e -> e.getValue().artifactKey().equals(artifact.artifactKey()));
  }

  private void downloadAndVerify(DownloadableArtifact artifact, Path targetPath) throws IOException {
    Files.createDirectories(targetPath.getParent());
    var tempFile = targetPath.getParent().resolve(targetPath.getFileName() + ".tmp");
    try {
      downloadPlugin(artifact, tempFile);
      if (!signatureVerifier.verify(tempFile, artifact)) {
        throw new IOException("Signature verification failed for " + artifact.artifactKey());
      }
      moveAtomically(tempFile, targetPath);
      LOG.info("Successfully downloaded {} plugin version {}", artifact.artifactKey(), artifact.version());
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  private static void moveAtomically(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      LOG.debug("Failed to delete invalid cached plugin", e);
    }
  }

  private void downloadPlugin(DownloadableArtifact artifact, Path destination) throws IOException {
    var url = String.format(artifact.urlPattern(), artifact.version());
    var httpClient = httpClientProvider.getHttpClientWithoutAuth();
    LOG.info("Downloading {} plugin version {} from {}", artifact.artifactKey(), artifact.version(), url);
    try (var response = httpClient.get(url)) {
      if (!response.isSuccessful()) {
        throw new IOException("Failed to download plugin: HTTP " + response.code());
      }
      try (var inputStream = response.bodyAsStream()) {
        FileUtils.copyInputStreamToFile(inputStream, destination.toFile());
      }
    }
  }

  private Path buildPluginPath(DownloadableArtifact artifact) {
    var artifactKey = artifact.artifactKey();
    var version = artifact.version();
    return cacheBaseDirectory
      .resolve(artifactKey)
      .resolve(version)
      .resolve(String.format("sonar-%s-plugin-%s.jar", artifactKey, version));
  }

}
