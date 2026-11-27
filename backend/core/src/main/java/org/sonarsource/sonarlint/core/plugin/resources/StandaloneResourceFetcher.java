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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.sonar.api.scanner.ResourceFetcher;
import org.sonar.api.utils.ZipUtils;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifact;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifactSource;
import org.sonarsource.sonarlint.core.serverapi.plugins.StaticResource;
import org.sonarsource.sonarlint.core.serverconnection.storage.TarGzUtils;

/**
 * Downloads a standalone static resource from the exploded binaries tree on every call.
 * The bytes are checked against the {@code staticResources} entries of the local {@code manifest.json}.
 * A JAR-packaged plugin has no exploded tree, so a fetch for it fails.
 */
public class StandaloneResourceFetcher implements ResourceFetcher {

  private static final Gson GSON = new Gson();

  private final Path onDemandPluginsDirectory;
  private final Path workDirectory;
  private final Downloader downloader;

  public StandaloneResourceFetcher(Path storageRoot, HttpClientProvider httpClientProvider) {
    this(storageRoot, url -> download(httpClientProvider, url));
  }

  StandaloneResourceFetcher(Path storageRoot, Downloader downloader) {
    this.onDemandPluginsDirectory = storageRoot.resolve(BinariesArtifactSource.CACHE_SUBDIR);
    this.workDirectory = storageRoot.resolve("tmp");
    this.downloader = downloader;
  }

  @Override
  public Path fetch(String pluginKey, String resourcePath) {
    filename(resourcePath);
    var artifact = requireExplodedPlugin(pluginKey);
    var resource = requireResource(artifact, pluginKey, resourcePath);
    if (resource.hash() == null || resource.hash().isBlank()) {
      throw new IllegalStateException("Plugin '" + pluginKey + "' has no hash for static resource '" + resourcePath + "'");
    }
    var bytes = downloader.download(artifact.resourceUrl(resourcePath));
    var actualHash = DigestUtils.sha256Hex(bytes);
    if (!actualHash.equalsIgnoreCase(resource.hash())) {
      throw new IllegalStateException("Static resource '" + resourcePath + "' for plugin '" + pluginKey + "' has SHA-256 " + actualHash + ", expected " + resource.hash());
    }
    return writeTemporaryFile(workDirectory, resourcePath, bytes);
  }

  @Override
  public Path fetchAndExtract(String pluginKey, String resourcePath) {
    var normalized = resourcePath.toLowerCase(Locale.ROOT);
    var zip = normalized.endsWith(".zip");
    var tarGz = normalized.endsWith(".tar.gz");
    if (!zip && !tarGz) {
      throw new IllegalArgumentException("Cannot extract '" + resourcePath + "' for plugin '" + pluginKey + "' because only zip and tar.gz resources are supported");
    }
    var archive = fetch(pluginKey, resourcePath);
    Path destination = null;
    try {
      Files.createDirectories(workDirectory);
      destination = Files.createTempDirectory(workDirectory, "sonarlint-resource-");
      if (zip) {
        ZipUtils.unzip(archive.toFile(), destination.toFile());
      } else {
        TarGzUtils.extractTarGz(archive, destination);
      }
      var extracted = destination;
      destination = null;
      return extracted;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot extract '" + resourcePath + "' for plugin '" + pluginKey + "'", e);
    } finally {
      if (destination != null) {
        FileUtils.deleteQuietly(destination.toFile());
      }
      FileUtils.deleteQuietly(archive.toFile());
    }
  }

  private StaticResource requireResource(BinariesArtifact artifact, String pluginKey, String resourcePath) {
    var manifest = readManifest(artifact, pluginKey);
    return manifest.staticResources.stream()
      .filter(resource -> resourcePath.equals(resource.path()))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("Plugin '" + pluginKey + "' has no static resource '" + resourcePath + "'"));
  }

  private PluginManifest readManifest(BinariesArtifact artifact, String pluginKey) {
    var manifest = artifact.localManifest(onDemandPluginsDirectory);
    if (!Files.isRegularFile(manifest)) {
      throw new IllegalStateException("Plugin '" + pluginKey + "' has no manifest");
    }
    try {
      var parsed = GSON.fromJson(Files.readString(manifest), PluginManifest.class);
      if (parsed == null || parsed.staticResources == null) {
        return new PluginManifest(List.of());
      }
      return parsed;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read manifest for plugin '" + pluginKey + "'", e);
    } catch (JsonSyntaxException e) {
      throw new IllegalStateException("Cannot read manifest for plugin '" + pluginKey + "'", e);
    }
  }

  private static BinariesArtifact requireExplodedPlugin(String pluginKey) {
    return BinariesArtifact.findByKey(pluginKey)
      .filter(BinariesArtifact::isExploded)
      .orElseThrow(() -> new IllegalStateException("Plugin '" + pluginKey + "' is not a standalone ZIP plugin"));
  }

  private static byte[] download(HttpClientProvider httpClientProvider, String url) {
    var httpClient = httpClientProvider.getHttpClientWithoutAuth();
    try (var response = httpClient.get(url)) {
      if (!response.isSuccessful()) {
        throw new IllegalStateException("Failed to download static resource: HTTP " + response.code());
      }
      try (var body = response.bodyAsStream()) {
        return body.readAllBytes();
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to download static resource from " + url, e);
    }
  }

  private static Path writeTemporaryFile(Path workDirectory, String resourcePath, byte[] bytes) {
    var filename = filename(resourcePath);
    Path file = null;
    try {
      Files.createDirectories(workDirectory);
      file = Files.createTempFile(workDirectory, "sonarlint-resource-", "-" + filename);
      Files.write(file, bytes);
      var written = file;
      file = null;
      return written;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot store static resource '" + resourcePath + "'", e);
    } finally {
      if (file != null) {
        FileUtils.deleteQuietly(file.toFile());
      }
    }
  }

  private static String filename(String resourcePath) {
    if (resourcePath.startsWith("/") || resourcePath.contains("\\")) {
      throw new IllegalArgumentException("Invalid static resource path: " + resourcePath);
    }
    var segments = resourcePath.split("/", -1);
    for (var segment : segments) {
      if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
        throw new IllegalArgumentException("Invalid static resource path: " + resourcePath);
      }
    }
    return segments[segments.length - 1];
  }

  @FunctionalInterface
  interface Downloader {
    byte[] download(String url);
  }

  private static final class PluginManifest {
    private final List<StaticResource> staticResources;

    private PluginManifest(List<StaticResource> staticResources) {
      this.staticResources = staticResources;
    }
  }

}
