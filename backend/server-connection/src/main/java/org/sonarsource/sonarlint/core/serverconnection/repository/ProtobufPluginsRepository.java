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
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import org.sonarsource.sonarlint.core.serverconnection.storage.RWLock;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

/**
 * Protobuf-based implementation of PluginsRepository.
 */
public class ProtobufPluginsRepository implements PluginsRepository {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  public static final String PLUGIN_REFERENCES_PB = "plugin_references.pb";

  private final Path storageRoot;

  public ProtobufPluginsRepository(Path storageRoot) {
    this.storageRoot = storageRoot;
  }

  private Path getPluginsRootPath(String connectionId) {
    return storageRoot.resolve(encodeForFs(connectionId)).resolve("plugins");
  }

  private Path getPluginReferencesFilePath(String connectionId) {
    return getPluginsRootPath(connectionId).resolve(PLUGIN_REFERENCES_PB);
  }

  @Override
  public boolean isValid(String connectionId) {
    var pluginReferencesFilePath = getPluginReferencesFilePath(connectionId);
    if (!Files.exists(pluginReferencesFilePath)) {
      return false;
    }
    try {
      new RWLock().read(() -> ProtobufFileUtil.readFile(pluginReferencesFilePath, Sonarlint.PluginReferences.parser()));
      return true;
    } catch (Exception e) {
      LOG.debug("Could not load plugins storage", e);
      return false;
    }
  }

  @Override
  public void store(String connectionId, ServerPlugin plugin, InputStream pluginBinary) {
    var rootPath = getPluginsRootPath(connectionId);
    var pluginReferencesFilePath = getPluginReferencesFilePath(connectionId);
    new RWLock().write(() -> {
      try {
        var pluginPath = rootPath.resolve(plugin.getFilename());
        FileUtils.copyInputStreamToFile(pluginBinary, pluginPath.toFile());
        LOG.debug("Storing plugin to {} with file size {} bytes", pluginPath.toAbsolutePath(), Files.size(pluginPath));
        var pluginFile = pluginPath.toFile();
        LOG.debug("Plugin file created: {}", pluginFile.exists());
        LOG.debug("Written plugin file size {} bytes", Files.size(pluginPath));
        var reference = adapt(plugin);
        var references = Files.exists(pluginReferencesFilePath)
          ? ProtobufFileUtil.readFile(pluginReferencesFilePath, Sonarlint.PluginReferences.parser())
          : Sonarlint.PluginReferences.newBuilder().build();
        var currentReferences = Sonarlint.PluginReferences.newBuilder(references);
        currentReferences.putPluginsByKey(plugin.getKey(), reference);
        ProtobufFileUtil.writeToFile(currentReferences.build(), pluginReferencesFilePath);
        LOG.debug("Plugin file {} created: {}", pluginReferencesFilePath, pluginReferencesFilePath.toFile().exists());
      } catch (IOException e) {
        // XXX should we stop the whole sync ? just continue and log ?
        throw new StorageException("Cannot save plugin " + plugin.getFilename() + " in " + rootPath, e);
      }
    });
  }

  @Override
  public List<StoredPlugin> getStoredPlugins(String connectionId) {
    var pluginReferencesFilePath = getPluginReferencesFilePath(connectionId);
    var rootPath = getPluginsRootPath(connectionId);
    return new RWLock().read(() -> Files.exists(pluginReferencesFilePath) ? ProtobufFileUtil.readFile(pluginReferencesFilePath, Sonarlint.PluginReferences.parser())
      : Sonarlint.PluginReferences.newBuilder().build()).getPluginsByKeyMap().values().stream().map(ref -> adapt(ref, rootPath)).toList();
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

  private static Sonarlint.PluginReferences.PluginReference adapt(ServerPlugin plugin) {
    return Sonarlint.PluginReferences.PluginReference.newBuilder()
      .setKey(plugin.getKey())
      .setHash(plugin.getHash())
      .setFilename(plugin.getFilename())
      .build();
  }

  private static StoredPlugin adapt(Sonarlint.PluginReferences.PluginReference plugin, Path rootPath) {
    return new StoredPlugin(
      plugin.getKey(),
      plugin.getHash(),
      rootPath.resolve(plugin.getFilename()));
  }

  @Override
  public void cleanUpUnknownPlugins(String connectionId, List<ServerPlugin> serverPluginsExpectedInStorage) {
    var rootPath = getPluginsRootPath(connectionId);
    var pluginReferencesFilePath = getPluginReferencesFilePath(connectionId);
    var expectedPluginPaths = serverPluginsExpectedInStorage.stream().map(plugin -> rootPath.resolve(plugin.getFilename())).collect(Collectors.toSet());
    var pluginsByKey = serverPluginsExpectedInStorage.stream().collect(Collectors.toMap(ServerPlugin::getKey, ProtobufPluginsRepository::adapt));
    var currentReferences = Sonarlint.PluginReferences.newBuilder();
    currentReferences.putAllPluginsByKey(pluginsByKey);
    new RWLock().write(() -> {
      var unknownFiles = getUnknownFiles(expectedPluginPaths, rootPath, pluginReferencesFilePath);
      deleteFiles(unknownFiles, rootPath);
      ProtobufFileUtil.writeToFile(currentReferences.build(), pluginReferencesFilePath);
    });
  }

  private static void deleteFiles(List<File> unknownFiles, Path rootPath) {
    if (!unknownFiles.isEmpty()) {
      LOG.debug("Cleaning up the plugins storage {}, removing {} unknown files:", rootPath, unknownFiles.size());
      unknownFiles.forEach(f -> LOG.debug(f.getAbsolutePath()));
      unknownFiles.forEach(FileUtils::deleteQuietly);
    }
  }

  private static List<File> getUnknownFiles(Set<Path> knownPluginsPaths, Path rootPath, Path pluginReferencesFilePath) {
    if (!Files.exists(rootPath)) {
      return Collections.emptyList();
    }
    LOG.debug("Known plugin paths: {}", knownPluginsPaths);
    try (Stream<Path> pathsInDir = Files.list(rootPath)) {
      var paths = pathsInDir.toList();
      LOG.debug("Paths in dir: {}", paths);
      var unknownFiles = paths.stream()
        .filter(p -> !p.equals(pluginReferencesFilePath))
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
    var pluginReferencesFilePath = getPluginReferencesFilePath(connectionId);
    if (!Files.exists(pluginReferencesFilePath)) {
      createPluginDirectory(pluginReferencesFilePath);
      new RWLock().write(() -> {
        var references = Sonarlint.PluginReferences.newBuilder().build();
        ProtobufFileUtil.writeToFile(references, pluginReferencesFilePath);
      });
    }
  }

  private static void createPluginDirectory(Path pluginReferencesFilePath) {
    try {
      Files.createDirectories(pluginReferencesFilePath.getParent());
    } catch (IOException e) {
      throw new StorageException(String.format("Cannot create plugin file directory: %s", pluginReferencesFilePath.getParent()), e);
    }
  }
}
