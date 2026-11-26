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
package org.sonarsource.sonarlint.core.plugin.resources;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.sonar.api.scanner.ResourceFetcher;
import org.sonar.api.utils.ZipUtils;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.plugins.SonarCloudCdnPlugins;
import org.sonarsource.sonarlint.core.serverapi.plugins.StaticResource;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.serverconnection.storage.StaticResourceStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.TarGzUtils;

/**
 * Returns a connected-mode static resource from {@code resources/{sha256}/{filename}}.
 * A JAR-packaged plugin has an empty inventory, so a fetch for it fails.
 */
public class ConnectedResourceFetcher implements ResourceFetcher {

  private static final Duration STALE_EXTRACTING_AGE = Duration.ofHours(1);
  private static final String EXTRACTING_PREFIX = ".extracting-";

  private final String connectionId;
  private final Map<String, StoredPlugin> plugins;
  private final StaticResourceStorage storage;
  private final Downloader downloader;

  public ConnectedResourceFetcher(String connectionId, Map<String, StoredPlugin> plugins, StaticResourceStorage storage, SonarQubeClientManager clientManager) {
    this.connectionId = connectionId;
    this.plugins = Map.copyOf(plugins);
    this.storage = storage;
    this.downloader = (plugin, resourcePath) -> download(clientManager, connectionId, plugin, resourcePath);
  }

  @Override
  public Path fetch(String pluginKey, String resourcePath) {
    var plugin = requirePlugin(pluginKey);
    var resource = requireResource(plugin, pluginKey, resourcePath);
    return cachedOrDownload(plugin, pluginKey, resource);
  }

  private Path cachedOrDownload(StoredPlugin plugin, String pluginKey, StaticResource resource) {
    var filename = filename(resource.path());
    var cached = storage.file(resource.hash(), filename);
    if (storage.matches(cached, resource.hash())) {
      return cached;
    }
    var bytes = downloader.download(plugin, resource.path());
    var actualHash = DigestUtils.sha256Hex(bytes);
    if (!actualHash.equalsIgnoreCase(resource.hash())) {
      throw new IllegalStateException("Static resource '" + resource.path() + "' for plugin '" + pluginKey + "' has SHA-256 " + actualHash + ", expected " + resource.hash());
    }
    try {
      return storage.store(resource.hash(), filename, bytes);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot store static resource '" + resource.path() + "'", e);
    }
  }

  @Override
  public Path fetchAndExtract(String pluginKey, String resourcePath) {
    var normalized = resourcePath.toLowerCase(Locale.ROOT);
    var zip = normalized.endsWith(".zip");
    var tarGz = normalized.endsWith(".tar.gz");
    if (!zip && !tarGz) {
      throw new IllegalArgumentException("Cannot extract '" + resourcePath + "' for plugin '" + pluginKey + "' because only zip and tar.gz resources are supported");
    }
    var resource = requireResource(requirePlugin(pluginKey), pluginKey, resourcePath);
    var destination = storage.extractionDirectory(resource.hash());
    deleteStaleExtractingDirectories(destination);
    var complete = destination.resolve(StaticResourceStorage.EXTRACTION_COMPLETE);
    if (Files.isRegularFile(complete)) {
      return destination;
    }
    var archive = fetch(pluginKey, resourcePath);
    var extracting = destination.resolveSibling(EXTRACTING_PREFIX + UUID.randomUUID());
    try {
      Files.createDirectories(extracting);
      if (zip) {
        ZipUtils.unzip(archive.toFile(), extracting.toFile());
      } else {
        TarGzUtils.extractTarGz(archive, extracting);
      }
      Files.writeString(extracting.resolve(StaticResourceStorage.EXTRACTION_COMPLETE), "");
      if (Files.isRegularFile(complete) || keepNewlyCompletedExtraction(destination)) {
        deleteRecursively(extracting);
        return destination;
      }
      publishExtraction(extracting, destination);
      if (!extractionRemainsComplete(complete)) {
        throw new IOException("Extracted resource '" + resourcePath + "' for plugin '" + pluginKey + "' is incomplete");
      }
      return destination;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot extract '" + resourcePath + "' for plugin '" + pluginKey + "'", e);
    } finally {
      FileUtils.deleteQuietly(extracting.toFile());
    }
  }

  private StoredPlugin requirePlugin(String pluginKey) {
    var plugin = plugins.get(pluginKey);
    if (plugin == null) {
      throw new IllegalStateException("Plugin '" + pluginKey + "' is not stored for connection '" + connectionId + "'");
    }
    return plugin;
  }

