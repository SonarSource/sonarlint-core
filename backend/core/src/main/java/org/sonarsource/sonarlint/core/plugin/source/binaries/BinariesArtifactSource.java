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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.plugins.SonarArtifact;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPluginDependency;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactDownload;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactLocation;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactSource;
import org.sonarsource.sonarlint.core.plugin.source.AvailableArtifact;

import static org.sonarsource.sonarlint.core.serverconnection.storage.TarGzUtils.extractTarGz;

/**
 * Artifact source backed by publicly downloadable artifacts from binaries.sonarsource.com.
 * Handles both plugins (CFamily, C# OSS) and plugin dependencies (OmniSharp distributions).
 *
 * <p>{@link #listAvailableArtifacts(Set)} returns local cached artifacts and blocking remote
 * download operations for artifacts that are not cached yet.</p>
 */
public class BinariesArtifactSource implements ArtifactSource {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String CACHE_SUBDIR = "ondemand-plugins";

  private final Path cacheBaseDirectory;
  private final HttpClientProvider httpClientProvider;
  private final BinariesSignatureVerifier signatureVerifier;
  private final BinariesLocalCacheManager cacheManager;

  private final Map<String, Path> cachedArtifactPaths = new ConcurrentHashMap<>();

  BinariesArtifactSource(UserPaths userPaths, HttpClientProvider httpClientProvider,
    BinariesSignatureVerifier signatureVerifier, BinariesLocalCacheManager binariesLocalCacheManager) {
    this.cacheBaseDirectory = userPaths.getStorageRoot().resolve(CACHE_SUBDIR);
    this.httpClientProvider = httpClientProvider;
    this.signatureVerifier = signatureVerifier;
    this.cacheManager = binariesLocalCacheManager;
  }

  /**
   * Returns all artifacts known to this source whose languages intersect {@code enabledLanguages}.
   * No downloads triggered.
   */
  @Override
  public List<AvailableArtifact> listAvailableArtifacts(Set<SonarLanguage> enabledLanguages) {
    return Arrays.stream(BinariesArtifact.values())
      .filter(artifact -> artifact.getLanguages().stream().anyMatch(enabledLanguages::contains))
      .map(this::toAvailableArtifact)
      .toList();
  }

  private AvailableArtifact toAvailableArtifact(BinariesArtifact artifact) {
    var version = Version.create(artifact.version());
    var sonarArtifact = SonarPlugin.findByKey(artifact.artifactKey()).<SonarArtifact>map(p -> p)
      .or(() -> SonarPluginDependency.findByKey(artifact.artifactKey()));
    ArtifactLocation location = findCachedArtifact(artifact)
      .<ArtifactLocation>map(cached -> new ArtifactLocation.Local(cached, ArtifactOrigin.ON_DEMAND, version))
      .orElseGet(() -> new ArtifactLocation.Remote(new BinariesDownload(artifact)));
    return new AvailableArtifact(artifact.artifactKey(), version, false, sonarArtifact, location);
  }

  private class BinariesDownload implements ArtifactDownload {
    private final BinariesArtifact artifact;

    private BinariesDownload(BinariesArtifact artifact) {
      this.artifact = artifact;
    }

    @Override
    public String deduplicationKey() {
      return String.format(artifact.urlPattern(), artifact.version());
    }

    @Override
    public ArtifactLocation.Local download() throws IOException {
      var path = downloadAndCache(artifact);
      return new ArtifactLocation.Local(path, ArtifactOrigin.ON_DEMAND, Version.create(artifact.version()));
    }
  }

  private Optional<Path> findCachedArtifact(BinariesArtifact artifact) {
    var artifactKey = artifact.artifactKey();
    var cached = cachedArtifactPaths.get(artifactKey);
    if (cached != null && Files.exists(cached)) {
      return Optional.of(cached);
    }
    var pluginPath = buildArtifactLocalPath(artifact);
    if (Files.exists(pluginPath)) {
      if (isValidCache(pluginPath, artifact)) {
        cachedArtifactPaths.put(artifactKey, pluginPath);
        return Optional.of(pluginPath);
      }
      LOG.warn("Invalid cached artifact {}, will re-download", artifactKey);
      FileUtils.deleteQuietly(pluginPath.toFile());
    }
    return Optional.empty();
  }

