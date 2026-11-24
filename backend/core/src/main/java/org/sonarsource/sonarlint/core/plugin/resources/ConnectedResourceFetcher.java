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
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.codec.digest.DigestUtils;
import org.sonar.api.scanner.ResourceFetcher;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.plugins.SonarCloudCdnPlugins;
import org.sonarsource.sonarlint.core.serverapi.plugins.StaticResource;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.serverconnection.storage.TarGzUtils;
import org.sonarsource.sonarlint.core.serverconnection.storage.ZipUtils;
import org.sonarsource.sonarlint.core.storage.StorageService;

/**
 * Downloads a connected-mode static resource on every call and checks it against the stored inventory.
 * A JAR-packaged plugin has an empty inventory, so a fetch for it fails.
 */
public class ConnectedResourceFetcher implements ResourceFetcher {

  private final String connectionId;
  private final Supplier<Map<String, StoredPlugin>> plugins;
  private final Downloader downloader;
  private final Path workDirectory;

  public ConnectedResourceFetcher(String connectionId, StorageService storageService, SonarQubeClientManager clientManager) {
    this(connectionId,
      () -> storageService.connection(connectionId).plugins().getStoredPluginsByKey(),
      (plugin, resourcePath) -> download(clientManager, connectionId, plugin, resourcePath),
      storageService.connection(connectionId).storageRoot().resolve("tmp"));
  }

  ConnectedResourceFetcher(String connectionId, Supplier<Map<String, StoredPlugin>> plugins, Downloader downloader, Path workDirectory) {
    this.connectionId = connectionId;
    this.plugins = plugins;
    this.downloader = downloader;
    this.workDirectory = workDirectory;
  }

  @Override
  public Path fetch(String pluginKey, String resourcePath) {
    var plugin = requirePlugin(pluginKey);
    var resource = requireResource(plugin, pluginKey, resourcePath);
    var bytes = downloader.download(plugin, resourcePath);
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
    try {
      Files.createDirectories(workDirectory);
      var destination = Files.createTempDirectory(workDirectory, "sonarlint-resource-");
      if (zip) {
        ZipUtils.extractZip(archive, destination);
      } else {
        TarGzUtils.extractTarGz(archive, destination);
      }
      return destination;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot extract '" + resourcePath + "' for plugin '" + pluginKey + "'", e);
    }
  }

  private StoredPlugin requirePlugin(String pluginKey) {
    var plugin = plugins.get().get(pluginKey);
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
      api.plugins().getStaticResource(plugin.getKey(), resourcePath, stream -> bytes[0] = readBytes(stream), new SonarLintCancelMonitor());
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

  private static Path writeTemporaryFile(Path workDirectory, String resourcePath, byte[] bytes) {
    var filename = Path.of(resourcePath).getFileName().toString();
    try {
      Files.createDirectories(workDirectory);
      var file = Files.createTempFile(workDirectory, "sonarlint-resource-", "-" + filename);
      Files.write(file, bytes);
      return file;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot store static resource '" + resourcePath + "'", e);
    }
  }

  @FunctionalInterface
  interface Downloader {
    byte[] download(StoredPlugin plugin, String resourcePath);
  }

}
