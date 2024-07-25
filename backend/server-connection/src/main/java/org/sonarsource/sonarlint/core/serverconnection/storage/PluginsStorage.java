/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

public class PluginsStorage {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  public static final String PLUGIN_REFERENCES_PB = "plugin_references.pb";

  private final Path rootPath;
  private final Path pluginReferencesFilePath;
  private final RWLock rwLock = new RWLock();

  public PluginsStorage(Path connectionStorageRoot) {
    this.rootPath = connectionStorageRoot.resolve("plugins");
    this.pluginReferencesFilePath = rootPath.resolve(PLUGIN_REFERENCES_PB);
  }

  public boolean isValid() {
    try {
      rwLock.read(() -> ProtobufFileUtil.readFile(pluginReferencesFilePath, Sonarlint.PluginReferences.parser()));
      return true;
    } catch (Exception e) {
      LOG.error("Could not load plugins storage", e);
      return false;
    }
  }

  public void store(ServerPlugin plugin, InputStream pluginBinary) {
    try {
//      var pluginPath = rootPath.resolve(plugin.getFilename());
//      createPluginDirectoryIfNeeded();
//      writePluginFile(pluginBinary, pluginPath, 1);
      FileUtils.copyInputStreamToFile(pluginBinary, rootPath.resolve(plugin.getFilename()).toFile());
      var reference = adapt(plugin);
      rwLock.write(() -> {
        var references = Files.exists(pluginReferencesFilePath) ? ProtobufFileUtil.readFile(pluginReferencesFilePath, Sonarlint.PluginReferences.parser())
          : Sonarlint.PluginReferences.newBuilder().build();
        var currentReferences = Sonarlint.PluginReferences.newBuilder(references);
        currentReferences.putPluginsByKey(plugin.getKey(), reference);
        ProtobufFileUtil.writeToFile(currentReferences.build(), pluginReferencesFilePath);
      });
    } catch (IOException e) {
      // XXX should we stop the whole sync ? just continue and log ?
      throw new StorageException("Cannot save plugin " + plugin.getFilename() + " in " + rootPath, e);
    }
  }

  private static void writePluginFile(InputStream pluginBinary, Path pluginPath, int attempts) throws IOException {
    try (var out = Files.newOutputStream(pluginPath)) {
      out.write(pluginBinary.readAllBytes());
      var pluginFileCanRead = pluginPath.toFile().canRead();
      LOG.debug("Stored plugin to {}", pluginPath.toAbsolutePath());
      LOG.debug("Plugin file readable: {}", pluginFileCanRead);
      if (pluginFileCanRead && attempts < 5) {
        LOG.debug("Retrying plugin file write. Attempt {}", attempts);
        writePluginFile(pluginBinary, pluginPath, attempts + 1);
      }
    } catch (IOException e) {
      throw new StorageException("Cannot save plugin " + pluginPath.toAbsolutePath() + " in " + pluginPath, e);
    }
  }

  public List<StoredPlugin> getStoredPlugins() {
    return rwLock.read(() -> Files.exists(pluginReferencesFilePath) ? ProtobufFileUtil.readFile(pluginReferencesFilePath, Sonarlint.PluginReferences.parser())
      : Sonarlint.PluginReferences.newBuilder().build()).getPluginsByKeyMap().values().stream().map(this::adapt).collect(Collectors.toList());
  }

  public Map<String, StoredPlugin> getStoredPluginsByKey() {
    return byKey(getStoredPlugins());
  }

  public Map<String, Path> getStoredPluginPathsByKey() {
    return getStoredPluginsByKey().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().getJarPath()));
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

  private StoredPlugin adapt(Sonarlint.PluginReferences.PluginReference plugin) {
    return new StoredPlugin(
      plugin.getKey(),
      plugin.getHash(),
      rootPath.resolve(plugin.getFilename()));
  }

  public void cleanUp() {
    getUnknownFiles()
      .forEach(FileUtils::deleteQuietly);
  }

  private List<File> getUnknownFiles() {
    if (!Files.exists(rootPath)) {
      return Collections.emptyList();
    }
    var knownPluginsPaths = getStoredPlugins().stream().map(StoredPlugin::getJarPath).collect(Collectors.toSet());
    try (Stream<Path> pathsInDir = Files.list(rootPath)) {
      return pathsInDir.filter(p -> !p.equals(pluginReferencesFilePath))
        .filter(p -> !knownPluginsPaths.contains(p))
        .map(Path::toFile)
        .collect(Collectors.toList());
    } catch (Exception e) {
      LOG.error("Cannot list files in '{}'", rootPath, e);
      return Collections.emptyList();
    }
  }

  public void storeNoPlugins() {
    if (!Files.exists(pluginReferencesFilePath)) {
      createPluginDirectory();
      rwLock.write(() -> {
        var references = Sonarlint.PluginReferences.newBuilder().build();
        ProtobufFileUtil.writeToFile(references, pluginReferencesFilePath);
      });
    }
  }


  private void createPluginDirectory() {
    try {
      Files.createDirectories(pluginReferencesFilePath.getParent());
    } catch (IOException e) {
      throw new StorageException(String.format("Cannot create plugin file directory: %s", rootPath), e);
    }
  }

//  private void createPluginDirectoryIfNeeded() {
//    if (Files.exists(rootPath)) {
//      return;
//    }
//    try {
//      Files.createDirectories(rootPath);
//    } catch (IOException e) {
//      throw new StorageException(String.format("Cannot create plugin file directory: %s", rootPath), e);
//    }
//  }
}