  /**
   * Returns {@code true} when the cached artifact at {@code pluginPath} is considered valid and
   * does not need to be re-downloaded.
   *
   * <p>For <b>JAR artifacts</b> the PGP signature is re-verified against the file on disk.
   *
   * <p>For <b>archive artifacts</b> (OmniSharp tar.gz distributions), {@code pluginPath} is the
   * extracted directory. The PGP signature was already verified against the original archive at
   * download time; the archive is deleted after extraction, so the signature cannot be re-checked.
   * A non-empty directory is used as the completion marker instead.
   */
  private boolean isValidCache(Path pluginPath, BinariesArtifact artifact) {
    if (artifact.isArchive()) {
      try (var entries = Files.list(pluginPath)) {
        return entries.findFirst().isPresent();
      } catch (IOException e) {
        LOG.warn("Could not read cached archive directory for {}", artifact.artifactKey(), e);
        return false;
      }
    }
    return signatureVerifier.verify(pluginPath, artifact);
  }

  private Path downloadAndCache(BinariesArtifact artifact) throws IOException {
    var pluginPath = buildArtifactLocalPath(artifact);
    downloadAndVerify(artifact, pluginPath);
    cacheManager.cleanupOldVersions(cacheBaseDirectory.resolve(artifact.artifactKey()), artifact.version());
    cachedArtifactPaths.put(artifact.artifactKey(), pluginPath);
    return pluginPath;
  }

  public Map<String, String> getOmnisharpExtraProperties() {
    var properties = new HashMap<String, String>();
    putIfCached(properties, SonarPluginDependency.OMNISHARP_MONO.getKey(), "sonar.cs.internal.omnisharpMonoLocation");
    putIfCached(properties, SonarPluginDependency.OMNISHARP_NET472.getKey(), "sonar.cs.internal.omnisharpWinLocation");
    putIfCached(properties, SonarPluginDependency.OMNISHARP_NET6.getKey(), "sonar.cs.internal.omnisharpNet6Location");
    return properties;
  }

  private void putIfCached(Map<String, String> properties, String artifactKey, String propertyKey) {
    var path = cachedArtifactPaths.get(artifactKey);
    if (path != null && Files.exists(path)) {
      properties.put(propertyKey, path.toString());
    }
  }

  private void downloadAndVerify(BinariesArtifact artifact, Path targetPath) throws IOException {
    Files.createDirectories(targetPath.getParent());
    var tempFile = targetPath.getParent().resolve(targetPath.getFileName() + ".tmp");
    try {
      downloadArtifact(artifact, tempFile);
      if (!signatureVerifier.verify(tempFile, artifact)) {
        throw new IOException("Signature verification failed for " + artifact.artifactKey());
      }
      if (artifact.isArchive()) {
        var tempExtractDir = targetPath.getParent().resolve(targetPath.getFileName() + ".extracting");
        try {
          Files.createDirectories(tempExtractDir);
          extractTarGz(tempFile, tempExtractDir);
          moveAtomically(tempExtractDir, targetPath);
        } finally {
          FileUtils.deleteQuietly(tempExtractDir.toFile());
        }
      } else {
        moveAtomically(tempFile, targetPath);
      }
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

  private void downloadArtifact(BinariesArtifact artifact, Path destination) throws IOException {
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

  private Path buildArtifactLocalPath(BinariesArtifact artifact) {
    var artifactKey = artifact.artifactKey();
    var version = artifact.version();
    var base = cacheBaseDirectory.resolve(artifactKey).resolve(version);
    if (artifact.isArchive()) {
      return base;
    }
    return base.resolve(String.format("sonar-%s-plugin-%s.jar", artifactKey, version));
  }

}