  private static StaticResource requireResource(StoredPlugin plugin, String pluginKey, String resourcePath) {
    return plugin.getStaticResources().stream()
      .filter(resource -> resource.path().equals(resourcePath))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("Plugin '" + pluginKey + "' has no static resource '" + resourcePath + "'"));
  }

  private static byte[] download(SonarQubeClientManager clientManager, String connectionId, StoredPlugin plugin, String resourcePath) {
    return clientManager.withActiveClientAndReturn(connectionId, api -> read(api, plugin, resourcePath))
      .orElseThrow(() -> new IllegalStateException("Connection '" + connectionId + "' cannot download static resources"));
  }

  private static byte[] read(ServerApi api, StoredPlugin plugin, String resourcePath) {
    var bytes = new byte[1][];
    if (api.isSonarCloud()) {
      var folder = plugin.getStaticResourceFolder();
      if (folder == null || folder.isBlank()) {
        throw new IllegalStateException("Plugin '" + plugin.getKey() + "' has no static resource folder");
      }
      new SonarCloudCdnPlugins(api.getHelper()).getResource(plugin.getKey(), folder, resourcePath, stream -> bytes[0] = readBytes(stream), new SonarLintCancelMonitor());
    } else {
      api.staticFiles().get("static/" + plugin.getKey() + "/" + resourcePath, stream -> bytes[0] = readBytes(stream), new SonarLintCancelMonitor());
    }
    return bytes[0];
  }

  private static byte[] readBytes(InputStream stream) {
    try {
      return stream.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String filename(String resourcePath) {
    var name = Path.of(resourcePath).getFileName();
    if (name == null || name.toString().isBlank() || ".".equals(name.toString()) || "..".equals(name.toString())) {
      throw new IllegalArgumentException("Invalid static resource path: " + resourcePath);
    }
    return name.toString();
  }

  /**
   * Returns true when {@code destination} is already complete, without moving it. An incomplete directory is renamed
   * aside and deleted. A complete directory that replaces it during that rename is moved back.
   */
  static boolean keepNewlyCompletedExtraction(Path destination) throws IOException {
    var marker = destination.resolve(StaticResourceStorage.EXTRACTION_COMPLETE);
    if (Files.isRegularFile(marker)) {
      return true;
    }
    if (!Files.exists(destination)) {
      return false;
    }
    var aside = destination.resolveSibling(EXTRACTING_PREFIX + UUID.randomUUID());
    try {
      Files.move(destination, aside);
    } catch (IOException moveFailed) {
      if (Files.isRegularFile(marker)) {
        return true;
      }
      if (!Files.exists(destination)) {
        return false;
      }
      throw moveFailed;
    }
    if (!Files.isRegularFile(aside.resolve(StaticResourceStorage.EXTRACTION_COMPLETE))) {
      deleteRecursivelyQuietly(aside);
      return false;
    }
    restoreCompletedExtraction(aside, destination);
    return true;
  }

  /**
   * Moves a completed aside directory back to {@code destination}. When another process has already restored that
   * directory, the aside copy is deleted.
   */
  static void restoreCompletedExtraction(Path aside, Path destination) throws IOException {
    try {
      Files.move(aside, destination);
    } catch (IOException moveFailed) {
      if (Files.isRegularFile(destination.resolve(StaticResourceStorage.EXTRACTION_COMPLETE))) {
        deleteRecursively(aside);
        return;
      }
      throw moveFailed;
    }
  }

  /**
   * A completed extraction can disappear for the moment another process moves it aside and restores it.
   * Poll instead of spinning while that move is in progress.
   */
  static boolean extractionRemainsComplete(Path complete) {
    return extractionRemainsComplete(complete, Duration.ofMillis(200), Duration.ofMillis(10), Thread::sleep);
  }

  static boolean extractionRemainsComplete(Path complete, Duration timeout, Duration pollInterval, PollDelay pollDelay) {
    var deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      if (Files.isRegularFile(complete)) {
        return true;
      }
      if (System.nanoTime() >= deadline) {
        return false;
      }
      try {
        pollDelay.awaitNext(pollInterval);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return Files.isRegularFile(complete);
      }
    }
  }

  static void publishExtraction(Path extracting, Path destination) throws IOException {
    try {
      Files.move(extracting, destination);
    } catch (IOException moveFailed) {
      deleteRecursivelyQuietly(extracting);
      if (!Files.isDirectory(destination)) {
        throw moveFailed;
      }
    }
  }

  private static void deleteStaleExtractingDirectories(Path destination) {
    var parent = destination.getParent();
    if (parent == null || !Files.isDirectory(parent)) {
      return;
    }
    var cutoff = Instant.now().minus(STALE_EXTRACTING_AGE);
    try (var children = Files.list(parent)) {
      for (var child : children.filter(path -> path.getFileName().toString().startsWith(EXTRACTING_PREFIX)).toList()) {
        deleteExtractingDirectoryIfStale(child, cutoff);
      }
    } catch (IOException | UncheckedIOException ignored) {
      // A failed sweep must not block the extraction.
    }
  }

  private static void deleteExtractingDirectoryIfStale(Path child, Instant cutoff) {
    try {
      if (Files.getLastModifiedTime(child).toInstant().isBefore(cutoff)) {
        deleteRecursively(child);
      }
    } catch (IOException | UncheckedIOException ignored) {
      // Another process may still be writing this extracting directory.
    }
  }

  private static void deleteRecursivelyQuietly(Path root) {
    try {
      deleteRecursively(root);
    } catch (IOException | UncheckedIOException ignored) {
      // The extraction is already failing.
    }
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (var walk = Files.walk(root)) {
      for (var path : walk.sorted((left, right) -> right.compareTo(left)).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }

  @FunctionalInterface
  interface PollDelay {
    void awaitNext(Duration pollInterval) throws InterruptedException;
  }

  @FunctionalInterface
  interface Downloader {
    byte[] download(StoredPlugin plugin, String resourcePath);
  }

}
