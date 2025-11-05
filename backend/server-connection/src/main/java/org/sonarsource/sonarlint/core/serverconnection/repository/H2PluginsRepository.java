/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection.repository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.serverconnection.storage.RWLock;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.PLUGINS;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

/**
 * H2-based implementation of PluginsRepository.
 * Stores plugin metadata in H2 database and plugin binaries on disk, same as protobuf implementation.
 */
public class H2PluginsRepository implements PluginsRepository {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintDatabase database;
  private final Path storageRoot;

  public H2PluginsRepository(SonarLintDatabase database, Path storageRoot) {
    this.database = database;
    this.storageRoot = storageRoot;
  }

  private Path getPluginsRootPath(String connectionId) {
    return storageRoot.resolve(encodeForFs(connectionId)).resolve("plugins");
  }

  @Override
  public boolean isValid(String connectionId) {
    // if the path exists, it means plugins were synchronized at least once
    return Files.exists(getPluginsRootPath(connectionId));
  }

  @Override
  public void store(String connectionId, ServerPlugin plugin, InputStream pluginBinary) {
    var rootPath = getPluginsRootPath(connectionId);
    new RWLock().write(() -> {
      try {
        var pluginPath = rootPath.resolve(plugin.getFilename());
        Files.createDirectories(rootPath);
        FileUtils.copyInputStreamToFile(pluginBinary, pluginPath.toFile());

        // Store metadata in H2
        var dsl = database.dsl();
        int updated = dsl.update(PLUGINS)
          .set(PLUGINS.HASH, plugin.getHash())
          .set(PLUGINS.FILENAME, plugin.getFilename())
          .where(PLUGINS.CONNECTION_ID.eq(connectionId))
          .and(PLUGINS.KEY.eq(plugin.getKey()))
          .execute();
        if (updated == 0) {
          dsl.insertInto(PLUGINS, PLUGINS.CONNECTION_ID, PLUGINS.KEY, PLUGINS.HASH, PLUGINS.FILENAME)
            .values(connectionId, plugin.getKey(), plugin.getHash(), plugin.getFilename())
            .execute();
        }
      } catch (IOException e) {
        // XXX should we stop the whole sync ? just continue and log ?
        throw new StorageException("Cannot save plugin " + plugin.getFilename() + " in " + rootPath, e);
      }
    });
  }

  @Override
  public List<StoredPlugin> getStoredPlugins(String connectionId) {
    var rootPath = getPluginsRootPath(connectionId);
    return new RWLock().read(() -> {
      var records = database.dsl()
        .select(PLUGINS.KEY, PLUGINS.HASH, PLUGINS.FILENAME)
        .from(PLUGINS)
        .where(PLUGINS.CONNECTION_ID.eq(connectionId))
        .fetch();
      return records.stream()
        .map(rec -> new StoredPlugin(
          rec.get(PLUGINS.KEY),
          rec.get(PLUGINS.HASH),
          rootPath.resolve(rec.get(PLUGINS.FILENAME))))
        .toList();
    });
  }

  @Override
  public Map<String, StoredPlugin> getStoredPluginsByKey(String connectionId) {
    return byKey(getStoredPlugins(connectionId));
  }

  @Override
  public Map<String, Path> getStoredPluginPathsByKey(String connectionId) {
    return getStoredPluginsByKey(connectionId).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().getJarPath()));
  }

  private static Map<String, StoredPlugin> byKey(List<StoredPlugin> plugins) {
    return plugins.stream().collect(Collectors.toMap(StoredPlugin::getKey, Function.identity()));
  }

  @Override
  public void cleanUpUnknownPlugins(String connectionId, List<ServerPlugin> serverPluginsExpectedInStorage) {
    var rootPath = getPluginsRootPath(connectionId);
    var expectedPluginPaths = serverPluginsExpectedInStorage.stream().map(plugin -> rootPath.resolve(plugin.getFilename())).collect(Collectors.toSet());
    new RWLock().write(() -> {
      var unknownFiles = getUnknownFiles(expectedPluginPaths, rootPath);
      deleteFiles(unknownFiles, rootPath);
      
      // Update H2 database to only keep expected plugins
      var expectedKeys = serverPluginsExpectedInStorage.stream().map(ServerPlugin::getKey).collect(Collectors.toSet());
      var dsl = database.dsl();
      
      // Delete plugins that are not in the expected list
      var allStoredPlugins = dsl.select(PLUGINS.KEY)
        .from(PLUGINS)
        .where(PLUGINS.CONNECTION_ID.eq(connectionId))
        .fetch()
        .map(rec -> rec.get(PLUGINS.KEY));
      
      allStoredPlugins.stream()
        .filter(key -> !expectedKeys.contains(key))
        .forEach(key -> dsl.deleteFrom(PLUGINS)
          .where(PLUGINS.CONNECTION_ID.eq(connectionId))
          .and(PLUGINS.KEY.eq(key))
          .execute());
      
      // Update or insert expected plugins
      for (var plugin : serverPluginsExpectedInStorage) {
        int updated = dsl.update(PLUGINS)
          .set(PLUGINS.HASH, plugin.getHash())
          .set(PLUGINS.FILENAME, plugin.getFilename())
          .where(PLUGINS.CONNECTION_ID.eq(connectionId))
          .and(PLUGINS.KEY.eq(plugin.getKey()))
          .execute();
        if (updated == 0) {
          dsl.insertInto(PLUGINS, PLUGINS.CONNECTION_ID, PLUGINS.KEY, PLUGINS.HASH, PLUGINS.FILENAME)
            .values(connectionId, plugin.getKey(), plugin.getHash(), plugin.getFilename())
            .execute();
        }
      }
    });
  }

  private static void deleteFiles(List<File> unknownFiles, Path rootPath) {
    if (!unknownFiles.isEmpty()) {
      LOG.debug("Cleaning up the plugins storage {}, removing {} unknown files:", rootPath, unknownFiles.size());
      unknownFiles.forEach(f -> LOG.debug(f.getAbsolutePath()));
      unknownFiles.forEach(FileUtils::deleteQuietly);
    }
  }

  private static List<File> getUnknownFiles(Set<Path> knownPluginsPaths, Path rootPath) {
    if (!Files.exists(rootPath)) {
      return Collections.emptyList();
    }
    LOG.debug("Known plugin paths: {}", knownPluginsPaths);
    try (Stream<Path> pathsInDir = Files.list(rootPath)) {
      var paths = pathsInDir.toList();
      LOG.debug("Paths in dir: {}", paths);
      var unknownFiles = paths.stream()
        .filter(p -> !knownPluginsPaths.contains(p))
        .map(Path::toFile)
        .toList();
      LOG.debug("Unknown files: {}", unknownFiles);
      return unknownFiles;
    } catch (Exception e) {
      LOG.error("Cannot list files in '{}'", rootPath, e);
      return Collections.emptyList();
    }
  }

  @Override
  public void storeNoPlugins(String connectionId) {
    var rootPath = getPluginsRootPath(connectionId);
    if (!Files.exists(rootPath)) {
      createPluginDirectory(rootPath);
    }
  }

  private static void createPluginDirectory(Path rootPath) {
    try {
      Files.createDirectories(rootPath);
    } catch (IOException e) {
      throw new StorageException(String.format("Cannot create plugin file directory: %s", rootPath), e);
    }
  }
}
