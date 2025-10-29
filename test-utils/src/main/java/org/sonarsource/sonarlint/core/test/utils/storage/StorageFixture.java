/*
 * SonarLint Core - Test Utils
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
package org.sonarsource.sonarlint.core.test.utils.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.serverapi.features.Feature;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

public class StorageFixture {
  public static StorageBuilder newStorage(String connectionId) {
    return new StorageBuilder(connectionId);
  }

  public static class Storage {
    private final Path path;
    private final List<Path> pluginPaths;
    private final List<ProjectStorageFixture.ProjectStorage> projectStorages;

    private Storage(Path path, List<Path> pluginPaths, List<ProjectStorageFixture.ProjectStorage> projectStorages) {
      this.path = path;
      this.pluginPaths = pluginPaths;
      this.projectStorages = projectStorages;
    }

    public Path getPath() {
      return path;
    }

    public List<Path> getPluginPaths() {
      return pluginPaths;
    }

    public List<ProjectStorageFixture.ProjectStorage> getProjectStorages() {
      return projectStorages;
    }
  }

  public static class StorageBuilder {
    private final String connectionId;
    private final List<Feature> supportedFeatures = new ArrayList<>();
    private final List<Plugin> plugins = new ArrayList<>();
    private final List<ProjectStorageFixture.ProjectStorageBuilder> projectBuilders = new ArrayList<>();
    private AiCodeFixFixtures.Builder aiCodeFixBuilder;
    private String serverVersion;
    private Map<String, String> globalSettings;

    private StorageBuilder(String connectionId) {
      this.connectionId = connectionId;
    }

    public StorageBuilder withGlobalSettings(Map<String, String> globalSettings) {
      this.globalSettings = globalSettings;
      return this;
    }

    public StorageBuilder withServerVersion(String serverVersion) {
      this.serverVersion = serverVersion;
      return this;
    }

    public StorageBuilder withServerFeature(Feature feature) {
      this.supportedFeatures.add(feature);
      return this;
    }

    public StorageBuilder withPlugins(org.sonarsource.sonarlint.core.test.utils.plugins.Plugin... plugins) {
      var builder = this;
      for (org.sonarsource.sonarlint.core.test.utils.plugins.Plugin plugin : plugins) {
        builder = builder.withPlugin(plugin);
      }
      return builder;
    }

    public StorageBuilder withPlugin(org.sonarsource.sonarlint.core.test.utils.plugins.Plugin plugin) {
      return withPlugin(plugin.getPluginKey(), plugin.getPath(), plugin.getHash());
    }

    public StorageBuilder withPlugin(String key, Path jarPath, String hash) {
      plugins.add(new Plugin(jarPath, jarPath.getFileName().toString(), hash, key));
      return this;
    }

    public StorageBuilder withProject(String projectKey, Consumer<ProjectStorageFixture.ProjectStorageBuilder> consumer) {
      var builder = new ProjectStorageFixture.ProjectStorageBuilder(projectKey);
      consumer.accept(builder);
      projectBuilders.add(builder);
      return this;
    }

    public StorageBuilder withProject(String projectKey) {
      projectBuilders.add(new ProjectStorageFixture.ProjectStorageBuilder(projectKey));
      return this;
    }

    public StorageBuilder withAiCodeFixSettings(Consumer<AiCodeFixFixtures.Builder> consumer) {
      var builder = new AiCodeFixFixtures.Builder();
      consumer.accept(builder);
      aiCodeFixBuilder = builder;
      return this;
    }

    public AiCodeFixFixtures.Builder getAiCodeFixSettingsBuilder() {
      return aiCodeFixBuilder;
    }

    public String getConnectionId() {
      return connectionId;
    }

    public Storage create(Path rootPath) {
      var storagePath = rootPath.resolve("storage");
      var connectionStorage = storagePath.resolve(encodeForFs(connectionId));
      var pluginsFolderPath = connectionStorage.resolve("plugins");
      var projectsFolderPath = connectionStorage.resolve("projects");
      try {
        FileUtils.forceMkdir(pluginsFolderPath.toFile());
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }

      createServerInfo(connectionStorage);

      var pluginPaths = createPlugins(pluginsFolderPath);
      createPluginReferences(pluginsFolderPath);

      List<ProjectStorageFixture.ProjectStorage> projectStorages = new ArrayList<>();
      projectBuilders.forEach(project -> projectStorages.add(project.create(projectsFolderPath)));
      if (aiCodeFixBuilder != null) {
        aiCodeFixBuilder.create(connectionStorage);
      }
      return new Storage(storagePath, pluginPaths, projectStorages);
    }

    private void createServerInfo(Path connectionStorage) {
      if (serverVersion != null || globalSettings != null || !supportedFeatures.isEmpty()) {
        var version = serverVersion == null ? "0.0.0" : serverVersion;
        var settings = globalSettings == null ? Map.<String, String>of() : globalSettings;
        ProtobufFileUtil.writeToFile(Sonarlint.ServerInfo.newBuilder()
            .setVersion(version)
            .addAllSupportedFeatures(supportedFeatures.stream().map(Feature::getKey).toList())
            .putAllGlobalSettings(settings)
            .build(),
          connectionStorage.resolve("server_info.pb"));
      }
    }

    private List<Path> createPlugins(Path pluginsFolderPath) {
      List<Path> pluginPaths = new ArrayList<>();
      plugins.forEach(plugin -> {
        var pluginPath = pluginsFolderPath.resolve(plugin.jarName);
        try {
          Files.copy(plugin.path, pluginPath);
        } catch (IOException e) {
          throw new IllegalStateException("Cannot copy plugin " + plugin.jarName, e);
        }
        pluginPaths.add(pluginPath);
      });
      return pluginPaths;
    }

    private void createPluginReferences(Path pluginsFolderPath) {
      var builder = Sonarlint.PluginReferences.newBuilder();
      plugins.forEach(plugin -> builder.putPluginsByKey(plugin.key, Sonarlint.PluginReferences.PluginReference.newBuilder()
        .setFilename(plugin.jarName)
        .setHash(plugin.hash)
        .setKey(plugin.key)
        .build()));
      ProtobufFileUtil.writeToFile(builder.build(), pluginsFolderPath.resolve("plugin_references.pb"));
    }

    private static class Plugin {
      private final Path path;
      private final String jarName;
      private final String hash;
      private final String key;

      private Plugin(Path path, String jarName, String hash, String key) {
        this.path = path;
        this.jarName = jarName;
        this.hash = hash;
        this.key = key;
      }
    }
  }
}
