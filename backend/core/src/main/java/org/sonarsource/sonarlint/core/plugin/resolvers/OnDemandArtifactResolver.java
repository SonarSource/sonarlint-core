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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.PluginStatusUpdateEvent;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.plugin.ArtifactSource;
import org.sonarsource.sonarlint.core.plugin.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.PluginStatus;
import org.sonarsource.sonarlint.core.plugin.ResolvedArtifact;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.Optional.ofNullable;

/**
 * Resolves on-demand analyzer plugins by downloading them from public artifact URLs,
 * without requiring a server connection.
 *
 * <p>Plugins are cached locally under {@code <storageRoot>/ondemand-plugins/<key>/<version>/}.
 * Each download is verified against a known signature before being promoted to the cache.</p>
 *
 * <p><b>Download lifecycle:</b> {@link #resolve} returns {@link ArtifactState#DOWNLOADING}
 * immediately while a background download runs. When it completes, a
 * {@link PluginStatusUpdateEvent} is published with {@link ArtifactState#ACTIVE} on success
 * or {@link ArtifactState#FAILED} on error. Concurrent calls for the same artifact key are
 * de-duplicated — at most one download runs at a time.</p>
 */
public class OnDemandArtifactResolver implements ArtifactResolver {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String CACHE_SUBDIR = "ondemand-plugins";

  private final Path cacheBaseDirectory;
  private final HttpClientProvider httpClientProvider;
  private final OnDemandPluginSignatureVerifier signatureVerifier;
  private final OnDemandPluginCacheManager cacheManager;
  private final Map<SonarLanguage, DownloadableArtifact> artifactsByLanguage;
  private final ApplicationEventPublisher eventPublisher;
  private final UniqueTaskExecutor uniqueTaskExecutor;

  private final Map<String, Path> cachedArtifactPaths = new ConcurrentHashMap<>();

  public OnDemandArtifactResolver(UserPaths userPaths, HttpClientProvider httpClientProvider,
    @Qualifier("onDemandArtifactsByLanguage") Map<SonarLanguage, DownloadableArtifact> artifactsByLanguage,
    ApplicationEventPublisher eventPublisher,
    @Qualifier("pluginDownloadExecutor") ExecutorService downloadExecutor) {
    this.cacheBaseDirectory = userPaths.getStorageRoot().resolve(CACHE_SUBDIR);
    this.httpClientProvider = httpClientProvider;
    this.signatureVerifier = new OnDemandPluginSignatureVerifier();
    this.cacheManager = new OnDemandPluginCacheManager();
    this.artifactsByLanguage = artifactsByLanguage;
    this.eventPublisher = eventPublisher;
    this.uniqueTaskExecutor = new UniqueTaskExecutor(downloadExecutor);
  }

  @Override
  public Optional<ResolvedArtifact> resolve(SonarLanguage language, @Nullable String connectionId) {
    return ofNullable(artifactsByLanguage.get(language))
      .map(artifact -> findCachedArtifact(artifact)
        .orElseGet(() -> scheduleDownload(artifact)));
  }

  private ResolvedArtifact scheduleDownload(DownloadableArtifact artifact) {
    uniqueTaskExecutor.scheduleIfAbsent(artifact.artifactKey(), () -> downloadAndFireEvent(artifact));
    return new ResolvedArtifact(ArtifactState.DOWNLOADING, null, null, null);
  }

  private Optional<ResolvedArtifact> findCachedArtifact(DownloadableArtifact artifact) {
    var artifactKey = artifact.artifactKey();
    var cached = cachedArtifactPaths.get(artifactKey);
    if (cached != null && Files.exists(cached)) {
      return Optional.of(toActiveArtifact(artifact, cached));
    }
    var pluginPath = buildPluginPath(artifact);
    if (Files.exists(pluginPath)) {
      if (signatureVerifier.verify(pluginPath, artifactKey)) {
        cachedArtifactPaths.put(artifactKey, pluginPath);
        return Optional.of(toActiveArtifact(artifact, pluginPath));
      }
      LOG.warn("Signature verification failed for cached plugin {}, will re-download", artifactKey);
      deleteQuietly(pluginPath);
    }
    return Optional.empty();
  }

  private static ResolvedArtifact toActiveArtifact(DownloadableArtifact artifact, Path pluginPath) {
    return new ResolvedArtifact(ArtifactState.ACTIVE, pluginPath, ArtifactSource.ON_DEMAND, Version.create(artifact.version()));
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
      .map(e -> PluginStatus.forLanguage(e.getKey(), ArtifactState.ACTIVE, ArtifactSource.ON_DEMAND, version, null, pluginPath, null))
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
      if (!signatureVerifier.verify(tempFile, artifact.artifactKey())) {
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
